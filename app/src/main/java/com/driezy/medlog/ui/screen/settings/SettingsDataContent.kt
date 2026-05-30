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
internal fun SettingsDataContent(
    backupInProgress: Boolean,
    onBackupClick: (String) -> Unit,
    onRestoreClick: () -> Unit,
    onReplayWelcome: () -> Unit,
) {
    SettingsCard(
    title = stringResource(R.string.settings_group_data_about),
    subtitle = stringResource(R.string.settings_group_data_about_desc),
    icon = MedLogIcons.CloudUpload,
    ) {
    SettingsSectionDivider(
        title = stringResource(R.string.settings_backup_restore),
        icon = MedLogIcons.CloudUpload,
        modifier = Modifier.padding(top = 0.dp),
    )
    DataSafetyPanel()
    DataActionRow(
        title = stringResource(R.string.settings_backup_title),
        subtitle = stringResource(R.string.settings_backup_subtitle),
        icon = MedLogIcons.Upload,
        actionLabel = stringResource(R.string.settings_data_backup_action),
        enabled = !backupInProgress,
        loading = backupInProgress,
        onClick = {
            val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(java.util.Date())
            onBackupClick("anshin_backup_$ts.db")
        },
    )
    Spacer(Modifier.height(MedLogSpacing.Small))
    DataActionRow(
        title = stringResource(R.string.settings_restore_title),
        subtitle = stringResource(R.string.settings_data_restore_warning_body),
        icon = MedLogIcons.Warning,
        actionLabel = stringResource(R.string.settings_data_restore_action),
        enabled = !backupInProgress,
        destructive = true,
        loading = backupInProgress,
        onClick = {
            onRestoreClick()
        },
    )
    SettingsSectionDivider(
        title = stringResource(R.string.settings_about),
        icon = MedLogIcons.Info,
    )
    ListItem(
        headlineContent = { Text("Anshin") },
        supportingContent = {
            Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
        },
        leadingContent = {
            MedLogIcon(
                MedLogIcons.Medication,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_replay_title)) },
        supportingContent = { Text(stringResource(R.string.settings_replay_subtitle)) },
        leadingContent = {
            MedLogIcon(
                MedLogIcons.Replay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onReplayWelcome),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
    }
}
