package com.driezy.medlog.ui.ocr

import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.get
import androidx.core.graphics.scale
import kotlin.math.roundToInt

private const val TAG = "OcrFrameInputPreprocessor"
private const val MIN_RECOGNITION_LONG_EDGE = 1280
private const val MAX_RECOGNITION_LONG_EDGE = 2400

internal object OcrFrameInputPreprocessor {

    fun prepare(source: Bitmap, recognitionRegion: OcrRecognitionRegion): PreparedFrame {
        val cropped = cropToRecognitionRegion(source, recognitionRegion)
        val normalized = normalizeSize(cropped)
        if (normalized !== cropped && cropped !== source) {
            cropped.recycle()
        }

        val quality = assess(normalized)
        Log.d(
            TAG,
            "Prepared OCR frame ${normalized.width}x${normalized.height} " +
                "brightness=${quality.meanLuminance.roundToInt()} " +
                "contrast=${quality.contrast.roundToInt()}",
        )
        return PreparedFrame(bitmap = normalized, quality = quality)
    }

    private fun cropToRecognitionRegion(bitmap: Bitmap, recognitionRegion: OcrRecognitionRegion): Bitmap {
        val bounds = recognitionRegion.cropBounds(bitmap.width, bitmap.height) ?: return bitmap
        if (bounds.x == 0 && bounds.y == 0 && bounds.width == bitmap.width && bounds.height == bitmap.height) {
            return bitmap
        }
        return Bitmap.createBitmap(bitmap, bounds.x, bounds.y, bounds.width, bounds.height)
    }

    private fun normalizeSize(bitmap: Bitmap): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        val targetLongEdge = longEdge.coerceIn(MIN_RECOGNITION_LONG_EDGE, MAX_RECOGNITION_LONG_EDGE)
        if (targetLongEdge == longEdge) return bitmap

        val scale = targetLongEdge.toFloat() / longEdge.toFloat()
        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return bitmap.scale(targetWidth, targetHeight)
    }

    private fun assess(bitmap: Bitmap): OcrFrameQuality {
        val sampleStep = maxOf(1, minOf(bitmap.width, bitmap.height) / 96)
        var count = 0
        var sum = 0.0
        var sumSquares = 0.0

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap[x, y]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luminance = 0.299 * r + 0.587 * g + 0.114 * b
                sum += luminance
                sumSquares += luminance * luminance
                count++
                x += sampleStep
            }
            y += sampleStep
        }

        val mean = if (count == 0) 0.0 else sum / count
        val variance = if (count == 0) 0.0 else (sumSquares / count) - (mean * mean)
        return OcrFrameQuality(
            meanLuminance = mean,
            contrast = kotlin.math.sqrt(variance.coerceAtLeast(0.0)),
        )
    }
}

internal data class PreparedFrame(val bitmap: Bitmap, val quality: OcrFrameQuality)

internal data class OcrFrameQuality(val meanLuminance: Double, val contrast: Double)
