package com.driezy.medlog.ui.screen.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
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
    onApiKeyImport: (String) -> Unit,
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
    var apiKeyImportDraft by rememberSaveable { mutableStateOf("") }
    val apiKeyImportPresentation = CloudAiApiKeyImportPresentation.from(apiKeyImportDraft)

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
                        currentBaseUrl = uiState.openAiCompatibleBaseUrl,
                        protocol = CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
                        onSelect = { preset ->
                            baseUrlDraft = preset.api
                            providerNameDraft = preset.name
                            onEndpointPresetSelect(preset)
                        },
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
                        presets = uiState.cloudAiEndpointPresets,
                        currentBaseUrl = uiState.anthropicCloudAiBaseUrl,
                        protocol = CloudAiEndpointProtocol.ANTHROPIC,
                        onSelect = { preset ->
                            anthropicBaseUrlDraft = preset.api
                            providerNameDraft = preset.name
                            onEndpointPresetSelect(preset)
                        },
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
                HorizontalDivider()
                OutlinedTextField(
                    value = apiKeyImportDraft,
                    onValueChange = { apiKeyImportDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    label = { Text(stringResource(R.string.settings_ai_api_key_import_label)) },
                    supportingText = {
                        if (apiKeyImportPresentation.canImport) {
                            val model = apiKeyImportPresentation.model
                            Text(
                                text = if (model.isNullOrBlank()) {
                                    stringResource(
                                        R.string.settings_ai_api_key_import_preview,
                                        apiKeyImportPresentation.providerName.orEmpty(),
                                    )
                                } else {
                                    stringResource(
                                        R.string.settings_ai_api_key_import_preview_with_model,
                                        apiKeyImportPresentation.providerName.orEmpty(),
                                        model,
                                    )
                                },
                            )
                        } else {
                            Text(stringResource(R.string.settings_ai_api_key_import_hint))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(stringResource(R.string.settings_ai_api_key_import_standard_chip)) },
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(stringResource(R.string.settings_ai_api_key_import_env_chip)) },
                    )
                    FilledTonalButton(
                        onClick = {
                            onApiKeyImport(apiKeyImportDraft)
                            apiKeyImportDraft = ""
                        },
                        enabled = apiKeyImportPresentation.canImport,
                    ) {
                        Text(stringResource(R.string.settings_ai_api_key_import_save))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EndpointPresetPicker(
    presets: List<CloudAiEndpointPreset>,
    currentBaseUrl: String,
    protocol: CloudAiEndpointProtocol,
    onSelect: (CloudAiEndpointPreset) -> Unit,
) {
    val protocolPresetCount = presets.count { it.protocol == protocol }
    if (protocolPresetCount == 0) return

    var expanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val presentation = CloudAiEndpointPresetListPresentation.from(
        presets = presets,
        query = query,
        currentBaseUrl = currentBaseUrl,
        protocol = protocol,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_ai_endpoint_presets_title, protocolPresetCount),
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
        if (presentation.featuredRows.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
            ) {
                Text(
                    text = stringResource(R.string.settings_ai_endpoint_featured_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                    verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
                ) {
                    presentation.featuredRows.forEach { row ->
                        FilterChip(
                            selected = row.selected,
                            onClick = { onSelect(row.preset) },
                            label = {
                                Text(
                                    text = row.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = if (row.selected) {
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
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_ai_endpoint_search_label)) },
                    leadingIcon = {
                        MedLogIcon(
                            MedLogIcons.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                if (presentation.rows.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_ai_endpoint_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = MedLogSpacing.Tiny),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
                    ) {
                        items(presentation.rows, key = { it.id }) { row ->
                            EndpointPresetRow(
                                row = row,
                                onClick = { onSelect(row.preset) },
                            )
                        }
                    }
                }
            }
        }
    }
}

internal data class CloudAiEndpointPresetRowPresentation(
    val id: String,
    val name: String,
    val api: String,
    val protocol: CloudAiEndpointProtocol,
    val selected: Boolean,
    val preset: CloudAiEndpointPreset,
)

internal data class CloudAiEndpointPresetListPresentation(
    val rows: List<CloudAiEndpointPresetRowPresentation>,
    val featuredRows: List<CloudAiEndpointPresetRowPresentation> = emptyList(),
) {
    companion object {
        fun from(
            presets: List<CloudAiEndpointPreset>,
            query: String,
            currentBaseUrl: String,
            protocol: CloudAiEndpointProtocol,
        ): CloudAiEndpointPresetListPresentation {
            val normalizedQuery = query.trim().lowercase()
            val normalizedCurrentBaseUrl = currentBaseUrl.normalizedEndpointUrl()
            val filteredPresets = presets
                .asSequence()
                .filter { preset -> preset.protocol == protocol }
                .filter { preset ->
                    normalizedQuery.isBlank() ||
                        preset.name.lowercase().contains(normalizedQuery) ||
                        preset.api.lowercase().contains(normalizedQuery) ||
                        preset.id.lowercase().contains(normalizedQuery)
                }
                .toList()
            val featuredRanks = if (normalizedQuery.isBlank()) {
                protocol.featuredPresetRanks()
            } else {
                emptyMap()
            }
            val rows = filteredPresets
                .sortedWith(
                    compareBy<CloudAiEndpointPreset> {
                        it.api.normalizedEndpointUrl() != normalizedCurrentBaseUrl
                    }.thenBy {
                        featuredRanks[it.id] ?: Int.MAX_VALUE
                    }.thenBy {
                        it.name.lowercase()
                    },
                )
                .take(80)
                .map { preset -> preset.toRow(normalizedCurrentBaseUrl) }
                .toList()
            val featuredRows = filteredPresets
                .filter { preset -> preset.id in featuredRanks }
                .sortedBy { preset -> featuredRanks[preset.id] ?: Int.MAX_VALUE }
                .map { preset -> preset.toRow(normalizedCurrentBaseUrl) }
            return CloudAiEndpointPresetListPresentation(
                rows = rows,
                featuredRows = featuredRows,
            )
        }
    }
}

private fun String.normalizedEndpointUrl(): String =
    trim().trimEnd('/').lowercase()

private fun CloudAiEndpointProtocol.featuredPresetRanks(): Map<String, Int> =
    when (this) {
        CloudAiEndpointProtocol.OPENAI_COMPATIBLE -> listOf(
            "nvidia-nim",
            "openai",
            "openrouter",
            "groq",
            "lmstudio",
            "ollama-local",
        )
        CloudAiEndpointProtocol.ANTHROPIC -> listOf("anthropic")
    }.withIndex().associate { (index, id) -> id to index }

private fun CloudAiEndpointPreset.toRow(normalizedCurrentBaseUrl: String): CloudAiEndpointPresetRowPresentation =
    CloudAiEndpointPresetRowPresentation(
        id = id,
        name = name,
        api = api,
        protocol = protocol,
        selected = api.normalizedEndpointUrl() == normalizedCurrentBaseUrl,
        preset = this,
    )

@Composable
private fun EndpointPresetRow(
    row: CloudAiEndpointPresetRowPresentation,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (row.selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (row.selected) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MedLogSpacing.Small, vertical = MedLogSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.selected) {
                MedLogIcon(
                    MedLogIcons.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.name,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AssistChip(
                        onClick = onClick,
                        label = {
                            Text(
                                text = when (row.protocol) {
                                    CloudAiEndpointProtocol.ANTHROPIC -> "Anthropic"
                                    CloudAiEndpointProtocol.OPENAI_COMPATIBLE -> "OpenAI"
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
                Text(
                    text = row.api,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (row.selected) {
                    stringResource(R.string.settings_ai_endpoint_selected)
                } else {
                    stringResource(R.string.common_select)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (row.selected) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
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
