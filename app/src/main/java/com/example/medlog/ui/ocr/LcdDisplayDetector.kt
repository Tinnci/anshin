package com.example.medlog.ui.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * LCD 数码管区域检测器 (YOLOv11-nano)。
 *
 * 使用 ONNX Runtime 运行 YOLOv11-nano 模型,
 * 在拍摄的照片中定位七段管数码显示区域。
 *
 * 模型信息:
 * - 输入: RGB [1, 3, 640, 640]
 * - 输出: [1, 5, 8400] (cx, cy, w, h, confidence per anchor)
 * - 类别: lcd_display (1 class)
 * - 大小: ~10 MB
 */
internal class LcdDisplayDetector(context: Context) {

    private val ortEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession?

    init {
        session = try {
            val modelBytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            ortEnvironment.createSession(modelBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LCD detector ONNX model", e)
            null
        }
    }

    /**
     * 检测图中的 LCD 显示区域。
     *
     * @return 检测到的区域列表 (归一化坐标 [0,1]),按置信度降序排列
     */
    fun detect(bitmap: Bitmap, confThreshold: Float = CONF_THRESHOLD): List<Detection> {
        val sess = session ?: return emptyList()
        return try {
            val (input, scaleX, scaleY, padX, padY) = preprocessBitmap(bitmap)
            val tensor = OnnxTensor.createTensor(
                ortEnvironment, input,
                longArrayOf(1, 3, INPUT_SIZE, INPUT_SIZE),
            )
            val output = sess.run(mapOf("images" to tensor))
            val rawOutput = output[0].value
            tensor.close()
            output.close()

            @Suppress("UNCHECKED_CAST")
            val results = postprocess(
                rawOutput as Array<Array<FloatArray>>,
                bitmap.width, bitmap.height,
                scaleX, scaleY, padX, padY,
                confThreshold,
            )
            results
        } catch (e: Exception) {
            Log.w(TAG, "LCD detection failed", e)
            emptyList()
        }
    }

    fun close() {
        session?.close()
    }

    /**
     * 将 Bitmap 预处理为模型输入。
     * - 保持宽高比缩放到 640×640
     * - 灰色填充
     * - 归一化到 [0, 1]
     * - CHW 格式
     *
     * @return (buffer, scaleX, scaleY, padX, padY)
     */
    private fun preprocessBitmap(bitmap: Bitmap): PreprocessResult {
        val origW = bitmap.width.toFloat()
        val origH = bitmap.height.toFloat()
        val scale = minOf(INPUT_SIZE / origW, INPUT_SIZE / origH)
        val newW = (origW * scale).toInt()
        val newH = (origH * scale).toInt()
        val padX = (INPUT_SIZE.toInt() - newW) / 2
        val padY = (INPUT_SIZE.toInt() - newH) / 2

        // 创建 640x640 灰色画布
        val padded = Bitmap.createBitmap(
            INPUT_SIZE.toInt(), INPUT_SIZE.toInt(), Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(padded)
        canvas.drawColor(0xFF808080.toInt()) // 灰色填充

        // 缩放绘制原图
        val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        canvas.drawBitmap(scaled, padX.toFloat(), padY.toFloat(), null)
        if (scaled !== bitmap) scaled.recycle()

        // 转 CHW 归一化 float buffer
        val size = INPUT_SIZE.toInt()
        val buffer = FloatBuffer.allocate(3 * size * size)
        val pixels = IntArray(size)

        // R plane, then G, then B
        for (c in 0 until 3) {
            for (y in 0 until size) {
                padded.getPixels(pixels, 0, size, 0, y, size, 1)
                for (x in 0 until size) {
                    val pixel = pixels[x]
                    val value = when (c) {
                        0 -> (pixel shr 16) and 0xFF // R
                        1 -> (pixel shr 8) and 0xFF  // G
                        else -> pixel and 0xFF        // B
                    }
                    buffer.put(value / 255f)
                }
            }
        }
        padded.recycle()
        buffer.rewind()

        return PreprocessResult(buffer, scale, scale, padX.toFloat(), padY.toFloat())
    }

    /**
     * YOLO 后处理: 解析输出 → NMS → 检测结果。
     */
    private fun postprocess(
        output: Array<Array<FloatArray>>,
        imgWidth: Int,
        imgHeight: Int,
        scaleX: Float,
        scaleY: Float,
        padX: Float,
        padY: Float,
        confThreshold: Float,
    ): List<Detection> {
        // output shape: [1, 5, 8400] for 1 class
        // 5 = cx, cy, w, h, conf
        val numAnchors = output[0][0].size
        val detections = mutableListOf<Detection>()

        for (i in 0 until numAnchors) {
            val cx = output[0][0][i]
            val cy = output[0][1][i]
            val w = output[0][2][i]
            val h = output[0][3][i]
            val conf = output[0][4][i]

            if (conf < confThreshold) continue

            // 从 letterbox 坐标转回原图归一化坐标
            val x1 = ((cx - w / 2) - padX) / scaleX / imgWidth
            val y1 = ((cy - h / 2) - padY) / scaleY / imgHeight
            val x2 = ((cx + w / 2) - padX) / scaleX / imgWidth
            val y2 = ((cy + h / 2) - padY) / scaleY / imgHeight

            detections.add(
                Detection(
                    rect = RectF(
                        x1.coerceIn(0f, 1f),
                        y1.coerceIn(0f, 1f),
                        x2.coerceIn(0f, 1f),
                        y2.coerceIn(0f, 1f),
                    ),
                    confidence = conf,
                ),
            )
        }

        // NMS
        return nms(detections.sortedByDescending { it.confidence }, NMS_THRESHOLD)
    }

    private fun nms(sorted: List<Detection>, iouThreshold: Float): List<Detection> {
        val result = mutableListOf<Detection>()
        val suppressed = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (suppressed[i]) continue
            result.add(sorted[i])
            for (j in i + 1 until sorted.size) {
                if (!suppressed[j] && iou(sorted[i].rect, sorted[j].rect) > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }
        return result
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interLeft = maxOf(a.left, b.left)
        val interTop = maxOf(a.top, b.top)
        val interRight = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)

        if (interRight <= interLeft || interBottom <= interTop) return 0f

        val interArea = (interRight - interLeft) * (interBottom - interTop)
        val aArea = (a.right - a.left) * (a.bottom - a.top)
        val bArea = (b.right - b.left) * (b.bottom - b.top)

        return interArea / (aArea + bArea - interArea)
    }

    data class Detection(
        val rect: RectF, // 归一化坐标 [0, 1]
        val confidence: Float,
    )

    private data class PreprocessResult(
        val buffer: FloatBuffer,
        val scaleX: Float,
        val scaleY: Float,
        val padX: Float,
        val padY: Float,
    )

    companion object {
        private const val TAG = "LcdDisplayDetector"
        private const val MODEL_ASSET = "lcd_detector.onnx"
        private const val INPUT_SIZE = 640L
        private const val CONF_THRESHOLD = 0.5f
        private const val NMS_THRESHOLD = 0.5f
    }
}
