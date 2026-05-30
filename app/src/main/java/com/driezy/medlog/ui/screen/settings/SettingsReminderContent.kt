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


@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsReminderContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
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
        onCheckedChange = viewModel::setPersistentReminder,
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
                        onClick = { viewModel.setPersistentInterval(minutes) },
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
                    if (uiState.earlyReminderMinutes > 0)
                        pluralStringResource(
                            R.plurals.settings_early_reminder_body_on,
                            uiState.earlyReminderMinutes,
                            uiState.earlyReminderMinutes,
                        )
                    else stringResource(R.string.settings_early_reminder_body_off),
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
                    onClick = { viewModel.setEarlyReminderMinutes(mins) },
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
        onCheckedChange = { viewModel.setFollowUpSettings(enabled = it) },
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
                        onClick = { viewModel.setFollowUpSettings(delayMinutes = mins) },
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
                        onClick = { viewModel.setFollowUpSettings(maxCount = count) },
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
        subtitle = if (uiState.enableTimePeriodMode)
            stringResource(R.string.settings_routine_mode_subtitle_on)
        else
            stringResource(R.string.settings_routine_mode_subtitle_off),
        icon = MedLogIcons.Schedule,
        checked = uiState.enableTimePeriodMode,
        onCheckedChange = { viewModel.setEnableTimePeriodMode(it) },
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
            // ── 一览行：五个时间快速预览 ──────────────────────
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MedLogSpacing.Large)
                    .padding(bottom = MedLogSpacing.Small),
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
            ) {
                listOf(
                    Triple(MedLogIcons.WbSunny,      stringResource(R.string.settings_routine_wake), "%02d:%02d".format(uiState.wakeHour,      uiState.wakeMinute)),
                    Triple(MedLogIcons.Coffee,       stringResource(R.string.settings_routine_breakfast), "%02d:%02d".format(uiState.breakfastHour, uiState.breakfastMinute)),
                    Triple(MedLogIcons.LunchDining,  stringResource(R.string.settings_routine_lunch), "%02d:%02d".format(uiState.lunchHour,     uiState.lunchMinute)),
                    Triple(MedLogIcons.DinnerDining, stringResource(R.string.settings_routine_dinner), "%02d:%02d".format(uiState.dinnerHour,    uiState.dinnerMinute)),
                    Triple(MedLogIcons.Bedtime,      stringResource(R.string.settings_routine_bed), "%02d:%02d".format(uiState.bedHour,       uiState.bedMinute)),
                ).forEach { (icon, label, time) ->
                    SuggestionChip(
                        onClick = {},
                        enabled = false,
                        icon = {
                            MedLogIcon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        },
                        label = {
                            Text(
                                "$label $time",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
            RoutineTimeRow(stringResource(R.string.settings_routine_wake), uiState.wakeHour, uiState.wakeMinute,
                MedLogIcons.WbSunny) { h, m -> viewModel.updateRoutineTime("wake", h, m) }
            HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
            RoutineTimeRow(stringResource(R.string.settings_routine_breakfast), uiState.breakfastHour, uiState.breakfastMinute,
                MedLogIcons.Coffee) { h, m -> viewModel.updateRoutineTime("breakfast", h, m) }
            HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
            RoutineTimeRow(stringResource(R.string.settings_routine_lunch), uiState.lunchHour, uiState.lunchMinute,
                MedLogIcons.LunchDining) { h, m -> viewModel.updateRoutineTime("lunch", h, m) }
            HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
            RoutineTimeRow(stringResource(R.string.settings_routine_dinner), uiState.dinnerHour, uiState.dinnerMinute,
                MedLogIcons.DinnerDining) { h, m -> viewModel.updateRoutineTime("dinner", h, m) }
            HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
            RoutineTimeRow(stringResource(R.string.settings_routine_bed), uiState.bedHour, uiState.bedMinute,
                MedLogIcons.Bedtime) { h, m -> viewModel.updateRoutineTime("bed", h, m) }
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
        subtitle = if (uiState.travelMode && uiState.homeTimeZoneId.isNotBlank())
            stringResource(R.string.settings_travel_subtitle_on, uiState.homeTimeZoneId)
        else
            stringResource(R.string.settings_travel_subtitle_off),
        checked = uiState.travelMode,
        onCheckedChange = viewModel::setTravelMode,
        icon = MedLogIcons.Schedule,
    )
    }
}
