package com.driezy.medlog.feature.health.application

import com.driezy.medlog.capability.ai.AiCloudConfigResolver
import com.driezy.medlog.data.model.NetworkType
import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.SettingsPreferences

data class HealthCloudImageAnalysisAvailability(
    val isAvailable: Boolean,
    val reason: HealthCloudImageAnalysisUnavailableReason? = null,
)

enum class HealthCloudImageAnalysisUnavailableReason {
    CLOUD_AI_DISABLED,
    IMAGE_ANALYSIS_DISABLED,
    API_KEY_MISSING,
    WIFI_REQUIRED,
    OPENAI_COMPATIBLE_BASE_URL_MISSING,
    IMAGE_INPUT_UNSUPPORTED,
}

object HealthCloudImageAnalysisGate {
    fun evaluate(
        settings: SettingsPreferences,
        availableProviders: Set<CloudAiProvider>,
        networkType: NetworkType,
    ): HealthCloudImageAnalysisAvailability {
        if (!settings.cloudAiEnabled) {
            return unavailable(HealthCloudImageAnalysisUnavailableReason.CLOUD_AI_DISABLED)
        }
        if (!settings.cloudAiImageAnalysisEnabled) {
            return unavailable(HealthCloudImageAnalysisUnavailableReason.IMAGE_ANALYSIS_DISABLED)
        }
        if (settings.cloudAiProvider !in availableProviders) {
            return unavailable(HealthCloudImageAnalysisUnavailableReason.API_KEY_MISSING)
        }
        if (
            settings.cloudAiProvider == CloudAiProvider.OPENAI_COMPATIBLE &&
            settings.openAiCompatibleBaseUrl.isBlank()
        ) {
            return unavailable(HealthCloudImageAnalysisUnavailableReason.OPENAI_COMPATIBLE_BASE_URL_MISSING)
        }
        if (!AiCloudConfigResolver.resolveCapabilities(settings).supportsImageInput) {
            return unavailable(HealthCloudImageAnalysisUnavailableReason.IMAGE_INPUT_UNSUPPORTED)
        }
        if (settings.cloudAiWifiOnly && networkType != NetworkType.WIFI) {
            return unavailable(HealthCloudImageAnalysisUnavailableReason.WIFI_REQUIRED)
        }
        return HealthCloudImageAnalysisAvailability(isAvailable = true)
    }

    private fun unavailable(reason: HealthCloudImageAnalysisUnavailableReason): HealthCloudImageAnalysisAvailability =
        HealthCloudImageAnalysisAvailability(isAvailable = false, reason = reason)
}
