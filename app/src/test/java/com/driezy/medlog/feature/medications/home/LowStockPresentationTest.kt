package com.driezy.medlog.feature.medications.home

import org.junit.Assert.assertEquals
import org.junit.Test

class LowStockPresentationTest {
    @Test
    fun `presentation collapses duplicate medication names and keeps lowest stock`() {
        val presentation = LowStockPresentation.from(
            listOf(
                "Amoxicillin" to (2.0 to "mg"),
                "Amoxicillin" to (1.0 to "mg"),
                "Metformin" to (5.0 to "mg"),
            ),
        )

        assertEquals(2, presentation.visibleItems.size)
        assertEquals("Amoxicillin", presentation.visibleItems[0].name)
        assertEquals(1.0, presentation.visibleItems[0].stock, 0.0)
    }

    @Test
    fun `presentation limits visible rows and summarizes hidden items`() {
        val presentation = LowStockPresentation.from(
            listOf(
                "A" to (1.0 to "tablet"),
                "B" to (2.0 to "tablet"),
                "C" to (3.0 to "tablet"),
                "D" to (4.0 to "tablet"),
                "E" to (5.0 to "tablet"),
            ),
        )

        assertEquals(3, presentation.visibleItems.size)
        assertEquals(2, presentation.hiddenCount)
    }
}
