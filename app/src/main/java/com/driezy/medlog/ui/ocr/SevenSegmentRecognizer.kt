package com.driezy.medlog.ui.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.driezy.medlog.data.repository.OcrModelType
import com.driezy.medlog.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.nio.FloatBuffer

/**
 * 七段数码管专用识别器 (LightSVTR + CTC)。
 *
 * 使用 ONNX Runtime 运行训练好的轻量 SVTR (CNN+Transformer) 模型，
 * 专门针对血压计、体温计等设备上的七段管数字显示。
 * 支持单行和多行 LCD 识别（模型原生输出 \n 分隔行）。
 *
 * 模型信息 (v11 INT8):
 * - 输入: 灰度图 [1, 1, 128, 256] (float32)
 * - 输出: CTC logits [T, 1, 16] (T=64)
 * - 参数量: 638K (INT8 动态量化)
 * - 大小: ~920 KB
 * - 字符集: 0-9, /, ., 空格, -, \n
 */
internal class SevenSegmentRecognizer(
    private val context: Context,
    private val prefsRepository: UserPreferencesRepository,
) {

    private val ortEnvironment = OrtEnvironment.getEnvironment()
    private val sessionLock = Any()
    @Volatile private var session: OrtSession? = null

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile private var currentModelType: OcrModelType? = null

    init {
        scope.launch {
            prefsRepository.settingsFlow
                .map { it.ocrModelType }
                .distinctUntilChanged()
                .collectLatest { modelType ->
                    switchModel(modelType)
                }
        }
    }

    /**
     * Switch OCR model dynamically.
     */
    private fun switchModel(modelType: OcrModelType) {
        synchronized(sessionLock) {
            if (currentModelType == modelType && session != null) {
                return
            }
            Log.d(TAG, "Switching OCR model from $currentModelType to $modelType")

            try {
                session?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing previous OCR session", e)
            }
            session = null

            val assetName = when (modelType) {
                OcrModelType.LIGHT_SVTR -> MODEL_SVTR_ASSET
                OcrModelType.FASTVIT_T8 -> MODEL_FASTVIT_ASSET
            }

            try {
                val modelBytes = context.applicationContext.assets.open(assetName).use { it.readBytes() }
                session = ortEnvironment.createSession(modelBytes)
                currentModelType = modelType
                Log.d(TAG, "Successfully loaded $modelType OCR model ($assetName)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load ONNX model $assetName for $modelType", e)
            }
        }
    }

    /**
     * 对 Bitmap 进行七段管数字识别。
     *
     * @return 识别出的文字字符串，失败返回 null
     */
    fun recognize(bitmap: Bitmap): String? {
        val modelType = synchronized(sessionLock) {
            if (session == null) return null
            currentModelType ?: OcrModelType.LIGHT_SVTR
        }
        return try {
            val input = preprocessBitmap(bitmap, modelType)
            val channels = if (modelType == OcrModelType.LIGHT_SVTR) 1L else 3L
            val tensor = OnnxTensor.createTensor(ortEnvironment, input, longArrayOf(1, channels, INPUT_H, INPUT_W))

            val output = synchronized(sessionLock) {
                val activeSession = session ?: return null
                activeSession.run(mapOf("input" to tensor))
            }

            val logits = output[0].value
            tensor.close()
            output.close()

            @Suppress("UNCHECKED_CAST")
            val result = ctcDecode(logits as Array<Array<FloatArray>>)
            result.ifEmpty { null }
        } catch (e: Exception) {
            Log.w(TAG, "7-segment recognition failed using $modelType", e)
            null
        }
    }

    /**
     * 对 LCD 区域进行多行识别：水平投影分行 → 逐行 CRNN。
     *
     * 血压计等设备的 LCD 通常显示多行 (收缩压/舒张压/脉率)，
     * 各行字体大小不同。此方法先将裁剪区分割为单行再分别识别。
     *
     * @return 每行识别结果列表 (跳过空行)
     */
    fun recognizeRows(bitmap: Bitmap): List<String> {
        if (session == null) return emptyList()

        // 优先: 用模型原生多行识别（模型输出含 \n 分隔）
        val fullResult = recognize(bitmap)
        if (fullResult != null && '\n' in fullResult) {
            val lines = fullResult.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size >= 2) return lines
        }

        // 回退: 若模型未输出 \n，用图像行分割
        val rows = splitRows(bitmap)
        if (rows.isEmpty()) {
            return fullResult?.let { listOf(it) } ?: emptyList()
        }
        val results = mutableListOf<String>()
        for (row in rows) {
            recognize(row)?.let { results.add(it) }
            row.recycle()
        }
        return results
    }

    fun close() {
        scope.cancel()
        synchronized(sessionLock) {
            session?.close()
            session = null
        }
    }

    /**
     * 基于行标准差的行分割。
     *
     * 对 LCD 裁剪区，每行像素的标准差可区分有数字的行和空白背景行：
     * - 数字行: 笔画+背景混合 → 标准差高
     * - 空白行: 均匀背景色 → 标准差低
     *
     * 通过标准差的谷值查找行间间隙，切分为多行 Bitmap。
     */
    private fun splitRows(bitmap: Bitmap): List<Bitmap> {
        val w = bitmap.width
        val h = bitmap.height
        if (h < 30 || w < 10) return emptyList()

        // 计算每行像素标准差
        val pixels = IntArray(w)
        val rowStd = FloatArray(h)
        for (y in 0 until h) {
            bitmap.getPixels(pixels, 0, w, 0, y, w, 1)
            var sum = 0L
            var sumSq = 0L
            for (x in 0 until w) {
                val px = pixels[x]
                val gray = (((px shr 16) and 0xFF) * 30 +
                    ((px shr 8) and 0xFF) * 59 +
                    (px and 0xFF) * 11) / 100
                sum += gray
                sumSq += gray.toLong() * gray
            }
            val mean = sum.toFloat() / w
            val variance = sumSq.toFloat() / w - mean * mean
            rowStd[y] = if (variance > 0) kotlin.math.sqrt(variance) else 0f
        }

        // 平滑标准差曲线 (窗口=3) 减少噪声
        val smoothed = FloatArray(h)
        for (y in 0 until h) {
            var s = 0f
            var c = 0
            for (dy in -1..1) {
                val yy = y + dy
                if (yy in 0 until h) { s += rowStd[yy]; c++ }
            }
            smoothed[y] = s / c
        }

        // 自适应阈值: 取标准差的中位数作为分界
        val sorted = smoothed.copyOf().also { it.sort() }
        val medianStd = sorted[h / 2]
        // 内容行的标准差应明显高于背景行
        val threshold = medianStd + (sorted[h * 3 / 4] - medianStd) * 0.3f

        // 标记内容行
        val isContentRow = BooleanArray(h) { y -> smoothed[y] > threshold }

        // 查找连续内容区段
        val segments = mutableListOf<Pair<Int, Int>>()
        var inSegment = false
        var segStart = 0
        for (y in 0 until h) {
            if (isContentRow[y] && !inSegment) {
                inSegment = true
                segStart = y
            } else if (!isContentRow[y] && inSegment) {
                inSegment = false
                segments.add(segStart to y - 1)
            }
        }
        if (inSegment) segments.add(segStart to h - 1)

        // 合并相距太近的段 (间隙 < 行高 * 20% 或 < 5px)
        val merged = mutableListOf<Pair<Int, Int>>()
        for (seg in segments) {
            if (merged.isNotEmpty()) {
                val prev = merged.last()
                val gap = seg.first - prev.second
                val prevHeight = prev.second - prev.first
                if (gap < maxOf(prevHeight * 0.2f, 5f)) {
                    merged[merged.lastIndex] = prev.first to seg.second
                    continue
                }
            }
            merged.add(seg)
        }

        // 过滤太窄的段 (< 总高度的 5%)
        val minRowH = maxOf(h * 0.05f, 8f).toInt()
        val finalSegs = merged.filter { (s, e) -> e - s + 1 >= minRowH }

        // 只有一个段 → 不算多行
        if (finalSegs.size <= 1) return emptyList()

        // 裁剪每行（上下各扩展 2px 边距以防裁掉笔画）
        return finalSegs.map { (startY, endY) ->
            val s = (startY - 2).coerceAtLeast(0)
            val e = (endY + 2).coerceAtMost(h - 1)
            Bitmap.createBitmap(bitmap, 0, s, w, e - s + 1)
        }
    }

    /**
     * Preprocess Bitmap to model input: resize + pad + normalize.
     * Supports both 1-channel grayscale [LIGHT_SVTR] and 3-channel RGB with ImageNet normalization [FASTVIT_T8].
     */
    private fun preprocessBitmap(bitmap: Bitmap, modelType: OcrModelType): FloatBuffer {
        val w = bitmap.width
        val h = bitmap.height

        // 1. Resize to fixed height while maintaining aspect ratio, capping width at INPUT_W
        val ratio = INPUT_H.toFloat() / h
        val scaledW = (w * ratio).toInt().coerceAtMost(INPUT_W.toInt())
        val scaled = bitmap.scale(scaledW, INPUT_H.toInt())

        // 2. Pad to INPUT_W width on an ARGB_8888 canvas (default zero-initialized background)
        val padded = createBitmap(INPUT_W.toInt(), INPUT_H.toInt())
        val canvas = Canvas(padded)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        if (scaled !== padded) scaled.recycle()

        // 3. Extract all pixels in a single operation
        val numPixels = INPUT_H.toInt() * INPUT_W.toInt()
        val pixels = IntArray(numPixels)
        padded.getPixels(pixels, 0, INPUT_W.toInt(), 0, 0, INPUT_W.toInt(), INPUT_H.toInt())
        padded.recycle()

        return when (modelType) {
            OcrModelType.LIGHT_SVTR -> {
                val buffer = FloatBuffer.allocate(numPixels)
                for (pixel in pixels) {
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    val gray = (r * 0.299f + g * 0.587f + b * 0.114f)
                    buffer.put(gray / 255.0f)
                }
                buffer.rewind()
                buffer
            }
            OcrModelType.FASTVIT_T8 -> {
                val buffer = FloatBuffer.allocate(3 * numPixels)
                val rVals = FloatArray(numPixels)
                val gVals = FloatArray(numPixels)
                val bVals = FloatArray(numPixels)

                // ImageNet normalization constants
                val meanR = 0.485f
                val meanG = 0.456f
                val meanB = 0.406f
                val stdR = 0.229f
                val stdG = 0.224f
                val stdB = 0.225f

                for (i in pixels.indices) {
                    val pixel = pixels[i]
                    val r = ((pixel shr 16) and 0xFF) / 255.0f
                    val g = ((pixel shr 8) and 0xFF) / 255.0f
                    val b = (pixel and 0xFF) / 255.0f

                    rVals[i] = (r - meanR) / stdR
                    gVals[i] = (g - meanG) / stdG
                    bVals[i] = (b - meanB) / stdB
                }

                buffer.put(rVals)
                buffer.put(gVals)
                buffer.put(bVals)
                buffer.rewind()
                buffer
            }
        }
    }

    /**
     * CTC 贪婪解码: 取每个时间步的 argmax，合并重复字符，移除 blank。
     */
    private fun ctcDecode(logits: Array<Array<FloatArray>>): String {
        val sb = StringBuilder()
        var prevIdx = BLANK_IDX

        for (t in logits.indices) {
            val scores = logits[t][0] // [num_classes]
            var bestIdx = 0
            var bestScore = scores[0]
            for (c in 1 until scores.size) {
                if (scores[c] > bestScore) {
                    bestScore = scores[c]
                    bestIdx = c
                }
            }
            if (bestIdx != BLANK_IDX && bestIdx != prevIdx) {
                val ch = CHARSET.getOrNull(bestIdx - 1)
                if (ch != null) sb.append(ch)
            }
            prevIdx = bestIdx
        }
        return postprocessCtc(sb.toString())
    }

    /**
     * CTC 解码后处理：修正常见错误模式。
     */
    private fun postprocessCtc(raw: String): String {
        if (raw.isEmpty()) return raw
        // 按换行分割各行，逐行后处理再拼回
        return raw.split('\n').joinToString("\n") { line ->
            var text = line.trim()
            // 合并连续空格
            text = text.replace(Regex("\\s{2,}"), " ")
            // 去除首尾分隔符
            text = text.trim('/', '.', '-')
            // 去除连续重复分隔符
            text = text.replace(Regex("([/.\\-])\\1+"), "$1")
            text
        }.trim('\n')
    }

    companion object {
        private const val TAG = "SevenSegRecognizer"
        private const val MODEL_SVTR_ASSET = "svtr_seven_seg.onnx"
        private const val MODEL_FASTVIT_ASSET = "fastvit_t8_ctc_reparam.onnx"
        private const val BLANK_IDX = 0
        private const val CHARSET = "0123456789/. -\n"
        private const val INPUT_H = 128L
        private const val INPUT_W = 256L
    }
}
