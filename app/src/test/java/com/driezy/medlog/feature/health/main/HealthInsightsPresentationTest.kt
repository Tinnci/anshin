package com.driezy.medlog.feature.health

import com.driezy.medlog.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthInsightsPresentationTest {
    @Test
    fun `refreshing empty insights shows pending body message`() {
        val presentation = HealthInsightsPresentation.from(
            insightCount = 0,
            isRefreshing = true,
        )

        assertTrue(presentation.showPendingBody)
        assertEquals(R.string.health_insights_pending_body, presentation.pendingBodyRes)
    }

    @Test
    fun `existing insights do not show pending body while refreshing`() {
        val presentation = HealthInsightsPresentation.from(
            insightCount = 2,
            isRefreshing = true,
        )

        assertEquals(false, presentation.showPendingBody)
    }
}
