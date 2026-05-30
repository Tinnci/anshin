package com.driezy.medlog.ui.screen.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driezy.medlog.BuildConfig
import com.driezy.medlog.R
import com.driezy.medlog.data.repository.AppTextScale
import com.driezy.medlog.data.repository.FontMode
import com.driezy.medlog.data.repository.OcrModelType
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.data.repository.UiDensityScale
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.theme.ThemePalette
import com.driezy.medlog.ui.utils.OemWidgetHelper
import com.driezy.medlog.widget.MedLogWidgetReceiver
import com.driezy.medlog.widget.NextDoseWidgetReceiver
import com.driezy.medlog.widget.StreakWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


@Composable
internal fun SettingsHomeModulesContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onNavigateToReminderSettings: () -> Unit,
    onNavigateToIntelligenceSettings: () -> Unit,
    onNavigateToWidgetSettings: () -> Unit,
    onNavigateToDataSettings: () -> Unit,
) {
    SettingsCard(
    title = stringResource(R.string.settings_group_modules_meds),
    subtitle = stringResource(R.string.settings_group_modules_meds_desc),
    icon = MedLogIcons.Tune,
    ) {
    SettingsSectionDivider(
        title = stringResource(R.string.settings_card_features),
        icon = MedLogIcons.Tune,
        modifier = Modifier.padding(top = 0.dp),
    )
    Text(
        stringResource(R.string.settings_features_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = MedLogSpacing.Large)
            .padding(bottom = MedLogSpacing.Tiny),
    )
    SettingsSwitchRow(
        title = stringResource(R.string.settings_symptom_title),
        subtitle = stringResource(R.string.settings_symptom_subtitle),
        checked = uiState.enableSymptomDiary,
        onCheckedChange = viewModel::setEnableSymptomDiary,
        icon = MedLogIcons.EditNote,
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
    SettingsSwitchRow(
        title = stringResource(R.string.settings_drug_db_title),
        subtitle = stringResource(R.string.settings_drug_db_subtitle),
        checked = uiState.enableDrugDatabase,
        onCheckedChange = viewModel::setEnableDrugDatabase,
        icon = MedLogIcons.MedicalServices,
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
    SettingsSwitchRow(
        title = stringResource(R.string.settings_health_title),
        subtitle = stringResource(R.string.settings_health_subtitle),
        checked = uiState.enableHealthModule,
        onCheckedChange = viewModel::setEnableHealthModule,
        icon = MedLogIcons.MonitorHeart,
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
    SettingsSwitchRow(
        title = stringResource(R.string.settings_interaction_title),
        subtitle = stringResource(R.string.settings_interaction_subtitle),
        checked = uiState.enableDrugInteractionCheck,
        onCheckedChange = viewModel::setEnableDrugInteractionCheck,
        icon = MedLogIcons.Warning,
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

    SettingsCard(
        title = stringResource(R.string.settings_more_title),
        subtitle = stringResource(R.string.settings_more_desc),
        icon = MedLogIcons.Settings,
    ) {
        SettingsNavigationRow(
            title = stringResource(R.string.settings_destination_reminders),
            subtitle = stringResource(R.string.settings_destination_reminders_desc),
            icon = MedLogIcons.Notifications,
            onClick = onNavigateToReminderSettings,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
        SettingsNavigationRow(
            title = stringResource(R.string.settings_destination_intelligence),
            subtitle = stringResource(R.string.settings_destination_intelligence_desc),
            icon = MedLogIcons.Memory,
            onClick = onNavigateToIntelligenceSettings,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
        SettingsNavigationRow(
            title = stringResource(R.string.settings_destination_widgets),
            subtitle = stringResource(R.string.settings_destination_widgets_desc),
            icon = MedLogIcons.Widgets,
            onClick = onNavigateToWidgetSettings,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
        SettingsNavigationRow(
            title = stringResource(R.string.settings_destination_data_about),
            subtitle = stringResource(R.string.settings_destination_data_about_desc),
            icon = MedLogIcons.CloudUpload,
            onClick = onNavigateToDataSettings,
        )
    }
}
