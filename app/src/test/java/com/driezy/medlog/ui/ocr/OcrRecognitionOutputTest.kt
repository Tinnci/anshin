package com.driezy.medlog.ui.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrRecognitionOutputTest {

    @Test
    fun `merged texts keep source order and remove normalized duplicates`() {
        val output = OcrRecognitionOutput(
            groups = listOf(
                OcrResultGroup(
                    source = OcrResultSource.ML_KIT_ORIGINAL,
                    texts = listOf(" Amoxicillin 500mg ", "120/80"),
                ),
                OcrResultGroup(
                    source = OcrResultSource.PREPROCESSED_VARIANTS,
                    texts = listOf("Amoxicillin  500mg", " 98 mg/dL "),
                ),
                OcrResultGroup(
                    source = OcrResultSource.SEVEN_SEGMENT_MODEL,
                    texts = listOf("120/80"),
                ),
            ),
        )

        assertEquals(
            listOf("Amoxicillin 500mg", "120/80", "98 mg/dL"),
            output.mergedTexts,
        )
    }
}
