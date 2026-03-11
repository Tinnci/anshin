package com.example.medlog.ui.ocr

import androidx.camera.core.ImageProxy
import javax.inject.Inject

/**
 * 纯 ML Kit 文字识别管线，适用于药品名称等通用 OCR 场景。
 *
 * 不使用 ONNX 模型，仅依赖 ML Kit 多路预处理策略。
 */
class MlKitOcrPipeline @Inject constructor() : OcrPipeline {
    override fun recognize(imageProxy: ImageProxy, onResult: (List<String>) -> Unit) {
        processImage(imageProxy, sevenSegRecognizer = null, lcdDetector = null, onResult = onResult)
    }
}
