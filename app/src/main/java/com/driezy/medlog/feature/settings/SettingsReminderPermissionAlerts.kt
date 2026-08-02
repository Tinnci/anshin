package com.driezy.medlog.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcons

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsReminderPermissionAlerts(
    canScheduleExactAlarms: Boolean,
    canPostNotifications: Boolean,
    onRequestExactAlarmPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    val hasMissingPermission = !canScheduleExactAlarms || !canPostNotifications
    val motionScheme = MaterialTheme.motionScheme

    AnimatedVisibility(
        visible = hasMissingPermission,
        enter = expandVertically(motionScheme.defaultSpatialSpec()) +
            fadeIn(motionScheme.defaultEffectsSpec()),
        exit = shrinkVertically(motionScheme.fastSpatialSpec()) +
            fadeOut(motionScheme.fastEffectsSpec()),
    ) {
        SettingsCard(
            title = stringResource(R.string.settings_home_reminders_warning),
            subtitle = stringResource(R.string.settings_reminder_permissions_desc),
            icon = MedLogIcons.Warning,
        ) {
            if (!canScheduleExactAlarms) {
                SettingsNavigationRow(
                    title = stringResource(R.string.settings_alarm_perm_title),
                    subtitle = stringResource(R.string.settings_alarm_perm_body),
                    icon = MedLogIcons.Schedule,
                    onClick = onRequestExactAlarmPermission,
                )
            }
            if (!canPostNotifications) {
                SettingsNavigationRow(
                    title = stringResource(R.string.settings_notif_perm_title),
                    subtitle = stringResource(R.string.settings_notif_perm_body),
                    icon = MedLogIcons.NotificationsOff,
                    onClick = onRequestNotificationPermission,
                )
            }
        }
    }
}
