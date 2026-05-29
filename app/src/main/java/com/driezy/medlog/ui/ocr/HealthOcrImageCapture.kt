package com.driezy.medlog.ui.ocr

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

private const val TAG = "HealthOcrImageCapture"
private const val MAX_CLOUD_IMAGE_DIMENSION = 1600
private const val CLOUD_IMAGE_JPEG_QUALITY = 84

internal fun ImageProxy.toCloudAnalysisJpegBytes(): ByteArray? = try {
    val source = toBitmap()
    val rotated = source.rotateIfNeeded(imageInfo.rotationDegrees)
    val scaled = rotated.scaleDown(MAX_CLOUD_IMAGE_DIMENSION)
    val bytes = ByteArrayOutputStream().use { output ->
        scaled.compress(Bitmap.CompressFormat.JPEG, CLOUD_IMAGE_JPEG_QUALITY, output)
        output.toByteArray()
    }
    if (scaled !== rotated) scaled.recycle()
    if (rotated !== source) rotated.recycle()
    source.recycle()
    bytes
} catch (e: Exception) {
    Log.w(TAG, "Failed to prepare health OCR image for cloud analysis", e)
    null
}

private fun Bitmap.rotateIfNeeded(rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) return this
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun Bitmap.scaleDown(maxDimension: Int): Bitmap {
    val currentMax = maxOf(width, height)
    if (currentMax <= maxDimension) return this
    val scale = maxDimension.toFloat() / currentMax.toFloat()
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}
