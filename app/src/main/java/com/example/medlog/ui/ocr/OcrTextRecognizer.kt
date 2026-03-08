package com.example.medlog.ui.ocr

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.util.Locale

private const val TAG = "OcrTextRecognizer"

private val mainHandler = Handler(Looper.getMainLooper())

/**
 * 使用 ML Kit 从 [ImageProxy] 中识别文字，结果回调到主线程。
 */
@SuppressLint("UnsafeOptInUsageError")
internal fun processImage(
    imageProxy: ImageProxy,
    onResult: (List<String>) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        mainHandler.post { onResult(emptyList()) }
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    val recognizer = try {
        createLocalizedTextRecognizer()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create text recognizer", e)
        imageProxy.close()
        mainHandler.post { onResult(emptyList()) }
        return
    }

    recognizer.process(inputImage)
        .addOnSuccessListener { visionText ->
            val lines = visionText.textBlocks
                .flatMap { block -> block.lines.map { it.text.trim() } }
                .filter { it.isNotBlank() }
            onResult(lines)
        }
        .addOnFailureListener { e ->
            Log.e(TAG, "OCR failed", e)
            onResult(emptyList())
        }
        .addOnCompleteListener {
            imageProxy.close()
            recognizer.close()
        }
}

/**
 * 根据设备语言创建对应的 ML Kit 文字识别器。
 * - ja → 日语识别器
 * - ko → 韩语识别器
 * - 其他 → 中文识别器（默认）
 */
private fun createLocalizedTextRecognizer() = when (Locale.getDefault().language) {
    "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    "ko" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    else -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
}
