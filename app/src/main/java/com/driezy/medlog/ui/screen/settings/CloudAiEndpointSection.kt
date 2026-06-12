package com.driezy.medlog.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.ai.CloudAiEndpointPreset
import com.driezy.medlog.ai.CloudAiEndpointProtocol
import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.OpenAiCompatibleCloudAuthMode
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EndpointConfigSection(
    uiState: SettingsUiState,
    onMimoBaseUrlSave: (String) -> Unit,
    onAnthropicBaseUrlSave: (String) -> Unit,
    onOpenAiBaseUrlSave: (String) -> Unit,
    onOpenAiAuthModeChange: (OpenAiCompatibleCloudAuthMode) -> Unit,
    onOpenAiProviderNameSave: (String) -> Unit,
    onEndpointPresetSelect: (CloudAiEndpointPreset) -> Unit,
) {
    var mimoBaseUrlDraft by rememberSaveable(uiState.cloudAiProvider, uiState.mimoCloudAiBaseUrl) {
        mutableStateOf(uiState.mimoCloudAiBaseUrl)
    }
    var anthropicBaseUrlDraft by rememberSaveable(uiState.cloudAiProvider, uiState.anthropicCloudAiBaseUrl) {
        mutableStateOf(uiState.anthropicCloudAiBaseUrl)
    }
    var baseUrlDraft by rememberSaveable(uiState.cloudAiProvider, uiState.openAiCompatibleBaseUrl) {
        mutableStateOf(uiState.openAiCompatibleBaseUrl)
    }
    var providerNameDraft by rememberSaveable(uiState.cloudAiProvider, uiState.openAiCompatibleProviderName) {
        mutableStateOf(uiState.openAiCompatibleProviderName)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(MedLogSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        ) {
            EndpointSectionHeader()

            when (uiState.cloudAiProvider) {
                CloudAiProvider.OPENAI_COMPATIBLE -> OpenAiCompatibleEndpointFields(
                    uiState = uiState,
                    baseUrlDraft = baseUrlDraft,
                    providerNameDraft = providerNameDraft,
                    onBaseUrlDraftChange = { baseUrlDraft = it },
                    onProviderNameDraftChange = { providerNameDraft = it },
                    onBaseUrlSave = { onOpenAiBaseUrlSave(baseUrlDraft) },
                    onProviderNameSave = { onOpenAiProviderNameSave(providerNameDraft) },
                    onAuthModeChange = onOpenAiAuthModeChange,
                    onPresetSelect = { preset ->
                        baseUrlDraft = preset.api
                        providerNameDraft = preset.name
                        onEndpointPresetSelect(preset)
                    },
                )

                CloudAiProvider.MIMO -> BaseUrlField(
                    value = mimoBaseUrlDraft,
                    onValueChange = { mimoBaseUrlDraft = it },
                    label = stringResource(R.string.settings_ai_endpoint_label),
                    supportingText = stringResource(R.string.settings_ai_mimo_endpoint_hint),
                    onSave = { onMimoBaseUrlSave(mimoBaseUrlDraft) },
                )

                CloudAiProvider.ANTHROPIC -> AnthropicEndpointFields(
                    uiState = uiState,
                    baseUrlDraft = anthropicBaseUrlDraft,
                    onBaseUrlDraftChange = { anthropicBaseUrlDraft = it },
                    onBaseUrlSave = { onAnthropicBaseUrlSave(anthropicBaseUrlDraft) },
                    onPresetSelect = { preset ->
                        anthropicBaseUrlDraft = preset.api
                        providerNameDraft = preset.name
                        onEndpointPresetSelect(preset)
                    },
                )

                else -> Unit
            }
        }
    }
}

@Composable
private fun EndpointSectionHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
    ) {
        MedLogIcon(
            MedLogIcons.Settings,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.settings_ai_endpoint_section_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OpenAiCompatibleEndpointFields(
    uiState: SettingsUiState,
    baseUrlDraft: String,
    providerNameDraft: String,
    onBaseUrlDraftChange: (String) -> Unit,
    onProviderNameDraftChange: (String) -> Unit,
    onBaseUrlSave: () -> Unit,
    onProviderNameSave: () -> Unit,
    onAuthModeChange: (OpenAiCompatibleCloudAuthMode) -> Unit,
    onPresetSelect: (CloudAiEndpointPreset) -> Unit,
) {
    EndpointPresetPicker(
        presets = uiState.cloudAiEndpointPresets,
        currentBaseUrl = uiState.openAiCompatibleBaseUrl,
        protocol = CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
        onSelect = onPresetSelect,
    )
    BaseUrlField(
        value = baseUrlDraft,
        onValueChange = onBaseUrlDraftChange,
        label = stringResource(R.string.settings_ai_base_url_label),
        supportingText = stringResource(R.string.settings_ai_base_url_hint),
        onSave = onBaseUrlSave,
    )
    BaseUrlField(
        value = providerNameDraft,
        onValueChange = onProviderNameDraftChange,
        label = stringResource(R.string.settings_ai_provider_name_label),
        supportingText = null,
        onSave = onProviderNameSave,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        OpenAiCompatibleCloudAuthMode.entries.forEachIndexed { index, mode ->
            ToggleButton(
                checked = uiState.openAiCompatibleAuthMode == mode,
                onCheckedChange = { onAuthModeChange(mode) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                },
            ) {
                Text(
                    text = when (mode) {
                        OpenAiCompatibleCloudAuthMode.BEARER -> stringResource(R.string.settings_ai_auth_bearer)
                        OpenAiCompatibleCloudAuthMode.API_KEY_HEADER -> stringResource(R.string.settings_ai_auth_api_key)
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun AnthropicEndpointFields(
    uiState: SettingsUiState,
    baseUrlDraft: String,
    onBaseUrlDraftChange: (String) -> Unit,
    onBaseUrlSave: () -> Unit,
    onPresetSelect: (CloudAiEndpointPreset) -> Unit,
) {
    EndpointPresetPicker(
        presets = uiState.cloudAiEndpointPresets,
        currentBaseUrl = uiState.anthropicCloudAiBaseUrl,
        protocol = CloudAiEndpointProtocol.ANTHROPIC,
        onSelect = onPresetSelect,
    )
    BaseUrlField(
        value = baseUrlDraft,
        onValueChange = onBaseUrlDraftChange,
        label = stringResource(R.string.settings_ai_endpoint_label),
        supportingText = stringResource(R.string.settings_ai_anthropic_endpoint_hint),
        onSave = onBaseUrlSave,
    )
}

@Composable
private fun BaseUrlField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String?,
    onSave: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        supportingText = supportingText?.let { text ->
            { Text(text) }
        },
        trailingIcon = {
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.common_save))
            }
        },
    )
}
