package com.driezy.medlog.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

@OptIn(ExperimentalLayoutApi::class)
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
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MedLogSpacing.Large)
                .padding(bottom = MedLogSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            SettingsDestinationTile(
                title = stringResource(R.string.settings_destination_reminders),
                status = stringResource(R.string.settings_home_tile_reminders_status),
                icon = MedLogIcons.Notifications,
                onClick = onNavigateToReminderSettings,
            )
            SettingsDestinationTile(
                title = stringResource(R.string.settings_destination_intelligence),
                status = stringResource(
                    if (uiState.cloudAiEnabled) {
                        R.string.settings_home_tile_ai_status_cloud
                    } else {
                        R.string.settings_home_tile_ai_status
                    },
                ),
                icon = MedLogIcons.Memory,
                onClick = onNavigateToIntelligenceSettings,
            )
            SettingsDestinationTile(
                title = stringResource(R.string.settings_destination_widgets),
                status = stringResource(R.string.settings_home_tile_widgets_status),
                icon = MedLogIcons.Widgets,
                onClick = onNavigateToWidgetSettings,
            )
            SettingsDestinationTile(
                title = stringResource(R.string.settings_destination_data_about),
                status = pluralStringResource(
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

@Composable
private fun SettingsDestinationTile(
    title: String,
    status: String,
    icon: Int,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .widthIn(min = 150.dp)
            .heightIn(min = 116.dp)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    MedLogIcon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp),
                    )
                }
                MedLogIcon(
                    MedLogIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
