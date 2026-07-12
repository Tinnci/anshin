package com.driezy.medlog.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcons

@Composable
internal fun SettingsHomeDashboard(
    uiState: SettingsUiState,
    onNavigateToReminderSettings: () -> Unit,
    onNavigateToIntelligenceSettings: () -> Unit,
    onNavigateToWidgetSettings: () -> Unit,
    onNavigateToDataSettings: () -> Unit,
) {
    SettingsCard(
        title = stringResource(R.string.settings_home_dashboard_title),
        subtitle = stringResource(R.string.settings_home_dashboard_desc),
        icon = MedLogIcons.Settings,
    ) {
        SettingsNavigationRow(
                title = stringResource(R.string.settings_destination_reminders),
                subtitle = stringResource(R.string.settings_home_tile_reminders_status),
                icon = MedLogIcons.Notifications,
                onClick = onNavigateToReminderSettings,
            )
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
        SettingsNavigationRow(
                title = stringResource(R.string.settings_destination_widgets),
                subtitle = stringResource(R.string.settings_home_tile_widgets_status),
                icon = MedLogIcons.Widgets,
                onClick = onNavigateToWidgetSettings,
            )
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
