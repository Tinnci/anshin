package com.driezy.medlog.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.driezy.medlog.R
import com.driezy.medlog.capability.ai.CloudAiEndpointPreset
import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.OpenAiCompatibleCloudAuthMode
import com.driezy.medlog.ui.icons.MedLogIcon
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
    val motionScheme = MaterialTheme.motionScheme
    var showProviderDetails by rememberSaveable(uiState.cloudAiProvider) {
        mutableStateOf(uiState.cloudAiProvider == CloudAiProvider.OPENAI_COMPATIBLE)
    }
    val showConfiguredControls = uiState.cloudAiProviderHasApiKey

    LaunchedEffect(uiState.cloudAiModelDiscoveryConnected, showConfiguredControls) {
        if (showConfiguredControls && uiState.cloudAiModelDiscoveryConnected == false) {
            showProviderDetails = true
        }
    }

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

        AnimatedVisibility(
            visible = uiState.cloudAiProvider.needsCustomEndpoint,
            label = "cloud_ai_settings_animated",
            enter = expandVertically(motionScheme.defaultSpatialSpec()) +
                fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) +
                fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            ProviderDetailsDisclosureRow(
                expanded = showProviderDetails,
                onClick = { showProviderDetails = !showProviderDetails },
            )
        }

        AnimatedVisibility(
            visible = showProviderDetails && uiState.cloudAiProvider.needsCustomEndpoint,
            label = "cloud_ai_settings_animated",
            enter = expandVertically(motionScheme.defaultSpatialSpec()) +
                fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) +
                fadeOut(motionScheme.fastEffectsSpec()),
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

        ApiKeyManagementSection(
            uiState = uiState,
            onApiKeySave = onApiKeySave,
            onApiKeyImport = onApiKeyImport,
            onApiKeyScan = onApiKeyScan,
            onApiKeyClear = onApiKeyClear,
        )

        AnimatedVisibility(
            visible = showConfiguredControls,
            label = "cloud_ai_settings_animated",
            enter = expandVertically(motionScheme.defaultSpatialSpec()) +
                fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) +
                fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            CloudAiModelSection(
                uiState = uiState,
                onModelSave = onModelSave,
                onRefreshModels = onRefreshModels,
            )
        }

        AnimatedVisibility(
            visible = showConfiguredControls,
            label = "cloud_ai_settings_animated",
            enter = expandVertically(motionScheme.defaultSpatialSpec()) +
                fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) +
                fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            CloudAiFeatureToggles(
                uiState = uiState,
                onImageAnalysisChange = onImageAnalysisChange,
                onHealthInsightsChange = onHealthInsightsChange,
                onWifiOnlyChange = onWifiOnlyChange,
            )
        }

        AnimatedVisibility(
            visible = showConfiguredControls,
            label = "cloud_ai_settings_animated",
            enter = expandVertically(motionScheme.defaultSpatialSpec()) +
                fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) +
                fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            CloudAiUsageSummaryCard(summary = uiState.aiUsageSummary)
        }
    }
}

@Composable
private fun ProviderDetailsDisclosureRow(expanded: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_ai_provider_details_title)) },
        supportingContent = { Text(stringResource(R.string.settings_ai_provider_details_desc)) },
        leadingContent = {
            MedLogIcon(
                MedLogIcons.Tune,
                contentDescription = null,
            )
        },
        trailingContent = {
            MedLogIcon(
                if (expanded) MedLogIcons.ExpandLess else MedLogIcons.ExpandMore,
                contentDescription = null,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
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
