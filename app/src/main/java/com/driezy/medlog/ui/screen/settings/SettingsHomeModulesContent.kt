package com.driezy.medlog.ui.screen.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

@Composable
internal fun SettingsHomeModulesContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsCard(
        title = stringResource(R.string.settings_group_modules_meds),
        subtitle = stringResource(R.string.settings_group_modules_meds_desc),
        icon = MedLogIcons.Tune,
    ) {
        Text(
            stringResource(R.string.settings_features_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = MedLogSpacing.Large)
                .padding(top = MedLogSpacing.Tiny)
                .padding(bottom = MedLogSpacing.Small),
        )
        ModuleToggleList(
            uiState = uiState,
            viewModel = viewModel,
        )
        SettingsSectionDivider(
            title = stringResource(R.string.settings_card_meds),
            icon = MedLogIcons.MedicalServices,
        )
        ArchivedMedicationsRow(
            archived = uiState.archivedMedications,
            onRestore = viewModel::unarchiveMedication,
        )
    }
}
