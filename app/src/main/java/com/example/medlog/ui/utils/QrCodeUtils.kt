package com.example.medlog.ui.utils

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 生成 QR 码 Bitmap。
 *
 * @param content 要编码的字符串内容（UTF-8）
 * @param size    输出图片边长（px），默认 512
 * @param darkColor  深色像素颜色，默认黑色
 * @param lightColor 浅色像素颜色，默认白色
 */
fun generateQrBitmap(
    content: String,
    size: Int = 512,
    darkColor: Int = Color.BLACK,
    lightColor: Int = Color.WHITE,
): Bitmap? = runCatching {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET      to "UTF-8",
        EncodeHintType.ERROR_CORRECTION   to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN             to 2,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bmp = createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp[x, y] = if (matrix[x, y]) darkColor else lightColor
        }
    }
    bmp
}.getOrNull()
