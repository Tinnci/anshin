package com.example.medlog.ui.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * 七段数码管专用识别器 (CRNN + CTC)。
 *
 * 使用 ONNX Runtime 运行训练好的轻量 CRNN 模型，
 * 专门针对血压计、体温计等设备上的七段管数字显示。
 *
 * 模型信息 (v9 INT8):
 * - 输入: 灰度图 [1, 1, 64, 256] (float32)
 * - 输出: CTC logits [T, 1, 15] (T 为动态时间步)
 * - 参数量: 397K (INT8 动态量化)
 * - 大小: ~421 KB
 * - 字符集: 0-9, /, ., 空格, -
 */
internal class SevenSegmentRecognizer(context: Context) {

    private val ortEnvironment = OrtEnvironment.getEnvironment()
    @Volatile private var session: OrtSession? = null

    init {
        val appContext = context.applicationContext
        Thread({
            session = try {
                val modelBytes = appContext.assets.open(MODEL_ASSET).use { it.readBytes() }
                ortEnvironment.createSession(modelBytes).also {
                    Log.d(TAG, "7-segment model loaded")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load 7-segment ONNX model", e)
                null
            }
        }, "seven-seg-init").start()
    }

    /**
     * 对 Bitmap 进行七段管数字识别。
     *
     * @return 识别出的文字字符串，失败返回 null
     */
    fun recognize(bitmap: Bitmap): String? {
        val sess = session ?: return null
        return try {
            val input = preprocessBitmap(bitmap)
            val tensor = OnnxTensor.createTensor(ortEnvironment, input, longArrayOf(1, 1, INPUT_H, INPUT_W))
            val output = sess.run(mapOf("input" to tensor))
            val logits = output[0].value
            tensor.close()
            output.close()

            @Suppress("UNCHECKED_CAST")
            val result = ctcDecode(logits as Array<Array<FloatArray>>)
            result.ifEmpty { null }
        } catch (e: Exception) {
            Log.w(TAG, "7-segment recognition failed", e)
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
        val rows = splitRows(bitmap)
        if (rows.isEmpty()) {
            // 无法分行时回退到整体识别
            return recognize(bitmap)?.let { listOf(it) } ?: emptyList()
        }
        val results = mutableListOf<String>()
        for (row in rows) {
            recognize(row)?.let { results.add(it) }
            row.recycle()
        }
        return results
    }

    fun close() {
        session?.close()
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
     * 将 Bitmap 预处理为模型输入: 灰度 + 缩放 + 归一化。
     */
    private fun preprocessBitmap(bitmap: Bitmap): FloatBuffer {
        // 转灰度
        val gray = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(gray)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        // 缩放到固定高度，保持宽高比，填充到固定宽度
        val ratio = INPUT_H.toFloat() / gray.height
        val scaledW = (gray.width * ratio).toInt().coerceAtMost(INPUT_W.toInt())
        val scaled = Bitmap.createScaledBitmap(gray, scaledW, INPUT_H.toInt(), true)
        gray.recycle()

        // 填充到 INPUT_W
        val padded = Bitmap.createBitmap(INPUT_W.toInt(), INPUT_H.toInt(), Bitmap.Config.ARGB_8888)
        Canvas(padded).drawBitmap(scaled, 0f, 0f, null)
        if (scaled !== padded) scaled.recycle()

        // 转为归一化浮点数组
        val buffer = FloatBuffer.allocate(INPUT_H.toInt() * INPUT_W.toInt())
        val pixels = IntArray(INPUT_W.toInt())
        for (y in 0 until INPUT_H.toInt()) {
            padded.getPixels(pixels, 0, INPUT_W.toInt(), 0, y, INPUT_W.toInt(), 1)
            for (x in 0 until INPUT_W.toInt()) {
                val pixel = pixels[x]
                // 取灰度值 (R channel) 并归一化到 [0, 1]
                val gray8 = (pixel shr 16) and 0xFF
                buffer.put(gray8 / 255f)
            }
        }
        padded.recycle()
        buffer.rewind()
        return buffer
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
        var text = raw.trim()
        // 合并连续空格
        text = text.replace(Regex("\\s{2,}"), " ")
        // 去除首尾分隔符
        text = text.trim('/', '.', '-')
        // 去除连续重复分隔符 (如 "//" → "/", ".." → ".")
        text = text.replace(Regex("([/.\\-])\\1+"), "$1")
        return text
    }

    companion object {
        private const val TAG = "SevenSegRecognizer"
        private const val MODEL_ASSET = "crnn_seven_seg.onnx"
        private const val BLANK_IDX = 0
        private const val CHARSET = "0123456789/. -"
        private const val INPUT_H = 64L
        private const val INPUT_W = 256L
    }
}
