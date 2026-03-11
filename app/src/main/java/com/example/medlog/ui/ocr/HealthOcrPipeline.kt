package com.example.medlog.ui.ocr

import android.content.Context
import androidx.camera.core.ImageProxy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 体征识别管线：ML Kit + 七段管 CRNN + LCD 区域检测。
 *
 * 拥有 ONNX 模型实例的生命周期，通过 [close] 释放。
 */
class HealthOcrPipeline @Inject constructor(
    @ApplicationContext context: Context,
) : OcrPipeline, AutoCloseable {

    private val sevenSegRecognizer = SevenSegmentRecognizer(context)
    private val lcdDetector = LcdDisplayDetector(context)

    override fun recognize(imageProxy: ImageProxy, onResult: (List<String>) -> Unit) {
        processImage(imageProxy, sevenSegRecognizer, lcdDetector, onResult)
    }

    override fun close() {
        sevenSegRecognizer.close()
        lcdDetector.close()
    }
}
