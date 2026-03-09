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
 * 模型信息:
 * - 输入: 灰度图 [1, 1, 64, 256]
 * - 输出: CTC logits [16, 1, 15]
 * - 大小: ~316 KB
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

    fun close() {
        session?.close()
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
