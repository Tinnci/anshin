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

internal data class WidgetCarouselItem(
    val previewType: WidgetPreviewType,
    val name: String,
    val description: String,
    val sizes: List<String>,
    val canPin: Boolean,
    val showActions: Boolean = true,
    val onAdd: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetPreviewCarousel(items: List<WidgetCarouselItem>) {
    val carouselState = rememberCarouselState { items.size }

    HorizontalCenteredHeroCarousel(
        state = carouselState,
        modifier = Modifier
            .fillMaxWidth()
            .height(296.dp),
        itemSpacing = MedLogSpacing.Small,
        maxItemWidth = 320.dp,
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) { index ->
        val item = items[index]
        WidgetPickerCard(
            previewType = item.previewType,
            name = item.name,
            description = item.description,
            sizes = item.sizes,
            canPin = item.canPin,
            showActions = item.showActions,
            modifier = Modifier
                .fillMaxHeight()
                .maskClip(RoundedCornerShape(24.dp)),
            onAdd = item.onAdd,
        )
    }
}

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

@Composable
internal fun ThemePaletteChip(
    palette: ThemePalette,
    selected: Boolean,
    darkTheme: Boolean,
    onClick: () -> Unit,
) {
    val scheme = palette.colorScheme(darkTheme)
    Surface(
        modifier = Modifier
            .widthIn(min = 148.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .semantics { role = Role.RadioButton },
        shape = RoundedCornerShape(18.dp),
        color = if (selected) scheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) scheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) scheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MedLogSpacing.Small, vertical = MedLogSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(scheme.primary, scheme.secondary, scheme.tertiary).forEach { swatchColor ->
                    Surface(
                        modifier = Modifier.size(width = 8.dp, height = 34.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = swatchColor,
                        content = {},
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = palette.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(palette.descriptionRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.78f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun <T> DisplayOptionGroup(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            options.forEachIndexed { index, (value, label) ->
                ToggleButton(
                    checked = selected == value,
                    onCheckedChange = { onSelected(value) },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

internal enum class CloudAiSettingsVisualState {
    OFF,
    NEEDS_KEY,
    READY,
    TEXT_ONLY,
}

internal data class CloudAiSettingsPresentation(
    val visualState: CloudAiSettingsVisualState,
    @param:StringRes val labelRes: Int,
    @param:StringRes val bodyRes: Int,
) {
    companion object {
        fun from(
            enabled: Boolean,
            hasApiKey: Boolean,
            supportsImageInput: Boolean,
        ): CloudAiSettingsPresentation = when {
            !enabled -> CloudAiSettingsPresentation(
                visualState = CloudAiSettingsVisualState.OFF,
                labelRes = R.string.settings_ai_status_off,
                bodyRes = R.string.settings_ai_status_off_body,
            )
            !hasApiKey -> CloudAiSettingsPresentation(
                visualState = CloudAiSettingsVisualState.NEEDS_KEY,
                labelRes = R.string.settings_ai_status_needs_key,
                bodyRes = R.string.settings_ai_status_needs_key_body,
            )
            supportsImageInput -> CloudAiSettingsPresentation(
                visualState = CloudAiSettingsVisualState.READY,
                labelRes = R.string.settings_ai_status_ready,
                bodyRes = R.string.settings_ai_status_ready_body,
            )
            else -> CloudAiSettingsPresentation(
                visualState = CloudAiSettingsVisualState.TEXT_ONLY,
                labelRes = R.string.settings_ai_status_text_only,
                bodyRes = R.string.settings_ai_status_text_only_body,
            )
        }
    }
}

@Composable
internal fun CloudAiStatusSummary(
    uiState: SettingsUiState,
    modifier: Modifier = Modifier,
) {
    val presentation = CloudAiSettingsPresentation.from(
        enabled = uiState.cloudAiEnabled,
        hasApiKey = uiState.cloudAiProviderHasApiKey,
        supportsImageInput = uiState.cloudAiSupportsImageInput,
    )
    val colors = when (presentation.visualState) {
        CloudAiSettingsVisualState.OFF -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        CloudAiSettingsVisualState.NEEDS_KEY -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        CloudAiSettingsVisualState.READY -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        CloudAiSettingsVisualState.TEXT_ONLY -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(MedLogSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MedLogIcon(
                icon = when (presentation.visualState) {
                    CloudAiSettingsVisualState.OFF -> MedLogIcons.CloudUpload
                    CloudAiSettingsVisualState.NEEDS_KEY -> MedLogIcons.Info
                    CloudAiSettingsVisualState.READY -> MedLogIcons.AutoAwesome
                    CloudAiSettingsVisualState.TEXT_ONLY -> MedLogIcons.AutoAwesome
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
            ) {
                Text(
                    text = stringResource(presentation.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(presentation.bodyRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.82f),
                )
            }
        }
    }
}

@Composable
internal fun OcrModelOptionCard(
    title: String,
    tag: String,
    description: String,
    specs: List<Pair<Int, String>>,
    selected: Boolean,
    tagContainerColor: Color,
    tagContentColor: Color,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MedLogSpacing.Medium),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = tagContainerColor,
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = tagContentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.padding(top = MedLogSpacing.Tiny),
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                ) {
                    specs.forEach { (icon, text) ->
                        SpecBadge(icon = icon, text = text)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecBadge(icon: Int, text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MedLogIcon(
                icon = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
