package com.driezy.medlog.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.driezy.medlog.BuildConfig
import com.driezy.medlog.R
import com.driezy.medlog.capability.update.model.AppUpdateState
import com.driezy.medlog.capability.update.ui.AppUpdateDialog
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun SettingsDataContent(
    backupInProgress: Boolean,
    onBackupClick: (String) -> Unit,
    onRestoreClick: () -> Unit,
    onReplayWelcome: () -> Unit,
    updateState: AppUpdateState = AppUpdateState.Idle,
    autoCheckUpdates: Boolean = true,
    showUpdateDialog: Boolean = false,
    onCheckForUpdates: () -> Unit = {},
    onToggleAutoCheckUpdates: (Boolean) -> Unit = {},
    onOpenUpdateDialog: () -> Unit = {},
    onDismissUpdateDialog: () -> Unit = {},
) {
    val context = LocalContext.current

    if (showUpdateDialog && updateState is AppUpdateState.UpdateAvailable) {
        val info = updateState.updateInfo
        AppUpdateDialog(
            updateInfo = info,
            onDismiss = onDismissUpdateDialog,
            onDownload = {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl ?: info.releaseUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            },
            onViewRelease = {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            },
        )
    }

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
                val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)
                    .format(LocalDateTime.now())
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
            headlineContent = { Text(stringResource(R.string.settings_check_update_title)) },
            supportingContent = {
                when (updateState) {
                    is AppUpdateState.Checking -> Text(stringResource(R.string.settings_check_update_checking))
                    is AppUpdateState.UpToDate -> Text(
                        stringResource(R.string.settings_check_update_up_to_date, updateState.version),
                    )
                    is AppUpdateState.UpdateAvailable -> Text(
                        stringResource(R.string.settings_check_update_available, updateState.updateInfo.latestVersion),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    is AppUpdateState.Error -> Text(
                        stringResource(R.string.settings_check_update_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                    AppUpdateState.Idle -> Text(stringResource(R.string.settings_check_update_subtitle_idle))
                }
            },
            leadingContent = {
                MedLogIcon(
                    MedLogIcons.Refresh,
                    contentDescription = null,
                    tint = if (updateState is AppUpdateState.UpdateAvailable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
            trailingContent = {
                when (updateState) {
                    is AppUpdateState.Checking -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    is AppUpdateState.UpdateAvailable -> {
                        FilledTonalButton(
                            onClick = onOpenUpdateDialog,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(stringResource(R.string.update_badge_new))
                        }
                    }
                    is AppUpdateState.UpToDate -> {
                        MedLogIcon(
                            MedLogIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    else -> {
                        MedLogIcon(
                            MedLogIcons.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            modifier = Modifier.clickable(
                enabled = updateState !is AppUpdateState.Checking,
                onClick = {
                    if (updateState is AppUpdateState.UpdateAvailable) {
                        onOpenUpdateDialog()
                    } else {
                        onCheckForUpdates()
                    }
                },
            ),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_auto_check_updates_title)) },
            supportingContent = { Text(stringResource(R.string.settings_auto_check_updates_desc)) },
            trailingContent = {
                Switch(
                    checked = autoCheckUpdates,
                    onCheckedChange = onToggleAutoCheckUpdates,
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
