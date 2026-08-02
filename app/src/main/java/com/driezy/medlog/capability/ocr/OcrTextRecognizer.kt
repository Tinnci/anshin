package com.driezy.medlog.capability.ocr

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
    recognitionRegion: OcrRecognitionRegion = OcrRecognitionRegion.FullImage,
    sevenSegRecognizer: SevenSegmentRecognizer?,
    lcdDetector: LcdDisplayDetector? = null,
    onResult: (OcrRecognitionOutput) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        mainHandler.post { onResult(OcrRecognitionOutput.Empty) }
        return
    }

    // 将 ImageProxy 转为正确旋转的 Bitmap；如果提供识别区域，则只裁剪框内区域参与 OCR。
    val sourceBitmap = imageProxyToBitmap(imageProxy)
    val preparedFrame = sourceBitmap?.let { bitmap ->
        OcrFrameInputPreprocessor.prepare(bitmap, recognitionRegion)
    }
    val recognitionBitmap = preparedFrame?.bitmap

    // 使用框内 bitmap 作为 ML Kit 原始输入；转换失败时才退回整张 media image。
    val originalInput = if (recognitionBitmap != null) {
        InputImage.fromBitmap(recognitionBitmap, 0)
    } else {
        InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    }

    // 七段管专用模型识别（同步，非常快 ~316KB 模型）
    val sevenSegResult = if (recognitionBitmap != null && sevenSegRecognizer != null) {
        sevenSegRecognizer.recognize(recognitionBitmap)?.let { listOf(it) } ?: emptyList()
    } else {
        emptyList()
    }

    // LCD 区域检测 → 裁剪后用 CRNN 专门识别（支持多行分割）
    val lcdCropResults = mutableListOf<String>()
    if (recognitionBitmap != null && lcdDetector != null && sevenSegRecognizer != null) {
        val detections = lcdDetector.detect(recognitionBitmap)
        for (det in detections) {
            val r = det.rect
            // 过滤面积过大的检测框 (> 30% 图片面积，可能误检)
            val areaRatio = (r.right - r.left) * (r.bottom - r.top)
            if (areaRatio > 0.3f) continue

            val x = (r.left * recognitionBitmap.width).toInt().coerceIn(0, recognitionBitmap.width - 1)
            val y = (r.top * recognitionBitmap.height).toInt().coerceIn(0, recognitionBitmap.height - 1)
            val w = ((r.right - r.left) * recognitionBitmap.width).toInt().coerceAtLeast(1)
                .coerceAtMost(recognitionBitmap.width - x)
            val h = ((r.bottom - r.top) * recognitionBitmap.height).toInt().coerceAtLeast(1)
                .coerceAtMost(recognitionBitmap.height - y)
            if (w > 10 && h > 10) {
                val crop = Bitmap.createBitmap(recognitionBitmap, x, y, w, h)
                // 使用多行识别：水平投影分行 → 逐行 CRNN
                lcdCropResults.addAll(sevenSegRecognizer.recognizeRows(crop))
                crop.recycle()
            }
        }
    }

    // 生成预处理变体
    val variantBitmaps = if (recognitionBitmap != null) {
        OcrImagePreprocessor.generateVariants(recognitionBitmap)
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
        recycleRecognitionBitmaps(sourceBitmap, recognitionBitmap)
        mainHandler.post { onResult(OcrRecognitionOutput.Empty) }
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
                    val grouped = buildRecognitionOutput(allResults, sevenSegResult, lcdCropResults)
                    imageProxy.close()
                    variantBitmaps.forEach { bmp -> bmp.recycle() }
                    recycleRecognitionBitmaps(sourceBitmap, recognitionBitmap)
                    recognizer.close()
                    mainHandler.post { onResult(grouped) }
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

private fun recycleRecognitionBitmaps(sourceBitmap: Bitmap?, recognitionBitmap: Bitmap?) {
    if (recognitionBitmap != null && recognitionBitmap !== sourceBitmap) {
        recognitionBitmap.recycle()
    }
    sourceBitmap?.recycle()
}

private fun buildRecognitionOutput(
    allResults: Array<List<String>>,
    sevenSegResult: List<String>,
    lcdCropResults: List<String>,
): OcrRecognitionOutput {
    val groups = buildList {
        addResultGroup(OcrResultSource.ML_KIT_ORIGINAL, allResults.firstOrNull().orEmpty())
        addResultGroup(OcrResultSource.PREPROCESSED_VARIANTS, allResults.drop(1).flatten())
        addResultGroup(OcrResultSource.SEVEN_SEGMENT_MODEL, sevenSegResult)
        addResultGroup(OcrResultSource.LCD_CROP_MODEL, lcdCropResults)
    }
    return OcrRecognitionOutput(groups)
}

private fun MutableList<OcrResultGroup>.addResultGroup(source: OcrResultSource, texts: List<String>) {
    val seen = mutableSetOf<String>()
    val cleaned = texts
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filter { seen.add(it.replace("\\s+".toRegex(), " ")) }
    if (cleaned.isNotEmpty()) {
        add(OcrResultGroup(source, cleaned))
    }
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
