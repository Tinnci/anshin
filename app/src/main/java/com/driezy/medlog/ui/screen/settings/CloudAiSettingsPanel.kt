package com.driezy.medlog.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.driezy.medlog.R
import com.driezy.medlog.ai.CloudAiEndpointPreset
import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.OpenAiCompatibleCloudAuthMode
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

private val CloudAiProvider.needsCustomEndpoint: Boolean
    get() = this != CloudAiProvider.GEMINI

@Composable
internal fun CloudAiSettingsPanel(
    uiState: SettingsUiState,
    onProviderChange: (CloudAiProvider) -> Unit,
    onModelSave: (String) -> Unit,
    onMimoBaseUrlSave: (String) -> Unit,
    onAnthropicBaseUrlSave: (String) -> Unit,
    onOpenAiBaseUrlSave: (String) -> Unit,
    onOpenAiAuthModeChange: (OpenAiCompatibleCloudAuthMode) -> Unit,
    onOpenAiProviderNameSave: (String) -> Unit,
    onEndpointPresetSelect: (CloudAiEndpointPreset) -> Unit,
    onRefreshModels: () -> Unit,
    onImageAnalysisChange: (Boolean) -> Unit,
    onHealthInsightsChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onApiKeySave: (String) -> Unit,
    onApiKeyImport: (String) -> Unit,
    onApiKeyScan: () -> Unit,
    onApiKeyClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MedLogSpacing.Large)
            .padding(bottom = MedLogSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
    ) {
        CloudAiProviderSection(
            uiState = uiState,
            onProviderChange = onProviderChange,
        )

        ApiKeyManagementSection(
            uiState = uiState,
            onApiKeySave = onApiKeySave,
            onApiKeyImport = onApiKeyImport,
            onApiKeyScan = onApiKeyScan,
            onApiKeyClear = onApiKeyClear,
        )

        AnimatedVisibility(
            visible = uiState.cloudAiProvider.needsCustomEndpoint,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            EndpointConfigSection(
                uiState = uiState,
                onMimoBaseUrlSave = onMimoBaseUrlSave,
                onAnthropicBaseUrlSave = onAnthropicBaseUrlSave,
                onOpenAiBaseUrlSave = onOpenAiBaseUrlSave,
                onOpenAiAuthModeChange = onOpenAiAuthModeChange,
                onOpenAiProviderNameSave = onOpenAiProviderNameSave,
                onEndpointPresetSelect = onEndpointPresetSelect,
            )
        }

        CloudAiModelSection(
            uiState = uiState,
            onModelSave = onModelSave,
            onRefreshModels = onRefreshModels,
        )

        AdkAgentSection()

        CloudAiFeatureToggles(
            uiState = uiState,
            onImageAnalysisChange = onImageAnalysisChange,
            onHealthInsightsChange = onHealthInsightsChange,
            onWifiOnlyChange = onWifiOnlyChange,
        )

        CloudAiUsageSummaryCard(summary = uiState.aiUsageSummary)
    }
}

@Composable
private fun CloudAiFeatureToggles(
    uiState: SettingsUiState,
    onImageAnalysisChange: (Boolean) -> Unit,
    onHealthInsightsChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
) {
    val imageAnalysisSupported = uiState.cloudAiSupportsImageInput
    SettingsSwitchRow(
        title = stringResource(R.string.settings_ai_image_title),
        subtitle = stringResource(
            if (imageAnalysisSupported) {
                R.string.settings_ai_image_subtitle
            } else {
                R.string.settings_ai_image_unsupported_subtitle
            },
        ),
        checked = uiState.cloudAiImageAnalysisEnabled && imageAnalysisSupported,
        onCheckedChange = { enabled ->
            if (imageAnalysisSupported) {
                onImageAnalysisChange(enabled)
            }
        },
        icon = MedLogIcons.DocumentScanner,
        enabled = imageAnalysisSupported,
    )
    HorizontalDivider()
    SettingsSwitchRow(
        title = stringResource(R.string.settings_ai_insights_title),
        subtitle = stringResource(R.string.settings_ai_insights_subtitle),
        checked = uiState.cloudAiHealthInsightsEnabled,
        onCheckedChange = onHealthInsightsChange,
        icon = MedLogIcons.AutoAwesome,
    )
    HorizontalDivider()
    SettingsSwitchRow(
        title = stringResource(R.string.settings_ai_wifi_only_title),
        subtitle = stringResource(R.string.settings_ai_wifi_only_subtitle),
        checked = uiState.cloudAiWifiOnly,
        onCheckedChange = onWifiOnlyChange,
        icon = MedLogIcons.CloudUpload,
    )
}
