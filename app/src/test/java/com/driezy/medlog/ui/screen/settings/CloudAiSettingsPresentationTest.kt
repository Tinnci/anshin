package com.driezy.medlog.ui.screen.settings

import com.driezy.medlog.R
import com.driezy.medlog.data.model.AiUsageFeature
import com.driezy.medlog.data.repository.AiUsageSummaryRow
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudAiSettingsPresentationTest {
    @Test
    fun `disabled cloud AI is presented as off`() {
        val presentation = CloudAiSettingsPresentation.from(
            enabled = false,
            hasApiKey = false,
            supportsImageInput = true,
        )

        assertEquals(CloudAiSettingsVisualState.OFF, presentation.visualState)
        assertEquals(R.string.settings_ai_status_off, presentation.labelRes)
    }

    @Test
    fun `enabled cloud AI without key asks for setup`() {
        val presentation = CloudAiSettingsPresentation.from(
            enabled = true,
            hasApiKey = false,
            supportsImageInput = true,
        )

        assertEquals(CloudAiSettingsVisualState.NEEDS_KEY, presentation.visualState)
        assertEquals(R.string.settings_ai_status_needs_key, presentation.labelRes)
    }

    @Test
    fun `ready cloud AI with image support is fully ready`() {
        val presentation = CloudAiSettingsPresentation.from(
            enabled = true,
            hasApiKey = true,
            supportsImageInput = true,
        )

        assertEquals(CloudAiSettingsVisualState.READY, presentation.visualState)
        assertEquals(R.string.settings_ai_status_ready, presentation.labelRes)
    }

    @Test
    fun `ready cloud AI without image support is text only`() {
        val presentation = CloudAiSettingsPresentation.from(
            enabled = true,
            hasApiKey = true,
            supportsImageInput = false,
        )

        assertEquals(CloudAiSettingsVisualState.TEXT_ONLY, presentation.visualState)
        assertEquals(R.string.settings_ai_status_text_only, presentation.labelRes)
    }

    @Test
    fun `usage summary presentation totals internal counters without raw request details`() {
        val presentation = CloudAiUsageSummaryPresentation.from(
            listOf(
                AiUsageSummaryRow(
                    feature = AiUsageFeature.HEALTH_INSIGHT,
                    totalCount = 3,
                    successCount = 2,
                    fallbackCount = 0,
                    errorCount = 1,
                    cacheHitCount = 1,
                    lastUsedAt = 2_000L,
                    lastErrorCategory = "POLICY_VIOLATION",
                ),
                AiUsageSummaryRow(
                    feature = AiUsageFeature.IMAGE_OCR,
                    totalCount = 2,
                    successCount = 2,
                    fallbackCount = 0,
                    errorCount = 0,
                    cacheHitCount = 1,
                    lastUsedAt = 3_000L,
                    lastErrorCategory = null,
                ),
            ),
        )

        assertEquals(false, presentation.isEmpty)
        assertEquals(5, presentation.totalCount)
        assertEquals(4, presentation.successCount)
        assertEquals(1, presentation.errorCount)
        assertEquals(2, presentation.cacheHitCount)
        assertEquals("POLICY_VIOLATION", presentation.latestErrorCategory)
    }
}
