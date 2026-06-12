package com.driezy.medlog.ui.screen.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModuleToggleGrid(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MedLogSpacing.Large)
            .padding(bottom = MedLogSpacing.Medium),
        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
    ) {
        SettingsModuleToggleCard(
            title = stringResource(R.string.settings_symptom_title),
            subtitle = stringResource(R.string.settings_symptom_subtitle),
            icon = MedLogIcons.EditNote,
            checked = uiState.enableSymptomDiary,
            onCheckedChange = viewModel::setEnableSymptomDiary,
        )
        SettingsModuleToggleCard(
            title = stringResource(R.string.settings_drug_db_title),
            subtitle = stringResource(R.string.settings_drug_db_subtitle),
            icon = MedLogIcons.MedicalServices,
            checked = uiState.enableDrugDatabase,
            onCheckedChange = viewModel::setEnableDrugDatabase,
        )
        SettingsModuleToggleCard(
            title = stringResource(R.string.settings_health_title),
            subtitle = stringResource(R.string.settings_health_subtitle),
            icon = MedLogIcons.MonitorHeart,
            checked = uiState.enableHealthModule,
            onCheckedChange = viewModel::setEnableHealthModule,
        )
        SettingsModuleToggleCard(
            title = stringResource(R.string.settings_interaction_title),
            subtitle = stringResource(R.string.settings_interaction_subtitle),
            icon = MedLogIcons.Warning,
            checked = uiState.enableDrugInteractionCheck,
            onCheckedChange = viewModel::setEnableDrugInteractionCheck,
        )
    }
}

@Composable
private fun SettingsModuleToggleCard(
    title: String,
    subtitle: String,
    icon: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val statusText = stringResource(
        if (checked) {
            R.string.settings_on
        } else {
            R.string.settings_off
        },
    )

    Surface(
        modifier = Modifier
            .widthIn(min = 150.dp)
            .heightIn(min = 138.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics {
                stateDescription = statusText
            },
        shape = RoundedCornerShape(20.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (checked) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
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
                MedLogIcon(
                    icon,
                    contentDescription = null,
                    tint = if (checked) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp),
                )
                Switch(
                    checked = checked,
                    onCheckedChange = null,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (checked) {
                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (checked) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = MedLogSpacing.Small, vertical = 6.dp),
                )
            }
        }
    }
}
