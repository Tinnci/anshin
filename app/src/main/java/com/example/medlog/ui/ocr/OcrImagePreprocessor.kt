package com.example.medlog.ui.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.annotation.VisibleForTesting

/**
 * 七段数码管 (7-segment display) 图像预处理器。
 *
 * 血压计、血糖仪、体温枪等医疗设备的 LCD/LED 屏幕使用七段数码管显示数字，
 * 这类数字外形与印刷体差异较大，ML Kit 直接识别效果很差。
 *
 * 本预处理器通过以下步骤将七段数码管数字增强为更接近印刷体的外观：
 * 1. 灰度化 + 高对比度增强
 * 2. 二值化（Otsu 阈值法）
 * 3. 形态学膨胀（加粗笔画，填充段间间隙）
 * 4. 可选反色（暗底亮字 → 白底黑字）
 */
internal object OcrImagePreprocessor {

    /**
     * 从原始 Bitmap 生成多个预处理变体，用于多路 OCR 识别。
     *
     * @return 预处理后的 Bitmap 列表（不含原图）
     */
    fun generateVariants(source: Bitmap): List<Bitmap> {
        val grayscale = toGrayscaleHighContrast(source)
        val binarized = binarizeOtsu(grayscale)
        val dilated = dilate(binarized, radius = 1)

        val variants = mutableListOf<Bitmap>()

        // 变体 1：高对比灰度（不二值化，保留更多细节给 ML Kit）
        variants.add(grayscale)

        // 变体 2：膨胀后的二值图（填充段间间隙）
        variants.add(dilated)

        // 变体 3：反色版本（如果原图是暗底亮字）
        val inverted = invert(dilated)
        variants.add(inverted)

        return variants
    }

    /**
     * 灰度化 + 高对比度增强。
     * 使用 ColorMatrix 将图像转为灰度并提升对比度（1.8x）。
     */
    @VisibleForTesting
    internal fun toGrayscaleHighContrast(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 灰度矩阵
        val grayscaleMatrix = ColorMatrix().apply { setSaturation(0f) }

        // 对比度增强矩阵（scale = 1.8, translate 使中间灰不偏移）
        val contrastScale = 1.8f
        val translate = (1f - contrastScale) * 128f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrastScale, 0f, 0f, 0f, translate,
                0f, contrastScale, 0f, 0f, translate,
                0f, 0f, contrastScale, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            ),
        )

        // 组合：灰度 → 对比度
        contrastMatrix.preConcat(grayscaleMatrix)
        paint.colorFilter = ColorMatrixColorFilter(contrastMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    /**
     * Otsu 二值化：自动计算最优阈值，将灰度图转为纯黑白。
     * 保证七段数码管的段与背景形成最大对比。
     */
    @VisibleForTesting
    internal fun binarizeOtsu(grayscale: Bitmap): Bitmap {
        val width = grayscale.width
        val height = grayscale.height
        val pixels = IntArray(width * height)
        grayscale.getPixels(pixels, 0, width, 0, 0, width, height)

        // 提取亮度通道
        val luminance = IntArray(pixels.size) { pixels[it] and 0xFF }

        // 计算灰度直方图
        val histogram = IntArray(256)
        for (lum in luminance) histogram[lum]++

        // Otsu 算法：找到使类间方差最大的阈值
        val threshold = otsuThreshold(histogram, pixels.size)

        // 应用阈值
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(pixels.size)
        for (i in luminance.indices) {
            val color = if (luminance[i] > threshold) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            outPixels[i] = color
        }
        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * 形态学膨胀：用正方形结构元素扩张前景像素。
     * 效果：加粗七段数码管的笔画，填充段间的微小间隙。
     */
    @VisibleForTesting
    internal fun dilate(binaryBitmap: Bitmap, radius: Int): Bitmap {
        val width = binaryBitmap.width
        val height = binaryBitmap.height
        val pixels = IntArray(width * height)
        binaryBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 判断前景色：统计黑色和白色像素数，少数派为前景
        var blackCount = 0
        for (p in pixels) {
            if (p and 0xFF < 128) blackCount++
        }
        val foregroundIsDark = blackCount < pixels.size / 2

        val outPixels = pixels.copyOf()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val isDark = pixels[idx] and 0xFF < 128
                val isForeground = if (foregroundIsDark) isDark else !isDark
                if (!isForeground) continue

                // 膨胀：将周围 radius 范围内的像素设为前景色
                val fgColor = if (foregroundIsDark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0 until width && ny in 0 until height) {
                            outPixels[ny * width + nx] = fgColor
                        }
                    }
                }
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * 反色：将黑白互换。
     * 适用于暗底亮字的 LCD 显示屏（如黑底绿字的血压计）。
     */
    @VisibleForTesting
    internal fun invert(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val invertMatrix = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        paint.colorFilter = ColorMatrixColorFilter(invertMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    /** Otsu 阈值计算 */
    private fun otsuThreshold(histogram: IntArray, totalPixels: Int): Int {
        var sumAll = 0L
        for (i in 0..255) sumAll += i.toLong() * histogram[i]

        var sumBg = 0L
        var weightBg = 0
        var maxVariance = 0.0
        var bestThreshold = 0

        for (t in 0..255) {
            weightBg += histogram[t]
            if (weightBg == 0) continue
            val weightFg = totalPixels - weightBg
            if (weightFg == 0) break

            sumBg += t.toLong() * histogram[t]
            val meanBg = sumBg.toDouble() / weightBg
            val meanFg = (sumAll - sumBg).toDouble() / weightFg
            val variance = weightBg.toDouble() * weightFg * (meanBg - meanFg) * (meanBg - meanFg)

            if (variance > maxVariance) {
                maxVariance = variance
                bestThreshold = t
            }
        }
        return bestThreshold
    }
}
