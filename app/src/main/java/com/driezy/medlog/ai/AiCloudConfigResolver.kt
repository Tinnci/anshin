package com.driezy.medlog.ai

import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.OpenAiCompatibleCloudAuthMode
import com.driezy.medlog.data.repository.SettingsPreferences

interface AiApiKeyAvailability {
    fun hasApiKey(provider: CloudAiProvider): Boolean
}

data class AiCloudModelIdentity(val provider: String, val model: String)

data class AiFeatureAvailability(
    val isAvailable: Boolean,
    val identity: AiCloudModelIdentity? = null,
    val reason: AiFeatureUnavailableReason? = null,
)

enum class AiFeatureUnavailableReason {
    CLOUD_AI_DISABLED,
    FEATURE_DISABLED,
    API_KEY_MISSING,
    OPENAI_COMPATIBLE_BASE_URL_MISSING,
    IMAGE_INPUT_UNSUPPORTED,
}

object AiCloudConfigResolver {
    fun resolveImageAnalysis(settings: SettingsPreferences, keys: AiApiKeyAvailability): AiFeatureAvailability =
        resolve(
            settings = settings,
            featureEnabled = settings.cloudAiImageAnalysisEnabled,
            requireImageInput = true,
            keys = keys,
        )

    fun resolveHealthInsights(settings: SettingsPreferences, keys: AiApiKeyAvailability): AiFeatureAvailability =
        resolve(
            settings = settings,
            featureEnabled = settings.cloudAiHealthInsightsEnabled,
            requireImageInput = false,
            keys = keys,
        )

    fun resolveCapabilities(settings: SettingsPreferences): CloudAiProviderCapabilities =
        settings.cloudAiProvider.capabilities.withModel(settings)

    fun toProviderConfig(settings: SettingsPreferences, apiKey: String): AiProviderConfig =
        when (settings.cloudAiProvider) {
            CloudAiProvider.MIMO ->
                AiProviderConfig.Mimo(
                    apiKey = apiKey,
                    model = settings.activeCloudAiModel(),
                    baseUrl = settings.mimoCloudAiBaseUrl.ifBlank { mimoBaseUrlFor(apiKey) },
                )
            CloudAiProvider.GEMINI ->
                AiProviderConfig.Gemini(
                    apiKey = apiKey,
                    model = settings.activeCloudAiModel(),
                )
            CloudAiProvider.ANTHROPIC ->
                AiProviderConfig.Anthropic(
                    apiKey = apiKey,
                    model = settings.activeCloudAiModel(),
                    baseUrl = settings.anthropicCloudAiBaseUrl.ifBlank { "https://api.anthropic.com" },
                )
            CloudAiProvider.OPENAI_COMPATIBLE ->
                AiProviderConfig.OpenAiCompatible(
                    baseUrl = settings.openAiCompatibleBaseUrl,
                    model = settings.activeCloudAiModel(),
                    apiKey = apiKey,
                    authMode = when (settings.openAiCompatibleAuthMode) {
                        OpenAiCompatibleCloudAuthMode.API_KEY_HEADER -> OpenAiAuthMode.API_KEY_HEADER
                        OpenAiCompatibleCloudAuthMode.BEARER -> OpenAiAuthMode.BEARER
                    },
                    providerName = settings.openAiCompatibleProviderName.ifBlank {
                        CloudAiProvider.OPENAI_COMPATIBLE.providerName
                    },
                )
        }

    fun mimoBaseUrlFor(apiKey: String): String = if (apiKey.trim().startsWith("tp-")) {
        "https://token-plan-sgp.xiaomimimo.com/v1"
    } else {
        "https://api.xiaomimimo.com/v1"
    }

    private fun resolve(
        settings: SettingsPreferences,
        featureEnabled: Boolean,
        requireImageInput: Boolean,
        keys: AiApiKeyAvailability,
    ): AiFeatureAvailability {
        if (!settings.cloudAiEnabled) {
            return AiFeatureAvailability(
                isAvailable = false,
                reason = AiFeatureUnavailableReason.CLOUD_AI_DISABLED,
            )
        }
        if (!featureEnabled) {
            return AiFeatureAvailability(
                isAvailable = false,
                reason = AiFeatureUnavailableReason.FEATURE_DISABLED,
            )
        }
        if (
            settings.cloudAiProvider == CloudAiProvider.OPENAI_COMPATIBLE &&
            settings.openAiCompatibleBaseUrl.isBlank()
        ) {
            return AiFeatureAvailability(
                isAvailable = false,
                reason = AiFeatureUnavailableReason.OPENAI_COMPATIBLE_BASE_URL_MISSING,
            )
        }
        if (requireImageInput && !resolveCapabilities(settings).supportsImageInput) {
            return AiFeatureAvailability(
                isAvailable = false,
                reason = AiFeatureUnavailableReason.IMAGE_INPUT_UNSUPPORTED,
            )
        }
        if (!keys.hasApiKey(settings.cloudAiProvider)) {
            return AiFeatureAvailability(
                isAvailable = false,
                reason = AiFeatureUnavailableReason.API_KEY_MISSING,
            )
        }
        return AiFeatureAvailability(
            isAvailable = true,
            identity = AiCloudModelIdentity(
                provider = settings.cloudAiProvider.providerName,
                model = settings.activeCloudAiModel(),
            ),
        )
    }
}
