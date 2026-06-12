package com.driezy.medlog.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CloudAiProviderSection(
    uiState: SettingsUiState,
    onProviderChange: (CloudAiProvider) -> Unit,
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
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CloudAiModelSection(
    uiState: SettingsUiState,
    onModelSave: (String) -> Unit,
    onRefreshModels: () -> Unit,
) {
    var modelDraft by rememberSaveable(uiState.cloudAiProvider, uiState.cloudAiModel) {
        mutableStateOf(uiState.cloudAiModel)
    }
    val canCheckModels = uiState.cloudAiProviderHasApiKey

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
                text = stringResource(R.string.settings_ai_model_section_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
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
            ModelDiscoveryStatusRow(
                uiState = uiState,
                canCheckModels = canCheckModels,
                onRefreshModels = onRefreshModels,
            )
            DiscoveredModelChips(
                uiState = uiState,
                onModelSelect = { model ->
                    modelDraft = model
                    onModelSave(model)
                },
            )
        }
    }
}

@Composable
private fun ModelDiscoveryStatusRow(
    uiState: SettingsUiState,
    canCheckModels: Boolean,
    onRefreshModels: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val connected = uiState.cloudAiModelDiscoveryConnected
        Text(
            text = when {
                !canCheckModels -> stringResource(R.string.settings_ai_models_key_required)
                uiState.cloudAiModelDiscoveryInProgress -> stringResource(R.string.settings_ai_models_fetching)
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
            enabled = canCheckModels && !uiState.cloudAiModelDiscoveryInProgress,
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoveredModelChips(
    uiState: SettingsUiState,
    onModelSelect: (String) -> Unit,
) {
    if (uiState.cloudAiDiscoveredModels.isEmpty()) return

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
    ) {
        uiState.cloudAiDiscoveredModels.take(12).forEach { model ->
            AssistChip(
                onClick = { onModelSelect(model.id) },
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
