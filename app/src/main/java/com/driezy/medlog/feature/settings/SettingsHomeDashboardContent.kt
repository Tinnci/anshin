package com.driezy.medlog.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

@Composable
internal fun SettingsHomeDashboard(
    uiState: SettingsUiState,
    onNavigateToAppearanceSettings: () -> Unit,
    onNavigateToReminderSettings: () -> Unit,
    onNavigateToModuleSettings: () -> Unit,
    onNavigateToIntelligenceSettings: () -> Unit,
    onNavigateToBpx1Settings: () -> Unit,
    onNavigateToWidgetSettings: () -> Unit,
    onNavigateToDataSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium)) {
        SettingsNavigationGroup(title = stringResource(R.string.settings_home_frequent_group)) {
            SettingsNavigationRow(
                title = stringResource(R.string.settings_destination_reminders),
                subtitle = stringResource(R.string.settings_home_tile_reminders_status),
                icon = MedLogIcons.Notifications,
                onClick = onNavigateToReminderSettings,
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = stringResource(R.string.settings_group_appearance_home),
                subtitle = stringResource(R.string.settings_group_appearance_home_desc),
                icon = MedLogIcons.Palette,
                onClick = onNavigateToAppearanceSettings,
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = stringResource(R.string.settings_group_modules_meds),
                subtitle = stringResource(R.string.settings_group_modules_meds_desc),
                icon = MedLogIcons.Tune,
                onClick = onNavigateToModuleSettings,
            )
        }

        SettingsNavigationGroup(title = stringResource(R.string.settings_home_more_group)) {
            SettingsNavigationRow(
                title = stringResource(R.string.settings_destination_intelligence),
                subtitle = stringResource(
                    if (uiState.cloudAiEnabled) {
                        R.string.settings_home_tile_ai_status_cloud
                    } else {
                        R.string.settings_home_tile_ai_status
                    },
                ),
                icon = MedLogIcons.Memory,
                onClick = onNavigateToIntelligenceSettings,
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = stringResource(R.string.settings_destination_bpx1),
                subtitle = stringResource(R.string.settings_home_tile_bpx1_status),
                icon = MedLogIcons.MonitorHeart,
                onClick = onNavigateToBpx1Settings,
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = stringResource(R.string.settings_destination_widgets),
                subtitle = stringResource(R.string.settings_home_tile_widgets_status),
                icon = MedLogIcons.Widgets,
                onClick = onNavigateToWidgetSettings,
            )
            HorizontalDivider()
            SettingsNavigationRow(
                title = stringResource(R.string.settings_destination_data_about),
                subtitle = pluralStringResource(
                    R.plurals.settings_home_tile_data_status,
                    uiState.archivedMedications.size,
                    uiState.archivedMedications.size,
                ),
                icon = MedLogIcons.CloudUpload,
                onClick = onNavigateToDataSettings,
            )
        }
    }
}
