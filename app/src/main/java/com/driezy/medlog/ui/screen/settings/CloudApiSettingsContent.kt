package com.driezy.medlog.ui.screen.settings

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
internal fun CloudApiSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
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
            onCheckedChange = { viewModel.setCloudAiSettings(enabled = it) },
            icon = MedLogIcons.CloudUpload,
        )
        AnimatedVisibility(
            visible = uiState.cloudAiEnabled,
            enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            CloudAiSettingsPanel(
                uiState = uiState,
                onProviderChange = { viewModel.setCloudAiSettings(provider = it) },
                onModelSave = { viewModel.setCloudAiSettings(model = it) },
                onMimoBaseUrlSave = { viewModel.setCloudAiSettings(mimoBaseUrl = it) },
                onAnthropicBaseUrlSave = { viewModel.setCloudAiSettings(anthropicBaseUrl = it) },
                onOpenAiBaseUrlSave = { viewModel.setCloudAiSettings(openAiCompatibleBaseUrl = it) },
                onOpenAiAuthModeChange = { viewModel.setCloudAiSettings(openAiCompatibleAuthMode = it) },
                onOpenAiProviderNameSave = { viewModel.setCloudAiSettings(openAiCompatibleProviderName = it) },
                onEndpointPresetSelect = viewModel::applyCloudAiEndpointPreset,
                onRefreshModels = viewModel::refreshCloudAiModels,
                onImageAnalysisChange = { viewModel.setCloudAiSettings(imageAnalysisEnabled = it) },
                onHealthInsightsChange = { viewModel.setCloudAiSettings(healthInsightsEnabled = it) },
                onWifiOnlyChange = { viewModel.setCloudAiSettings(wifiOnly = it) },
                onApiKeySave = viewModel::setCurrentCloudAiApiKey,
                onApiKeyImport = viewModel::importCloudAiApiKey,
                onApiKeyScan = { showApiKeyScanner = true },
                onApiKeyClear = viewModel::clearCurrentCloudAiApiKey,
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
                    viewModel.importCloudAiApiKey(raw)
                },
                onBack = { showApiKeyScanner = false },
            )
        }
    }
}
