package com.driezy.medlog.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.ui.components.RoutineScheduleEditor
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsReminderContent(uiState: SettingsUiState, onAction: (SettingsUiAction) -> Unit) {
    val motionScheme = MaterialTheme.motionScheme
    SettingsCard(
        title = stringResource(R.string.settings_group_reminders_routine),
        subtitle = stringResource(R.string.settings_group_reminders_routine_desc),
        icon = MedLogIcons.Notifications,
    ) {
        NotificationSettingsOverview(uiState)
        SettingsSwitchRow(
            title = stringResource(R.string.settings_persistent_title),
            subtitle = stringResource(R.string.settings_persistent_subtitle),
            checked = uiState.persistentReminder,
            onCheckedChange = { onAction(SettingsUiAction.SetPersistentReminder(it)) },
            icon = MedLogIcons.NotificationsActive,
        )
        AnimatedVisibility(
            visible = uiState.persistentReminder,
            enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MedLogSpacing.Large)
                    .padding(bottom = MedLogSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
            ) {
                HorizontalDivider(modifier = Modifier.padding(vertical = MedLogSpacing.Tiny))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
                ) {
                    MedLogIcon(
                        MedLogIcons.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.settings_interval_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        pluralStringResource(
                            R.plurals.settings_minutes,
                            uiState.persistentIntervalMinutes,
                            uiState.persistentIntervalMinutes,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                ) {
                    listOf(3, 5, 10, 15, 30).forEach { minutes ->
                        FilterChip(
                            selected = uiState.persistentIntervalMinutes == minutes,
                            onClick = { onAction(SettingsUiAction.SetPersistentInterval(minutes)) },
                            label = { Text(pluralStringResource(R.plurals.settings_minutes, minutes, minutes)) },
                        )
                    }
                }
            }
        }
        // ── 提前预告提醒 ──────────────────────────────────
        HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MedLogSpacing.Large)
                .padding(top = MedLogSpacing.Medium, bottom = MedLogSpacing.Tiny),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
            ) {
                MedLogIcon(
                    MedLogIcons.AccessAlarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_early_reminder_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        if (uiState.earlyReminderMinutes > 0) {
                            pluralStringResource(
                                R.plurals.settings_early_reminder_body_on,
                                uiState.earlyReminderMinutes,
                                uiState.earlyReminderMinutes,
                            )
                        } else {
                            stringResource(R.string.settings_early_reminder_body_off)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            ) {
                listOf(
                    0 to stringResource(R.string.settings_off),
                    15 to pluralStringResource(R.plurals.settings_minutes, 15, 15),
                    30 to pluralStringResource(R.plurals.settings_minutes, 30, 30),
                    60 to stringResource(R.string.settings_1hour),
                ).forEach { (mins, label) ->
                    FilterChip(
                        selected = uiState.earlyReminderMinutes == mins,
                        onClick = { onAction(SettingsUiAction.SetEarlyReminder(mins)) },
                        label = { Text(label) },
                    )
                }
            }
        }
        SettingsSectionDivider(
            title = stringResource(R.string.settings_follow_up_section),
            icon = MedLogIcons.NotificationAdd,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_follow_up_enable),
            subtitle = stringResource(R.string.settings_follow_up_enable_desc),
            icon = MedLogIcons.AlarmAdd,
            checked = uiState.followUpReminderEnabled,
            onCheckedChange = { onAction(SettingsUiAction.SetFollowUp(enabled = it)) },
        )
        AnimatedVisibility(
            visible = uiState.followUpReminderEnabled,
            enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MedLogSpacing.Large)
                    .padding(bottom = MedLogSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            ) {
                HorizontalDivider()
                // ── 再提醒间隔 ────────────────────────────
                Text(
                    stringResource(R.string.settings_follow_up_delay),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                ) {
                    listOf(10, 15, 30, 60).forEach { mins ->
                        FilterChip(
                            selected = uiState.followUpDelayMinutes == mins,
                            onClick = { onAction(SettingsUiAction.SetFollowUp(delayMinutes = mins)) },
                            label = {
                                Text(
                                    pluralStringResource(
                                        R.plurals.settings_follow_up_delay_min,
                                        mins,
                                        mins,
                                    ),
                                )
                            },
                        )
                    }
                }
                // ── 最多再提醒次数 ─────────────────────────
                Text(
                    stringResource(R.string.settings_follow_up_count),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                ) {
                    listOf(1, 2, 3).forEach { count ->
                        FilterChip(
                            selected = uiState.followUpMaxCount == count,
                            onClick = { onAction(SettingsUiAction.SetFollowUp(maxCount = count)) },
                            label = { Text("$count") },
                        )
                    }
                }
            }
        }
        SettingsSectionDivider(
            title = stringResource(R.string.settings_routine),
            icon = MedLogIcons.Schedule,
        )
        // ── 模式开关 ──────────────────────────────────────
        SettingsSwitchRow(
            title = stringResource(R.string.settings_routine_mode_title),
            subtitle = if (uiState.enableTimePeriodMode) {
                stringResource(R.string.settings_routine_mode_subtitle_on)
            } else {
                stringResource(R.string.settings_routine_mode_subtitle_off)
            },
            icon = MedLogIcons.Schedule,
            checked = uiState.enableTimePeriodMode,
            onCheckedChange = { onAction(SettingsUiAction.SetTimePeriodMode(it)) },
        )
        // ── 仅在作息模式开启时显示详细时间设置 ────────────
        AnimatedVisibility(
            visible = uiState.enableTimePeriodMode,
            enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            Column {
                HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
                Text(
                    stringResource(R.string.settings_routine_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = MedLogSpacing.Large)
                        .padding(top = MedLogSpacing.Small, bottom = MedLogSpacing.Tiny),
                )
                RoutineScheduleEditor(
                    schedule = uiState.routineSchedule,
                    onTimeChange = { slot, time -> onAction(SettingsUiAction.UpdateRoutineTime(slot, time)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MedLogSpacing.Large)
                        .padding(bottom = MedLogSpacing.Medium),
                )
            }
        }
        SettingsSectionDivider(
            title = stringResource(R.string.settings_card_travel),
            icon = MedLogIcons.FlightTakeoff,
        )
        Text(
            stringResource(R.string.settings_travel_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = MedLogSpacing.Large)
                .padding(bottom = MedLogSpacing.Tiny),
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_travel_title),
            subtitle = if (uiState.travelMode && uiState.homeTimeZoneId.isNotBlank()) {
                stringResource(R.string.settings_travel_subtitle_on, uiState.homeTimeZoneId)
            } else {
                stringResource(R.string.settings_travel_subtitle_off)
            },
            checked = uiState.travelMode,
            onCheckedChange = { onAction(SettingsUiAction.SetTravelMode(it)) },
            icon = MedLogIcons.Schedule,
        )
    }
}
