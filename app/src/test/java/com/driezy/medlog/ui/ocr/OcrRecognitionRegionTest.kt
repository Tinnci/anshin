package com.driezy.medlog.ui.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrRecognitionRegionTest {

    @Test
    fun `center crop uses requested width and aspect ratio`() {
        val region = OcrRecognitionRegion(
            enabled = true,
            widthFraction = 0.5f,
            aspectRatio = 2f,
        )

        val bounds = region.cropBounds(imageWidth = 1000, imageHeight = 800)

        assertEquals(OcrCropBounds(x = 250, y = 275, width = 500, height = 250), bounds)
    }

    @Test
    fun `center crop is height constrained when aspect is tall`() {
        val region = OcrRecognitionRegion(
            enabled = true,
            widthFraction = 1f,
            aspectRatio = 0.5f,
        )

        val bounds = region.cropBounds(imageWidth = 1000, imageHeight = 800)

        assertEquals(OcrCropBounds(x = 312, y = 24, width = 376, height = 752), bounds)
    }

    @Test
    fun `disabled region scans full image`() {
        assertNull(OcrRecognitionRegion.FullImage.cropBounds(imageWidth = 1000, imageHeight = 800))
    }
}
