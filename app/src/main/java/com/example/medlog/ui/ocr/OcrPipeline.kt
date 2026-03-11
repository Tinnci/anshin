package com.example.medlog.ui.ocr

import androidx.camera.core.ImageProxy

/**
 * OCR 识别管线策略接口。
 *
 * 不同场景使用不同的实现：
 * - [MlKitOcrPipeline]: 纯 ML Kit 文字识别（药品名称等通用场景）
 * - [HealthOcrPipeline]: ML Kit + 七段管 CRNN + LCD 检测（体征数据场景）
 */
interface OcrPipeline {
    /**
     * 对拍摄的图像执行 OCR 识别。
     *
     * @param imageProxy CameraX 拍摄的图像
     * @param onResult   识别完成回调，返回去重后的文本行列表（主线程）
     */
    fun recognize(imageProxy: ImageProxy, onResult: (List<String>) -> Unit)
}
