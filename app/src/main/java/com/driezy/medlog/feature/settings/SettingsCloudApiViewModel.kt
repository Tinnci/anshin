package com.driezy.medlog.feature.settings

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.driezy.medlog.capability.ai.AiApiKeyStore
import com.driezy.medlog.capability.ai.AiCloudConfigResolver
import com.driezy.medlog.capability.ai.AiProviderConfig
import com.driezy.medlog.capability.ai.CloudAiDiscoveredModel
import com.driezy.medlog.capability.ai.CloudAiEndpointPresetLoader
import com.driezy.medlog.capability.ai.CloudAiEndpointProtocol
import com.driezy.medlog.capability.ai.CloudAiModelDiscoveryClient
import com.driezy.medlog.capability.ai.OpenAiAuthMode
import com.driezy.medlog.data.model.AiUsageSummaryRow
import com.driezy.medlog.data.repository.AiCacheRepository
import com.driezy.medlog.data.repository.AiPreferenceState
import com.driezy.medlog.data.repository.AiPreferences
import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.OpenAiCompatibleCloudAuthMode
import com.driezy.medlog.data.repository.SettingsPreferences
import com.driezy.medlog.feature.medications.application.UnifiedImportPayload
import com.driezy.medlog.feature.medications.application.UnifiedImportPayloadCodec
import com.driezy.medlog.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class SettingsCloudApiViewModel @Inject constructor(
    private val preferences: AiPreferences,
    private val apiKeyStore: AiApiKeyStore,
    private val cacheRepository: AiCacheRepository,
    private val clock: Clock,
    @param:ApplicationContext context: Context,
) : BaseViewModel() {
    private val usageSummary = MutableStateFlow(emptyList<AiUsageSummaryRow>())
    private val discovery = MutableStateFlow(CloudModelDiscoveryState())
    private val discoveryClient = CloudAiModelDiscoveryClient()
    private val endpointPresets = CloudAiEndpointPresetLoader.load(context)

    val uiState = combine(
        preferences.ai,
        apiKeyStore.availableProviders,
        usageSummary,
        discovery,
    ) { ai, availableProviders, usage, modelDiscovery ->
        val capabilities = AiCloudConfigResolver.resolveCapabilities(ai.toLegacySettings())
        SettingsUiState(
            ocrModelType = ai.ocrModelType,
            cloudAiEnabled = ai.cloudAiEnabled,
            cloudAiImageAnalysisEnabled = ai.cloudAiImageAnalysisEnabled,
            cloudAiHealthInsightsEnabled = ai.cloudAiHealthInsightsEnabled,
            cloudAiWifiOnly = ai.cloudAiWifiOnly,
            cloudAiProvider = ai.cloudAiProvider,
            cloudAiModel = ai.cloudAiModel,
            mimoCloudAiBaseUrl = ai.mimoCloudAiBaseUrl,
            anthropicCloudAiBaseUrl = ai.anthropicCloudAiBaseUrl,
            openAiCompatibleBaseUrl = ai.openAiCompatibleBaseUrl,
            openAiCompatibleAuthMode = ai.openAiCompatibleAuthMode,
            openAiCompatibleProviderName = ai.openAiCompatibleProviderName,
            cloudAiAvailableProviders = availableProviders,
            cloudAiProviderHasApiKey = ai.cloudAiProvider in availableProviders,
            cloudAiSupportsImageInput = capabilities.supportsImageInput,
            cloudAiSupportsText = capabilities.supportsText,
            cloudAiSupportsJsonInstruction = capabilities.supportsJsonInstruction,
            cloudAiModelDiscoveryInProgress = modelDiscovery.inProgress,
            cloudAiModelDiscoveryConnected = modelDiscovery.connected,
            cloudAiModelDiscoveryError = modelDiscovery.error,
            cloudAiDiscoveredModels = modelDiscovery.models,
            cloudAiEndpointPresets = endpointPresets,
            aiUsageSummary = usage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        refreshUsage()
    }

    fun onAction(action: SettingsUiAction) {
        when (action) {
            is SettingsUiAction.SetCloudAi -> updateSettings(action)
            is SettingsUiAction.SaveCloudApiKey -> saveApiKey(action.value)
            is SettingsUiAction.ImportCloudApiKey -> importApiKey(action.raw)
            SettingsUiAction.ClearCloudApiKey -> clearApiKey()
            SettingsUiAction.RefreshCloudModels -> refreshModels()
            is SettingsUiAction.ApplyCloudEndpointPreset -> applyPreset(action)
            SettingsUiAction.RefreshAiUsage -> refreshUsage()
            else -> Unit
        }
    }

    private fun updateSettings(action: SettingsUiAction.SetCloudAi) {
        safeLaunch {
            preferences.updateCloudAiSettings(
                enabled = action.enabled,
                imageAnalysisEnabled = action.imageAnalysisEnabled,
                healthInsightsEnabled = action.healthInsightsEnabled,
                wifiOnly = action.wifiOnly,
                provider = action.provider,
                model = action.model,
                mimoBaseUrl = action.mimoBaseUrl,
                anthropicBaseUrl = action.anthropicBaseUrl,
                openAiCompatibleBaseUrl = action.openAiCompatibleBaseUrl,
                openAiCompatibleAuthMode = action.openAiCompatibleAuthMode,
                openAiCompatibleProviderName = action.openAiCompatibleProviderName,
            )
            if (action.provider != null) discovery.value = CloudModelDiscoveryState()
        }
    }

    private fun saveApiKey(value: String) {
        safeLaunch {
            val ai = preferences.ai.first()
            apiKeyStore.setApiKey(ai.cloudAiProvider, value.trim())
            refreshModelsNow()
        }
    }

    private fun importApiKey(raw: String) {
        safeLaunch {
            val payload = UnifiedImportPayloadCodec.decode(raw) as? UnifiedImportPayload.CloudAiApiKey
                ?: return@safeLaunch
            val key = payload.key
            apiKeyStore.setApiKey(key.provider, key.apiKey)
            preferences.updateCloudAiSettings(
                enabled = true,
                provider = key.provider,
                model = key.model,
                mimoBaseUrl = key.baseUrl.takeIf { key.provider == CloudAiProvider.MIMO },
                anthropicBaseUrl = key.baseUrl.takeIf { key.provider == CloudAiProvider.ANTHROPIC },
                openAiCompatibleBaseUrl = key.baseUrl.takeIf { key.provider == CloudAiProvider.OPENAI_COMPATIBLE },
                openAiCompatibleAuthMode = key.openAiAuthMode.takeIf {
                    key.provider == CloudAiProvider.OPENAI_COMPATIBLE
                },
                openAiCompatibleProviderName = key.providerName.takeIf {
                    key.provider == CloudAiProvider.OPENAI_COMPATIBLE
                },
            )
            refreshModelsNow()
        }
    }

    private fun clearApiKey() {
        safeLaunch { apiKeyStore.clearApiKey(preferences.ai.first().cloudAiProvider) }
    }

    private fun refreshModels() {
        safeLaunch { refreshModelsNow() }
    }

    private suspend fun refreshModelsNow() {
        discovery.value = discovery.value.copy(inProgress = true, connected = null, error = null)
        val ai = preferences.ai.first()
        val config = ai.toDiscoveryConfig(apiKeyStore.getApiKey(ai.cloudAiProvider))
        if (config == null) {
            discovery.value = CloudModelDiscoveryState(
                connected = false,
                error = "API key or OpenAI-compatible Base URL is missing.",
            )
            return
        }
        val result = discoveryClient.fetch(config)
        val selected = result.selectBestModel(requireImageInput = true)
            ?: result.selectBestModel(requireImageInput = false)
        if (result.isConnected && selected != null) {
            preferences.updateCloudAiSettings(model = selected.id)
        }
        discovery.value = CloudModelDiscoveryState(
            connected = result.isConnected,
            error = result.errorMessage,
            models = result.models,
        )
    }

    private fun applyPreset(action: SettingsUiAction.ApplyCloudEndpointPreset) {
        safeLaunch {
            when (action.preset.protocol) {
                CloudAiEndpointProtocol.ANTHROPIC -> preferences.updateCloudAiSettings(
                    provider = CloudAiProvider.ANTHROPIC,
                    anthropicBaseUrl = action.preset.api,
                    openAiCompatibleProviderName = action.preset.name,
                )
                CloudAiEndpointProtocol.OPENAI_COMPATIBLE -> preferences.updateCloudAiSettings(
                    provider = CloudAiProvider.OPENAI_COMPATIBLE,
                    openAiCompatibleBaseUrl = action.preset.api,
                    openAiCompatibleProviderName = action.preset.name,
                    openAiCompatibleAuthMode = OpenAiCompatibleCloudAuthMode.BEARER,
                )
            }
        }
    }

    private fun refreshUsage() {
        safeLaunch {
            val sevenDays = 7L * 24 * 60 * 60 * 1_000
            usageSummary.value = cacheRepository.usageSummary(clock.millis() - sevenDays)
        }
    }
}

private data class CloudModelDiscoveryState(
    val inProgress: Boolean = false,
    val connected: Boolean? = null,
    val error: String? = null,
    val models: List<CloudAiDiscoveredModel> = emptyList(),
)

private fun AiPreferenceState.toLegacySettings() = SettingsPreferences(
    ocrModelType = ocrModelType,
    cloudAiEnabled = cloudAiEnabled,
    cloudAiImageAnalysisEnabled = cloudAiImageAnalysisEnabled,
    cloudAiHealthInsightsEnabled = cloudAiHealthInsightsEnabled,
    cloudAiWifiOnly = cloudAiWifiOnly,
    cloudAiProvider = cloudAiProvider,
    cloudAiModel = cloudAiModel,
    mimoCloudAiModel = cloudAiModel.takeIf { cloudAiProvider == CloudAiProvider.MIMO }
        ?: CloudAiProvider.MIMO.defaultModel,
    mimoCloudAiBaseUrl = mimoCloudAiBaseUrl,
    geminiCloudAiModel = cloudAiModel.takeIf { cloudAiProvider == CloudAiProvider.GEMINI }
        ?: CloudAiProvider.GEMINI.defaultModel,
    anthropicCloudAiModel = cloudAiModel.takeIf { cloudAiProvider == CloudAiProvider.ANTHROPIC }
        ?: CloudAiProvider.ANTHROPIC.defaultModel,
    anthropicCloudAiBaseUrl = anthropicCloudAiBaseUrl,
    openAiCompatibleCloudAiModel = cloudAiModel.takeIf { cloudAiProvider == CloudAiProvider.OPENAI_COMPATIBLE }
        ?: CloudAiProvider.OPENAI_COMPATIBLE.defaultModel,
    openAiCompatibleBaseUrl = openAiCompatibleBaseUrl,
    openAiCompatibleAuthMode = openAiCompatibleAuthMode,
    openAiCompatibleProviderName = openAiCompatibleProviderName,
)

private fun AiPreferenceState.toDiscoveryConfig(apiKey: String?): AiProviderConfig? = when (cloudAiProvider) {
    CloudAiProvider.MIMO -> apiKey?.let {
        AiProviderConfig.Mimo(
            apiKey = it,
            model = cloudAiModel,
            baseUrl = mimoCloudAiBaseUrl.ifBlank { AiCloudConfigResolver.mimoBaseUrlFor(it) },
        )
    }
    CloudAiProvider.GEMINI -> apiKey?.let { AiProviderConfig.Gemini(it, cloudAiModel) }
    CloudAiProvider.ANTHROPIC -> apiKey?.let {
        AiProviderConfig.Anthropic(
            apiKey = it,
            model = cloudAiModel,
            baseUrl = anthropicCloudAiBaseUrl.ifBlank { "https://api.anthropic.com" },
        )
    }
    CloudAiProvider.OPENAI_COMPATIBLE -> {
        val baseUrl = openAiCompatibleBaseUrl.takeIf(String::isNotBlank) ?: return null
        AiProviderConfig.OpenAiCompatible(
            baseUrl = baseUrl,
            model = cloudAiModel,
            apiKey = apiKey,
            authMode = when (openAiCompatibleAuthMode) {
                OpenAiCompatibleCloudAuthMode.API_KEY_HEADER -> OpenAiAuthMode.API_KEY_HEADER
                OpenAiCompatibleCloudAuthMode.BEARER -> OpenAiAuthMode.BEARER
            },
            providerName = openAiCompatibleProviderName,
        )
    }
}
