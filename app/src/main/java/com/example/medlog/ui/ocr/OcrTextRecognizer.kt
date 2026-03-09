package com.example.medlog.ui.ocr

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "OcrTextRecognizer"

private val mainHandler = Handler(Looper.getMainLooper())

/**
 * 使用 ML Kit 从 [ImageProxy] 中识别文字。
 *
 * 采用多路识别策略应对七段数码管 (7-segment display) 等难以识别的场景：
 * 1. 原始图像直接识别 (ML Kit)
 * 2. 高对比灰度增强版本 (ML Kit)
 * 3. 二值化 + 膨胀版本（填充段间间隙）(ML Kit)
 * 4. 反色版本（暗底亮字 LCD）(ML Kit)
 * 5. 七段管专用 CRNN 模型 (ONNX Runtime)
 * 6. LCD 区域检测 → 裁剪 → 专门识别 (可选)
 *
 * 所有变体的识别结果合并去重后回调到主线程。
 */
@SuppressLint("UnsafeOptInUsageError")
internal fun processImage(
    imageProxy: ImageProxy,
    sevenSegRecognizer: SevenSegmentRecognizer?,
    lcdDetector: LcdDisplayDetector? = null,
    onResult: (List<String>) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        mainHandler.post { onResult(emptyList()) }
        return
    }

    // 先创建原始 InputImage 让 ML Kit 处理
    val originalInput = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

    // 同时将 ImageProxy 转为 Bitmap 用于预处理变体
    val sourceBitmap = imageProxyToBitmap(imageProxy)

    // 七段管专用模型识别（同步，非常快 ~316KB 模型）
    val sevenSegResult = if (sourceBitmap != null && sevenSegRecognizer != null) {
        sevenSegRecognizer.recognize(sourceBitmap)?.let { listOf(it) } ?: emptyList()
    } else {
        emptyList()
    }

    // LCD 区域检测 → 裁剪后用 CRNN 专门识别
    val lcdCropResults = mutableListOf<String>()
    if (sourceBitmap != null && lcdDetector != null && sevenSegRecognizer != null) {
        val detections = lcdDetector.detect(sourceBitmap)
        for (det in detections) {
            val r = det.rect
            val x = (r.left * sourceBitmap.width).toInt().coerceIn(0, sourceBitmap.width - 1)
            val y = (r.top * sourceBitmap.height).toInt().coerceIn(0, sourceBitmap.height - 1)
            val w = ((r.right - r.left) * sourceBitmap.width).toInt().coerceAtLeast(1)
                .coerceAtMost(sourceBitmap.width - x)
            val h = ((r.bottom - r.top) * sourceBitmap.height).toInt().coerceAtLeast(1)
                .coerceAtMost(sourceBitmap.height - y)
            if (w > 10 && h > 10) {
                val crop = Bitmap.createBitmap(sourceBitmap, x, y, w, h)
                sevenSegRecognizer.recognize(crop)?.let { lcdCropResults.add(it) }
                crop.recycle()
            }
        }
    }

    // 生成预处理变体
    val variantBitmaps = if (sourceBitmap != null) {
        OcrImagePreprocessor.generateVariants(sourceBitmap)
    } else {
        emptyList()
    }

    val variantInputs = variantBitmaps.map { InputImage.fromBitmap(it, 0) }

    // 所有需要识别的输入：原始 + 变体
    val allInputs = listOf(originalInput) + variantInputs
    val allResults = Array<List<String>>(allInputs.size) { emptyList() }
    val completedCount = AtomicInteger(0)

    val recognizer = try {
        createLocalizedTextRecognizer()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create text recognizer", e)
        imageProxy.close()
        variantBitmaps.forEach { it.recycle() }
        sourceBitmap?.recycle()
        mainHandler.post { onResult(emptyList()) }
        return
    }

    for (i in allInputs.indices) {
        recognizer.process(allInputs[i])
            .addOnSuccessListener { visionText ->
                val lines = visionText.textBlocks
                    .flatMap { block -> block.lines.map { it.text.trim() } }
                    .filter { it.isNotBlank() }
                allResults[i] = lines
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "OCR pass $i failed", e)
            }
            .addOnCompleteListener {
                if (completedCount.incrementAndGet() == allInputs.size) {
                    // 所有识别完成，合并去重 → 回调（包括七段管模型结果 + LCD 检测裁剪结果）
                    val merged = mergeOcrResults(allResults, sevenSegResult + lcdCropResults)
                    imageProxy.close()
                    variantBitmaps.forEach { bmp -> bmp.recycle() }
                    sourceBitmap?.recycle()
                    recognizer.close()
                    mainHandler.post { onResult(merged) }
                }
            }
    }
}

/**
 * 将 ImageProxy 转换为正确旋转的 Bitmap。
 */
private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? = try {
    val bitmap = imageProxy.toBitmap()
    val rotation = imageProxy.imageInfo.rotationDegrees
    if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    } else {
        bitmap
    }
} catch (e: Exception) {
    Log.w(TAG, "Failed to convert ImageProxy to Bitmap", e)
    null
}

/**
 * 合并多路 OCR 结果：去重，优先保留原始识别结果的顺序。
 * 七段管模型结果追加在末尾（可能含不同格式的数字）。
 */
private fun mergeOcrResults(allResults: Array<List<String>>, extraResults: List<String> = emptyList()): List<String> {
    val seen = mutableSetOf<String>()
    val merged = mutableListOf<String>()

    for (results in allResults) {
        for (line in results) {
            // 标准化后去重（忽略空格差异）
            val normalized = line.replace("\\s+".toRegex(), " ").trim()
            if (normalized.isNotEmpty() && seen.add(normalized)) {
                merged.add(line)
            }
        }
    }
    // 追加额外结果（七段管模型等）
    for (line in extraResults) {
        val normalized = line.replace("\\s+".toRegex(), " ").trim()
        if (normalized.isNotEmpty() && seen.add(normalized)) {
            merged.add(line)
        }
    }
    return merged
}

/**
 * 根据设备语言创建对应的 ML Kit 文字识别器。
 * - ja → 日语识别器
 * - ko → 韩语识别器
 * - 其他 → 中文识别器（默认）
 */
private fun createLocalizedTextRecognizer(): TextRecognizer = when (Locale.getDefault().language) {
    "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    "ko" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    else -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
}
