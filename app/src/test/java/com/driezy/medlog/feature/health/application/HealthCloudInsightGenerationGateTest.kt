package com.driezy.medlog.feature.health.application

import com.driezy.medlog.data.model.NetworkType
import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.SettingsPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCloudInsightGenerationGateTest {

    @Test
    fun `health insights are available only when enabled key exists and network policy allows`() {
        val settings = SettingsPreferences(
            cloudAiEnabled = true,
            cloudAiHealthInsightsEnabled = true,
            cloudAiWifiOnly = true,
            cloudAiProvider = CloudAiProvider.ANTHROPIC,
        )

        val availability = HealthCloudInsightGenerationGate.evaluate(
            settings = settings,
            availableProviders = setOf(CloudAiProvider.ANTHROPIC),
            networkType = NetworkType.WIFI,
        )

        assertTrue(availability.isAvailable)
        assertEquals(null, availability.reason)
    }

    @Test
    fun `health insights are blocked by feature flags key and network policy`() {
        val base = SettingsPreferences(
            cloudAiEnabled = true,
            cloudAiHealthInsightsEnabled = true,
            cloudAiWifiOnly = true,
            cloudAiProvider = CloudAiProvider.MIMO,
        )

        assertEquals(
            HealthCloudInsightUnavailableReason.CLOUD_AI_DISABLED,
            HealthCloudInsightGenerationGate.evaluate(
                base.copy(cloudAiEnabled = false),
                emptySet(),
                NetworkType.WIFI,
            ).reason,
        )
        assertEquals(
            HealthCloudInsightUnavailableReason.HEALTH_INSIGHTS_DISABLED,
            HealthCloudInsightGenerationGate.evaluate(
                base.copy(cloudAiHealthInsightsEnabled = false),
                emptySet(),
                NetworkType.WIFI,
            ).reason,
        )
        assertEquals(
            HealthCloudInsightUnavailableReason.API_KEY_MISSING,
            HealthCloudInsightGenerationGate.evaluate(base, emptySet(), NetworkType.WIFI).reason,
        )
        assertEquals(
            HealthCloudInsightUnavailableReason.WIFI_REQUIRED,
            HealthCloudInsightGenerationGate.evaluate(base, setOf(CloudAiProvider.MIMO), NetworkType.CELLULAR).reason,
        )
    }

    @Test
    fun `openai compatible health insights require base url`() {
        val settings = SettingsPreferences(
            cloudAiEnabled = true,
            cloudAiHealthInsightsEnabled = true,
            cloudAiWifiOnly = false,
            cloudAiProvider = CloudAiProvider.OPENAI_COMPATIBLE,
            openAiCompatibleBaseUrl = "",
        )

        val availability = HealthCloudInsightGenerationGate.evaluate(
            settings = settings,
            availableProviders = setOf(CloudAiProvider.OPENAI_COMPATIBLE),
            networkType = NetworkType.CELLULAR,
        )

        assertFalse(availability.isAvailable)
        assertEquals(HealthCloudInsightUnavailableReason.OPENAI_COMPATIBLE_BASE_URL_MISSING, availability.reason)
    }
}
