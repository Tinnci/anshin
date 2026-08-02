package com.driezy.medlog.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.data.repository.OcrModelType
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsIntelligenceContent(
    uiState: SettingsUiState,
    onAction: (SettingsUiAction) -> Unit,
    onNavigateToCloudApiSettings: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
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
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
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
                onSelect = { onAction(SettingsUiAction.SetOcrModel(OcrModelType.LIGHT_SVTR)) },
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
                onSelect = { onAction(SettingsUiAction.SetOcrModel(OcrModelType.FASTVIT_T8)) },
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
            onCheckedChange = { onAction(SettingsUiAction.SetCloudAi(enabled = it)) },
            icon = MedLogIcons.CloudUpload,
        )
        AnimatedVisibility(
            visible = uiState.cloudAiEnabled,
            enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            SettingsNavigationRow(
                title = stringResource(R.string.settings_ai_config_title),
                subtitle = stringResource(R.string.settings_ai_config_desc),
                icon = MedLogIcons.CloudUpload,
                onClick = onNavigateToCloudApiSettings,
            )
        }
    }
}
