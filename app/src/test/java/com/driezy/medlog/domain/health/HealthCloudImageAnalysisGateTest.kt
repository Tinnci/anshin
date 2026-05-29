package com.driezy.medlog.domain.health

import com.driezy.medlog.data.model.NetworkType
import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.SettingsPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCloudImageAnalysisGateTest {

    @Test
    fun `image analysis is available only when enabled key exists and network policy allows`() {
        val settings = SettingsPreferences(
            cloudAiEnabled = true,
            cloudAiImageAnalysisEnabled = true,
            cloudAiWifiOnly = true,
            cloudAiProvider = CloudAiProvider.MIMO,
        )

        val available = HealthCloudImageAnalysisGate.evaluate(
            settings = settings,
            availableProviders = setOf(CloudAiProvider.MIMO),
            networkType = NetworkType.WIFI,
        )

        assertTrue(available.isAvailable)
        assertEquals(null, available.reason)
    }

    @Test
    fun `image analysis is blocked by feature flags api key and cellular policy`() {
        val base = SettingsPreferences(
            cloudAiEnabled = true,
            cloudAiImageAnalysisEnabled = true,
            cloudAiWifiOnly = true,
            cloudAiProvider = CloudAiProvider.GEMINI,
        )

        assertEquals(
            HealthCloudImageAnalysisUnavailableReason.CLOUD_AI_DISABLED,
            HealthCloudImageAnalysisGate.evaluate(base.copy(cloudAiEnabled = false), emptySet(), NetworkType.WIFI).reason,
        )
        assertEquals(
            HealthCloudImageAnalysisUnavailableReason.IMAGE_ANALYSIS_DISABLED,
            HealthCloudImageAnalysisGate.evaluate(base.copy(cloudAiImageAnalysisEnabled = false), emptySet(), NetworkType.WIFI).reason,
        )
        assertEquals(
            HealthCloudImageAnalysisUnavailableReason.API_KEY_MISSING,
            HealthCloudImageAnalysisGate.evaluate(base, emptySet(), NetworkType.WIFI).reason,
        )
        assertEquals(
            HealthCloudImageAnalysisUnavailableReason.WIFI_REQUIRED,
            HealthCloudImageAnalysisGate.evaluate(base, setOf(CloudAiProvider.GEMINI), NetworkType.CELLULAR).reason,
        )
    }

    @Test
    fun `openai compatible provider requires base url`() {
        val settings = SettingsPreferences(
            cloudAiEnabled = true,
            cloudAiImageAnalysisEnabled = true,
            cloudAiWifiOnly = false,
            cloudAiProvider = CloudAiProvider.OPENAI_COMPATIBLE,
            openAiCompatibleBaseUrl = "",
        )

        val availability = HealthCloudImageAnalysisGate.evaluate(
            settings = settings,
            availableProviders = setOf(CloudAiProvider.OPENAI_COMPATIBLE),
            networkType = NetworkType.CELLULAR,
        )

        assertFalse(availability.isAvailable)
        assertEquals(HealthCloudImageAnalysisUnavailableReason.OPENAI_COMPATIBLE_BASE_URL_MISSING, availability.reason)
    }

    @Test
    fun `image analysis is blocked when selected model does not support images`() {
        val settings = SettingsPreferences(
            cloudAiEnabled = true,
            cloudAiImageAnalysisEnabled = true,
            cloudAiWifiOnly = false,
            cloudAiProvider = CloudAiProvider.OPENAI_COMPATIBLE,
            cloudAiModel = "gpt-3.5-turbo",
            openAiCompatibleBaseUrl = "https://api.example.com/v1",
        )

        val availability = HealthCloudImageAnalysisGate.evaluate(
            settings = settings,
            availableProviders = setOf(CloudAiProvider.OPENAI_COMPATIBLE),
            networkType = NetworkType.CELLULAR,
        )

        assertFalse(availability.isAvailable)
        assertEquals(HealthCloudImageAnalysisUnavailableReason.IMAGE_INPUT_UNSUPPORTED, availability.reason)
    }
}
