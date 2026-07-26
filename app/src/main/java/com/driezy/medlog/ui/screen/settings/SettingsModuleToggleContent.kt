package com.driezy.medlog.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

@Composable
internal fun ModuleToggleList(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val layoutProfile = rememberSettingsLayoutProfile()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MedLogSpacing.Large)
            .padding(bottom = MedLogSpacing.Medium),
    ) {
        SettingsModuleToggleCard(
            title = stringResource(R.string.settings_symptom_title),
            subtitle = stringResource(R.string.settings_symptom_subtitle),
            icon = MedLogIcons.EditNote,
            checked = uiState.enableSymptomDiary,
            onCheckedChange = viewModel::setEnableSymptomDiary,
            showSubtitle = layoutProfile.showSupportingText,
        )
        HorizontalDivider()
        SettingsModuleToggleCard(
            title = stringResource(R.string.settings_drug_db_title),
            subtitle = stringResource(R.string.settings_drug_db_subtitle),
            icon = MedLogIcons.MedicalServices,
            checked = uiState.enableDrugDatabase,
            onCheckedChange = viewModel::setEnableDrugDatabase,
            showSubtitle = layoutProfile.showSupportingText,
        )
        HorizontalDivider()
        SettingsModuleToggleCard(
            title = stringResource(R.string.settings_health_title),
            subtitle = stringResource(R.string.settings_health_subtitle),
            icon = MedLogIcons.MonitorHeart,
            checked = uiState.enableHealthModule,
            onCheckedChange = viewModel::setEnableHealthModule,
            showSubtitle = layoutProfile.showSupportingText,
        )
        HorizontalDivider()
        SettingsModuleToggleCard(
            title = stringResource(R.string.settings_interaction_title),
            subtitle = stringResource(R.string.settings_interaction_subtitle),
            icon = MedLogIcons.Warning,
            checked = uiState.enableDrugInteractionCheck,
            onCheckedChange = viewModel::setEnableDrugInteractionCheck,
            showSubtitle = layoutProfile.showSupportingText,
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
    showSubtitle: Boolean,
) {
    val statusText = stringResource(
        if (checked) {
            R.string.settings_on
        } else {
            R.string.settings_off
        },
    )

    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = if (showSubtitle) {
            {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        leadingContent = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (checked) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (checked) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                MedLogIcon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(MedLogSpacing.Small)
                        .size(20.dp),
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics {
                stateDescription = statusText
            },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}
