package com.driezy.medlog.ui.screen.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.ai.CloudAiEndpointPreset
import com.driezy.medlog.ai.CloudAiEndpointProtocol
import com.driezy.medlog.data.repository.AiUsageSummaryRow
import com.driezy.medlog.data.repository.AppTextScale
import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.FontMode
import com.driezy.medlog.data.repository.OpenAiCompatibleCloudAuthMode
import com.driezy.medlog.data.repository.UiDensityScale
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.theme.ThemePalette


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
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
    onApiKeyClear: () -> Unit,
) {
    var modelDraft by rememberSaveable(uiState.cloudAiProvider) { mutableStateOf(uiState.cloudAiModel) }
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
    var apiKeyDraft by rememberSaveable(uiState.cloudAiProvider) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MedLogSpacing.Large)
            .padding(bottom = MedLogSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(MedLogSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
            ) {
                Text(
                    text = stringResource(R.string.settings_ai_provider_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                    verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
                ) {
                    CloudAiProvider.entries.forEach { provider ->
                        FilterChip(
                            selected = uiState.cloudAiProvider == provider,
                            onClick = { onProviderChange(provider) },
                            label = { Text(provider.providerName) },
                            leadingIcon = if (provider in uiState.cloudAiAvailableProviders) {
                                {
                                    MedLogIcon(
                                        MedLogIcons.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = modelDraft,
                    onValueChange = { modelDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_ai_model_label)) },
                    supportingText = {
                        Text(stringResource(R.string.settings_ai_model_hint, uiState.cloudAiProvider.defaultModel))
                    },
                    trailingIcon = {
                        TextButton(onClick = { onModelSave(modelDraft) }) {
                            Text(stringResource(R.string.common_save))
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val connected = uiState.cloudAiModelDiscoveryConnected
                    Text(
                        text = when {
                            uiState.cloudAiModelDiscoveryInProgress ->
                                stringResource(R.string.settings_ai_models_fetching)
                            connected == true && uiState.cloudAiDiscoveredModels.isNotEmpty() ->
                                pluralStringResource(
                                    R.plurals.settings_ai_models_connected,
                                    uiState.cloudAiDiscoveredModels.size,
                                    uiState.cloudAiDiscoveredModels.size,
                                )
                            connected == true -> stringResource(R.string.settings_ai_models_empty)
                            connected == false -> stringResource(
                                R.string.settings_ai_models_failed,
                                uiState.cloudAiModelDiscoveryError.orEmpty(),
                            )
                            else -> stringResource(R.string.settings_ai_models_not_checked)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onRefreshModels,
                        enabled = !uiState.cloudAiModelDiscoveryInProgress,
                    ) {
                        if (uiState.cloudAiModelDiscoveryInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.settings_ai_models_fetch))
                        }
                    }
                }
                if (uiState.cloudAiDiscoveredModels.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
                    ) {
                        uiState.cloudAiDiscoveredModels.take(12).forEach { model ->
                            AssistChip(
                                onClick = {
                                    modelDraft = model.id
                                    onModelSave(model.id)
                                },
                                label = {
                                    Text(
                                        text = if (model.supportsImageInput) {
                                            stringResource(R.string.settings_ai_model_chip_image, model.id)
                                        } else {
                                            stringResource(R.string.settings_ai_model_chip_text, model.id)
                                        },
                                        maxLines = 1,
                                    )
                                },
                                leadingIcon = if (model.supportsImageInput) {
                                    {
                                        MedLogIcon(
                                            MedLogIcons.DocumentScanner,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
                if (uiState.cloudAiProvider == CloudAiProvider.OPENAI_COMPATIBLE) {
                    EndpointPresetPicker(
                        presets = uiState.cloudAiEndpointPresets,
                        onSelect = onEndpointPresetSelect,
                    )
                    OutlinedTextField(
                        value = baseUrlDraft,
                        onValueChange = { baseUrlDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_ai_base_url_label)) },
                        supportingText = { Text(stringResource(R.string.settings_ai_base_url_hint)) },
                        trailingIcon = {
                            TextButton(onClick = { onOpenAiBaseUrlSave(baseUrlDraft) }) {
                                Text(stringResource(R.string.common_save))
                            }
                        },
                    )
                    OutlinedTextField(
                        value = providerNameDraft,
                        onValueChange = { providerNameDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_ai_provider_name_label)) },
                        trailingIcon = {
                            TextButton(onClick = { onOpenAiProviderNameSave(providerNameDraft) }) {
                                Text(stringResource(R.string.common_save))
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    ) {
                        OpenAiCompatibleCloudAuthMode.entries.forEachIndexed { index, mode ->
                            ToggleButton(
                                checked = uiState.openAiCompatibleAuthMode == mode,
                                onCheckedChange = { onOpenAiAuthModeChange(mode) },
                                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
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
                if (uiState.cloudAiProvider == CloudAiProvider.MIMO) {
                    OutlinedTextField(
                        value = mimoBaseUrlDraft,
                        onValueChange = { mimoBaseUrlDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_ai_endpoint_label)) },
                        supportingText = { Text(stringResource(R.string.settings_ai_mimo_endpoint_hint)) },
                        trailingIcon = {
                            TextButton(onClick = { onMimoBaseUrlSave(mimoBaseUrlDraft) }) {
                                Text(stringResource(R.string.common_save))
                            }
                        },
                    )
                }
                if (uiState.cloudAiProvider == CloudAiProvider.ANTHROPIC) {
                    EndpointPresetPicker(
                        presets = uiState.cloudAiEndpointPresets.filter {
                            it.protocol == CloudAiEndpointProtocol.ANTHROPIC
                        },
                        onSelect = onEndpointPresetSelect,
                    )
                    OutlinedTextField(
                        value = anthropicBaseUrlDraft,
                        onValueChange = { anthropicBaseUrlDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_ai_endpoint_label)) },
                        supportingText = { Text(stringResource(R.string.settings_ai_anthropic_endpoint_hint)) },
                        trailingIcon = {
                            TextButton(onClick = { onAnthropicBaseUrlSave(anthropicBaseUrlDraft) }) {
                                Text(stringResource(R.string.common_save))
                            }
                        },
                    )
                }
            }
        }

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
        CloudAiUsageSummaryCard(summary = uiState.aiUsageSummary)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(MedLogSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_ai_api_key_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (uiState.cloudAiProviderHasApiKey) {
                                stringResource(R.string.settings_ai_api_key_configured, uiState.cloudAiProvider.providerName)
                            } else {
                                stringResource(R.string.settings_ai_api_key_missing, uiState.cloudAiProvider.providerName)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (uiState.cloudAiProviderHasApiKey) {
                        OutlinedButton(onClick = onApiKeyClear) {
                            Text(stringResource(R.string.settings_ai_api_key_clear))
                        }
                    }
                }
                OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = { apiKeyDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_ai_api_key_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text(stringResource(R.string.settings_ai_api_key_storage_hint)) },
                )
                FilledTonalButton(
                    onClick = {
                        onApiKeySave(apiKeyDraft)
                        apiKeyDraft = ""
                    },
                    enabled = apiKeyDraft.isNotBlank(),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.settings_ai_api_key_save))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EndpointPresetPicker(
    presets: List<CloudAiEndpointPreset>,
    onSelect: (CloudAiEndpointPreset) -> Unit,
) {
    if (presets.isEmpty()) return

    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_ai_endpoint_presets_title, presets.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    stringResource(
                        if (expanded) {
                            R.string.common_collapse
                        } else {
                            R.string.common_expand
                        },
                    ),
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
            ) {
                presets.forEach { preset ->
                    AssistChip(
                        onClick = { onSelect(preset) },
                        label = {
                            Text(
                                text = preset.name,
                                maxLines = 1,
                            )
                        },
                    )
                }
            }
        }
    }
}

internal data class CloudAiUsageSummaryPresentation(
    val isEmpty: Boolean,
    val totalCount: Int,
    val successCount: Int,
    val errorCount: Int,
    val cacheHitCount: Int,
    val latestErrorCategory: String?,
) {
    companion object {
        fun from(rows: List<AiUsageSummaryRow>): CloudAiUsageSummaryPresentation =
            CloudAiUsageSummaryPresentation(
                isEmpty = rows.isEmpty(),
                totalCount = rows.sumOf { it.totalCount },
                successCount = rows.sumOf { it.successCount },
                errorCount = rows.sumOf { it.errorCount },
                cacheHitCount = rows.sumOf { it.cacheHitCount },
                latestErrorCategory = rows
                    .filter { it.lastErrorCategory != null }
                    .maxByOrNull { it.lastUsedAt }
                    ?.lastErrorCategory,
            )
    }
}

@Composable
private fun CloudAiUsageSummaryCard(
    summary: List<AiUsageSummaryRow>,
    modifier: Modifier = Modifier,
) {
    val presentation = CloudAiUsageSummaryPresentation.from(summary)
    if (presentation.isEmpty) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(MedLogSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MedLogIcon(
                    MedLogIcons.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.settings_ai_usage_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(
                    R.string.settings_ai_usage_summary,
                    presentation.totalCount,
                    presentation.successCount,
                    presentation.errorCount,
                    presentation.cacheHitCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            presentation.latestErrorCategory?.let { latestError ->
                AssistChip(
                    onClick = {},
                    label = {
                        Text(stringResource(R.string.settings_ai_usage_last_error, latestError))
                    },
                    leadingIcon = {
                        MedLogIcon(
                            MedLogIcons.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}
