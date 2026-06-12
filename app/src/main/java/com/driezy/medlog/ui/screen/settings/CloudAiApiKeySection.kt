package com.driezy.medlog.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

@Composable
internal fun ApiKeyManagementSection(
    uiState: SettingsUiState,
    onApiKeySave: (String) -> Unit,
    onApiKeyImport: (String) -> Unit,
    onApiKeyScan: () -> Unit,
    onApiKeyClear: () -> Unit,
) {
    var apiKeyDraft by rememberSaveable(uiState.cloudAiProvider) { mutableStateOf("") }
    var apiKeyImportDraft by rememberSaveable { mutableStateOf("") }
    val apiKeyImportPresentation = CloudAiApiKeyImportPresentation.from(apiKeyImportDraft)
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(MedLogSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            ApiKeyStatusHeader(
                uiState = uiState,
                onApiKeyClear = onApiKeyClear,
            )
            SecondaryTabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text(stringResource(R.string.settings_ai_api_key_tab_manual)) },
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text(stringResource(R.string.settings_ai_api_key_tab_import)) },
                )
            }
            when (selectedTabIndex) {
                0 -> ManualApiKeyPane(
                    draft = apiKeyDraft,
                    onDraftChange = { apiKeyDraft = it },
                    onSave = {
                        onApiKeySave(apiKeyDraft)
                        apiKeyDraft = ""
                    },
                )
                1 -> ApiKeyImportPane(
                    draft = apiKeyImportDraft,
                    presentation = apiKeyImportPresentation,
                    onDraftChange = { apiKeyImportDraft = it },
                    onScan = onApiKeyScan,
                    onImport = {
                        onApiKeyImport(apiKeyImportDraft)
                        apiKeyImportDraft = ""
                    },
                )
            }
        }
    }
}

@Composable
private fun ApiKeyStatusHeader(
    uiState: SettingsUiState,
    onApiKeyClear: () -> Unit,
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
}

@Composable
private fun ManualApiKeyPane(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small)) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.settings_ai_api_key_label)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = { Text(stringResource(R.string.settings_ai_api_key_storage_hint)) },
        )
        FilledTonalButton(
            onClick = onSave,
            enabled = draft.isNotBlank(),
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.settings_ai_api_key_save))
        }
    }
}

@Composable
private fun ApiKeyImportPane(
    draft: String,
    presentation: CloudAiApiKeyImportPresentation,
    onDraftChange: (String) -> Unit,
    onScan: () -> Unit,
    onImport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small)) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
            isError = presentation.visualState == CloudAiApiKeyImportVisualState.UNSUPPORTED,
            label = { Text(stringResource(R.string.settings_ai_api_key_import_label)) },
            supportingText = { ApiKeyImportSupportingText(presentation) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onScan) {
                MedLogIcon(
                    MedLogIcons.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(MedLogSpacing.Tiny))
                Text(stringResource(R.string.settings_ai_api_key_import_scan))
            }
            FilledTonalButton(
                onClick = onImport,
                enabled = presentation.canImport,
            ) {
                Text(stringResource(R.string.settings_ai_api_key_import_save))
            }
        }
    }
}

@Composable
private fun ApiKeyImportSupportingText(
    presentation: CloudAiApiKeyImportPresentation,
) {
    when (presentation.visualState) {
        CloudAiApiKeyImportVisualState.EMPTY ->
            Text(stringResource(R.string.settings_ai_api_key_import_hint))
        CloudAiApiKeyImportVisualState.UNSUPPORTED ->
            Text(stringResource(R.string.settings_ai_api_key_import_unsupported))
        CloudAiApiKeyImportVisualState.READY -> {
            val model = presentation.model
            Text(
                text = if (model.isNullOrBlank()) {
                    stringResource(
                        R.string.settings_ai_api_key_import_preview,
                        presentation.providerName.orEmpty(),
                    )
                } else {
                    stringResource(
                        R.string.settings_ai_api_key_import_preview_with_model,
                        presentation.providerName.orEmpty(),
                        model,
                    )
                },
            )
        }
    }
}
