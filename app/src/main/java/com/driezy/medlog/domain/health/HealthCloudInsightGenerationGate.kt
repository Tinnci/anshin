package com.driezy.medlog.domain.health

import com.driezy.medlog.data.model.NetworkType
import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.SettingsPreferences

data class HealthCloudInsightAvailability(
    val isAvailable: Boolean,
    val reason: HealthCloudInsightUnavailableReason? = null,
)

enum class HealthCloudInsightUnavailableReason {
    CLOUD_AI_DISABLED,
    HEALTH_INSIGHTS_DISABLED,
    API_KEY_MISSING,
    WIFI_REQUIRED,
    OPENAI_COMPATIBLE_BASE_URL_MISSING,
}

object HealthCloudInsightGenerationGate {
    fun evaluate(
        settings: SettingsPreferences,
        availableProviders: Set<CloudAiProvider>,
        networkType: NetworkType,
    ): HealthCloudInsightAvailability {
        if (!settings.cloudAiEnabled) {
            return unavailable(HealthCloudInsightUnavailableReason.CLOUD_AI_DISABLED)
        }
        if (!settings.cloudAiHealthInsightsEnabled) {
            return unavailable(HealthCloudInsightUnavailableReason.HEALTH_INSIGHTS_DISABLED)
        }
        if (settings.cloudAiProvider !in availableProviders) {
            return unavailable(HealthCloudInsightUnavailableReason.API_KEY_MISSING)
        }
        if (
            settings.cloudAiProvider == CloudAiProvider.OPENAI_COMPATIBLE &&
            settings.openAiCompatibleBaseUrl.isBlank()
        ) {
            return unavailable(HealthCloudInsightUnavailableReason.OPENAI_COMPATIBLE_BASE_URL_MISSING)
        }
        if (settings.cloudAiWifiOnly && networkType != NetworkType.WIFI) {
            return unavailable(HealthCloudInsightUnavailableReason.WIFI_REQUIRED)
        }
        return HealthCloudInsightAvailability(isAvailable = true)
    }

    private fun unavailable(reason: HealthCloudInsightUnavailableReason): HealthCloudInsightAvailability =
        HealthCloudInsightAvailability(isAvailable = false, reason = reason)
}
