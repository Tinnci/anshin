package com.driezy.medlog.ui.screen.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driezy.medlog.BuildConfig
import com.driezy.medlog.R
import com.driezy.medlog.data.repository.AppTextScale
import com.driezy.medlog.data.repository.FontMode
import com.driezy.medlog.data.repository.OcrModelType
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.data.repository.UiDensityScale
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.qr.QrScannerPage
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.theme.ThemePalette
import com.driezy.medlog.ui.utils.OemWidgetHelper
import com.driezy.medlog.widget.MedLogWidgetReceiver
import com.driezy.medlog.widget.NextDoseWidgetReceiver
import com.driezy.medlog.widget.StreakWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsIntelligenceContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val motionScheme = MaterialTheme.motionScheme
    var showApiKeyScanner by rememberSaveable { mutableStateOf(false) }
    SettingsCard(
    title = stringResource(R.string.settings_group_intelligence),
    subtitle = stringResource(R.string.settings_group_intelligence_desc),
    icon = MedLogIcons.Memory,
    ) {
    SettingsSectionDivider(
        title = stringResource(R.string.settings_ocr_model_card_title),
        icon = MedLogIcons.DocumentScanner,
        modifier = Modifier.padding(top = 0.dp),
    )
    Text(
        text = stringResource(R.string.settings_ocr_model_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = MedLogSpacing.Large)
            .padding(bottom = MedLogSpacing.Medium),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MedLogSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium)
    ) {
        OcrModelOptionCard(
            title = stringResource(R.string.settings_ocr_model_light_title),
            tag = stringResource(R.string.settings_ocr_model_light_tag),
            description = stringResource(R.string.settings_ocr_model_light_desc),
            specs = listOf(
                MedLogIcons.Storage to stringResource(R.string.settings_ocr_model_light_size),
                MedLogIcons.Speed to stringResource(R.string.settings_ocr_model_light_latency),
                MedLogIcons.CheckCircle to stringResource(R.string.settings_ocr_model_light_accuracy),
            ),
            selected = uiState.ocrModelType == OcrModelType.LIGHT_SVTR,
            tagContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            tagContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onSelect = { viewModel.setOcrModelType(OcrModelType.LIGHT_SVTR) },
        )

        OcrModelOptionCard(
            title = stringResource(R.string.settings_ocr_model_fastvit_title),
            tag = stringResource(R.string.settings_ocr_model_fastvit_tag),
            description = stringResource(R.string.settings_ocr_model_fastvit_desc),
            specs = listOf(
                MedLogIcons.Storage to stringResource(R.string.settings_ocr_model_fastvit_size),
                MedLogIcons.Speed to stringResource(R.string.settings_ocr_model_fastvit_latency),
                MedLogIcons.CheckCircle to stringResource(R.string.settings_ocr_model_fastvit_accuracy),
            ),
            selected = uiState.ocrModelType == OcrModelType.FASTVIT_T8,
            tagContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            tagContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onSelect = { viewModel.setOcrModelType(OcrModelType.FASTVIT_T8) },
        )
    }

    SettingsSectionDivider(
        title = stringResource(R.string.settings_ai_section_title),
        icon = MedLogIcons.AutoAwesome,
    )
    Text(
        text = stringResource(R.string.settings_ai_section_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = MedLogSpacing.Large)
            .padding(bottom = MedLogSpacing.Tiny),
    )
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
