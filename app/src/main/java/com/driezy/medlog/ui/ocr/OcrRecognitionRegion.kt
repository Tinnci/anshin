package com.driezy.medlog.ui.ocr

import kotlin.math.roundToInt

data class OcrRecognitionRegion(val enabled: Boolean, val widthFraction: Float, val aspectRatio: Float) {
    fun cropBounds(imageWidth: Int, imageHeight: Int): OcrCropBounds? {
        if (!enabled || imageWidth <= 0 || imageHeight <= 0) return null

        val safeWidthFraction = widthFraction.coerceIn(0.2f, 1f)
        val safeAspectRatio = aspectRatio.coerceAtLeast(0.2f)
        val maxWidth = imageWidth * safeWidthFraction
        val maxHeight = imageHeight * 0.94f

        var cropWidth = maxWidth
        var cropHeight = cropWidth / safeAspectRatio
        if (cropHeight > maxHeight) {
            cropHeight = maxHeight
            cropWidth = cropHeight * safeAspectRatio
        }

        val width = cropWidth.roundToInt().coerceIn(1, imageWidth)
        val height = cropHeight.roundToInt().coerceIn(1, imageHeight)
        val x = ((imageWidth - width) / 2f).roundToInt().coerceIn(0, imageWidth - width)
        val y = ((imageHeight - height) / 2f).roundToInt().coerceIn(0, imageHeight - height)

        return OcrCropBounds(x = x, y = y, width = width, height = height)
    }

    companion object {
        val FullImage = OcrRecognitionRegion(
            enabled = false,
            widthFraction = 1f,
            aspectRatio = 1f,
        )
    }
}

data class OcrCropBounds(val x: Int, val y: Int, val width: Int, val height: Int)
