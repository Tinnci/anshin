package com.driezy.medlog.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.qr.QrScannerPage
import com.driezy.medlog.ui.theme.MedLogSpacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CloudApiSettingsContent(uiState: SettingsUiState, onAction: (SettingsUiAction) -> Unit) {
    val motionScheme = MaterialTheme.motionScheme
    var showApiKeyScanner by rememberSaveable { mutableStateOf(false) }

    SettingsCard(
        title = stringResource(R.string.settings_ai_config_title),
        subtitle = stringResource(R.string.settings_ai_config_desc),
        icon = MedLogIcons.CloudUpload,
    ) {
        CloudAiStatusSummary(
            uiState = uiState,
            modifier = Modifier
                .padding(horizontal = MedLogSpacing.Large)
                .padding(bottom = MedLogSpacing.Small),
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_ai_enable_title),
            subtitle = stringResource(R.string.settings_ai_enable_subtitle),
            checked = uiState.cloudAiEnabled,
            onCheckedChange = { onAction(SettingsUiAction.SetCloudAi(enabled = it)) },
            icon = MedLogIcons.CloudUpload,
        )
        AnimatedVisibility(
            visible = uiState.cloudAiEnabled,
            enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            CloudAiSettingsPanel(
                uiState = uiState,
                onProviderChange = { onAction(SettingsUiAction.SetCloudAi(provider = it)) },
                onModelSave = { onAction(SettingsUiAction.SetCloudAi(model = it)) },
                onMimoBaseUrlSave = { onAction(SettingsUiAction.SetCloudAi(mimoBaseUrl = it)) },
                onAnthropicBaseUrlSave = { onAction(SettingsUiAction.SetCloudAi(anthropicBaseUrl = it)) },
                onOpenAiBaseUrlSave = { onAction(SettingsUiAction.SetCloudAi(openAiCompatibleBaseUrl = it)) },
                onOpenAiAuthModeChange = {
                    onAction(SettingsUiAction.SetCloudAi(openAiCompatibleAuthMode = it))
                },
                onOpenAiProviderNameSave = {
                    onAction(SettingsUiAction.SetCloudAi(openAiCompatibleProviderName = it))
                },
                onEndpointPresetSelect = { onAction(SettingsUiAction.ApplyCloudEndpointPreset(it)) },
                onRefreshModels = { onAction(SettingsUiAction.RefreshCloudModels) },
                onImageAnalysisChange = { onAction(SettingsUiAction.SetCloudAi(imageAnalysisEnabled = it)) },
                onHealthInsightsChange = { onAction(SettingsUiAction.SetCloudAi(healthInsightsEnabled = it)) },
                onWifiOnlyChange = { onAction(SettingsUiAction.SetCloudAi(wifiOnly = it)) },
                onApiKeySave = { onAction(SettingsUiAction.SaveCloudApiKey(it)) },
                onApiKeyImport = { onAction(SettingsUiAction.ImportCloudApiKey(it)) },
                onApiKeyScan = { showApiKeyScanner = true },
                onApiKeyClear = { onAction(SettingsUiAction.ClearCloudApiKey) },
            )
        }
    }

    if (showApiKeyScanner) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showApiKeyScanner = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
            ),
        ) {
            QrScannerPage(
                onResult = { raw ->
                    showApiKeyScanner = false
                    onAction(SettingsUiAction.ImportCloudApiKey(raw))
                },
                onBack = { showApiKeyScanner = false },
            )
        }
    }
}
