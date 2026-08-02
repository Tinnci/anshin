package com.driezy.medlog.capability.ocr

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
     * @param imageProxy         CameraX 拍摄的图像
     * @param recognitionRegion  识别区域；用于让取景框和实际 OCR 输入保持一致
     * @param onResult           识别完成回调，返回按来源分组的文本结果（主线程）
     */
    fun recognize(
        imageProxy: ImageProxy,
        recognitionRegion: OcrRecognitionRegion = OcrRecognitionRegion.FullImage,
        onResult: (OcrRecognitionOutput) -> Unit,
    )
}
