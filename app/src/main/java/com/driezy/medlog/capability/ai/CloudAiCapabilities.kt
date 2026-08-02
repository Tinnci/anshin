package com.driezy.medlog.capability.ai

import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.SettingsPreferences

enum class CloudAiAuthMode {
    API_KEY_HEADER,
    BEARER,
    GEMINI_QUERY_KEY,
    ANTHROPIC_X_API_KEY,
}

data class CloudAiProviderCapabilities(
    val supportsText: Boolean,
    val supportsImageInput: Boolean,
    val supportsJsonInstruction: Boolean,
    val requiresApiKey: Boolean,
    val defaultAuthMode: CloudAiAuthMode,
)

val CloudAiProvider.capabilities: CloudAiProviderCapabilities
    get() = when (this) {
        CloudAiProvider.MIMO -> CloudAiProviderCapabilities(
            supportsText = true,
            supportsImageInput = true,
            supportsJsonInstruction = true,
            requiresApiKey = true,
            defaultAuthMode = CloudAiAuthMode.API_KEY_HEADER,
        )
        CloudAiProvider.GEMINI -> CloudAiProviderCapabilities(
            supportsText = true,
            supportsImageInput = true,
            supportsJsonInstruction = true,
            requiresApiKey = true,
            defaultAuthMode = CloudAiAuthMode.GEMINI_QUERY_KEY,
        )
        CloudAiProvider.ANTHROPIC -> CloudAiProviderCapabilities(
            supportsText = true,
            supportsImageInput = true,
            supportsJsonInstruction = true,
            requiresApiKey = true,
            defaultAuthMode = CloudAiAuthMode.ANTHROPIC_X_API_KEY,
        )
        CloudAiProvider.OPENAI_COMPATIBLE -> CloudAiProviderCapabilities(
            supportsText = true,
            supportsImageInput = true,
            supportsJsonInstruction = true,
            requiresApiKey = true,
            defaultAuthMode = CloudAiAuthMode.BEARER,
        )
    }

fun CloudAiProviderCapabilities.withModel(settings: SettingsPreferences): CloudAiProviderCapabilities {
    val normalized = settings.activeCloudAiModel().trim().lowercase()
    val imageCapable = when (settings.cloudAiProvider) {
        CloudAiProvider.MIMO -> normalized == "mimo-v2.5" || normalized == "mimo-v2-omni"
        CloudAiProvider.OPENAI_COMPATIBLE -> normalized.isBlank() ||
            normalized.startsWith("gpt-4") ||
            normalized.startsWith("gpt-4o") ||
            normalized.startsWith("o3") ||
            normalized.startsWith("o4") ||
            "vision" in normalized ||
            "vl" in normalized ||
            "omni" in normalized
        else -> supportsImageInput
    }
    return copy(supportsImageInput = imageCapable)
}
