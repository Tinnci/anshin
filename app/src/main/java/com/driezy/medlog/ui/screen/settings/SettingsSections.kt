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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsHomeAppearanceContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    palettePreviewDark: Boolean,
) {
    // ── 外观与首页 ─────────────────────────────────────────
    SettingsCard(
    title = stringResource(R.string.settings_group_appearance_home),
    subtitle = stringResource(R.string.settings_group_appearance_home_desc),
    icon = MedLogIcons.Palette,
    ) {
    // ―― 主题模式 ――
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MedLogSpacing.Large)
            .padding(top = MedLogSpacing.Medium, bottom = MedLogSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            MedLogIcon(
                MedLogIcons.DarkMode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        val themeModes = listOf(
            ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
            ThemeMode.LIGHT  to stringResource(R.string.settings_theme_light),
            ThemeMode.DARK   to stringResource(R.string.settings_theme_dark),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            themeModes.forEachIndexed { index, (mode, label) ->
                ToggleButton(
                    checked = uiState.themeMode == mode,
                    onCheckedChange = { viewModel.setThemeMode(mode) },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        themeModes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MedLogSpacing.Large)
            .padding(top = MedLogSpacing.Medium, bottom = MedLogSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            MedLogIcon(
                MedLogIcons.LocalFlorist,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_palette_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(R.string.settings_palette_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            ThemePalette.entries.forEach { palette ->
                ThemePaletteChip(
                    palette = palette,
                    selected = uiState.themePalette == palette,
                    darkTheme = palettePreviewDark,
                    onClick = { viewModel.setThemePalette(palette) },
                )
            }
        }
    }
    // ―― Material You 动态颜色（Android 12+）――
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
        SettingsSwitchRow(
            title = stringResource(R.string.settings_dynamic_color_title),
            subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
            checked = uiState.useDynamicColor,
            onCheckedChange = viewModel::setUseDynamicColor,
            icon = MedLogIcons.ColorLens,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MedLogSpacing.Large)
            .padding(top = MedLogSpacing.Medium, bottom = MedLogSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            MedLogIcon(
                MedLogIcons.Notes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_display_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(R.string.settings_display_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DisplayOptionGroup(
            title = stringResource(R.string.settings_font_mode_title),
            options = listOf(
                FontMode.SYSTEM to stringResource(R.string.settings_font_mode_system),
                FontMode.ANSHIN to stringResource(R.string.settings_font_mode_anshin),
            ),
            selected = uiState.fontMode,
            onSelected = viewModel::setFontMode,
        )
        DisplayOptionGroup(
            title = stringResource(R.string.settings_text_size_title),
            options = listOf(
                AppTextScale.SMALL to stringResource(R.string.settings_text_size_small),
                AppTextScale.STANDARD to stringResource(R.string.settings_text_size_standard),
                AppTextScale.LARGE to stringResource(R.string.settings_text_size_large),
                AppTextScale.EXTRA_LARGE to stringResource(R.string.settings_text_size_extra_large),
            ),
            selected = uiState.appTextScale,
            onSelected = viewModel::setAppTextScale,
        )
        DisplayOptionGroup(
            title = stringResource(R.string.settings_ui_density_title),
            options = listOf(
                UiDensityScale.COMPACT to stringResource(R.string.settings_ui_density_compact),
                UiDensityScale.STANDARD to stringResource(R.string.settings_ui_density_standard),
                UiDensityScale.COMFORTABLE to stringResource(R.string.settings_ui_density_comfortable),
            ),
            selected = uiState.uiDensityScale,
            onSelected = viewModel::setUiDensityScale,
        )
    }
    SettingsSectionDivider(
        title = stringResource(R.string.settings_card_today),
        icon = MedLogIcons.ViewAgenda,
    )
    SettingsSwitchRow(
        title = stringResource(R.string.settings_auto_collapse_title),
        subtitle = stringResource(R.string.settings_auto_collapse_subtitle),
        checked = uiState.autoCollapseCompletedGroups,
        onCheckedChange = viewModel::setAutoCollapseCompletedGroups,
        icon = MedLogIcons.UnfoldLess,
    )
    }
}

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsIntelligenceContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val motionScheme = MaterialTheme.motionScheme
    SettingsCard(
    title = stringResource(R.string.settings_group_intelligence),
    subtitle = stringResource(R.string.settings_group_intelligence_desc),
    icon = MedLogIcons.Memory,
    ) {
    SettingsSectionDivider(
        title = stringResource(R.string.settings_ocr_model_card_title),
        icon = MedLogIcons.DocumentScanner,
        modifier = Modifier.padding(top = 0.dp),
    )
    Text(
        text = stringResource(R.string.settings_ocr_model_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = MedLogSpacing.Large)
            .padding(bottom = MedLogSpacing.Medium),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MedLogSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium)
    ) {
        OcrModelOptionCard(
            title = stringResource(R.string.settings_ocr_model_light_title),
            tag = stringResource(R.string.settings_ocr_model_light_tag),
            description = stringResource(R.string.settings_ocr_model_light_desc),
            specs = listOf(
                MedLogIcons.Storage to stringResource(R.string.settings_ocr_model_light_size),
                MedLogIcons.Speed to stringResource(R.string.settings_ocr_model_light_latency),
                MedLogIcons.CheckCircle to stringResource(R.string.settings_ocr_model_light_accuracy),
            ),
            selected = uiState.ocrModelType == OcrModelType.LIGHT_SVTR,
            tagContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            tagContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onSelect = { viewModel.setOcrModelType(OcrModelType.LIGHT_SVTR) },
        )

        OcrModelOptionCard(
            title = stringResource(R.string.settings_ocr_model_fastvit_title),
            tag = stringResource(R.string.settings_ocr_model_fastvit_tag),
            description = stringResource(R.string.settings_ocr_model_fastvit_desc),
            specs = listOf(
                MedLogIcons.Storage to stringResource(R.string.settings_ocr_model_fastvit_size),
                MedLogIcons.Speed to stringResource(R.string.settings_ocr_model_fastvit_latency),
                MedLogIcons.CheckCircle to stringResource(R.string.settings_ocr_model_fastvit_accuracy),
            ),
            selected = uiState.ocrModelType == OcrModelType.FASTVIT_T8,
            tagContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            tagContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onSelect = { viewModel.setOcrModelType(OcrModelType.FASTVIT_T8) },
        )
    }

    SettingsSectionDivider(
        title = stringResource(R.string.settings_ai_section_title),
        icon = MedLogIcons.AutoAwesome,
    )
    Text(
        text = stringResource(R.string.settings_ai_section_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = MedLogSpacing.Large)
            .padding(bottom = MedLogSpacing.Tiny),
    )
    CloudAiStatusSummary(
        uiState = uiState,
        modifier = Modifier
            .padding(horizontal = MedLogSpacing.Large)
            .padding(bottom = MedLogSpacing.Small),
    )
    SettingsSwitchRow(
        title = stringResource(R.string.settings_ai_enable_title),
        subtitle = stringResource(R.string.settings_ai_enable_subtitle),
        checked = uiState.cloudAiEnabled,
        onCheckedChange = { viewModel.setCloudAiSettings(enabled = it) },
        icon = MedLogIcons.CloudUpload,
    )
    AnimatedVisibility(
        visible = uiState.cloudAiEnabled,
        enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
        exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
    ) {
        CloudAiSettingsPanel(
            uiState = uiState,
            onProviderChange = { viewModel.setCloudAiSettings(provider = it) },
            onModelSave = { viewModel.setCloudAiSettings(model = it) },
            onMimoBaseUrlSave = { viewModel.setCloudAiSettings(mimoBaseUrl = it) },
            onAnthropicBaseUrlSave = { viewModel.setCloudAiSettings(anthropicBaseUrl = it) },
            onOpenAiBaseUrlSave = { viewModel.setCloudAiSettings(openAiCompatibleBaseUrl = it) },
            onOpenAiAuthModeChange = { viewModel.setCloudAiSettings(openAiCompatibleAuthMode = it) },
            onOpenAiProviderNameSave = { viewModel.setCloudAiSettings(openAiCompatibleProviderName = it) },
            onEndpointPresetSelect = viewModel::applyCloudAiEndpointPreset,
            onRefreshModels = viewModel::refreshCloudAiModels,
            onImageAnalysisChange = { viewModel.setCloudAiSettings(imageAnalysisEnabled = it) },
            onHealthInsightsChange = { viewModel.setCloudAiSettings(healthInsightsEnabled = it) },
            onWifiOnlyChange = { viewModel.setCloudAiSettings(wifiOnly = it) },
            onApiKeySave = viewModel::setCurrentCloudAiApiKey,
            onApiKeyClear = viewModel::clearCurrentCloudAiApiKey,
        )
    }
    }
}

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsWidgetsContent(
    context: Context,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    msgWidgetPinOem: String,
    msgWidgetPinOk: String,
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    SettingsCard(
    title = stringResource(R.string.settings_card_widgets),
    subtitle = stringResource(R.string.settings_group_widgets_desc),
    icon = MedLogIcons.Widgets,
    ) {
    val widgetManager = AppWidgetManager.getInstance(context)
    val canPin = widgetManager.isRequestPinAppWidgetSupported
    val oemNeedsPermission = OemWidgetHelper.requiresExtraPermission

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MedLogSpacing.Large)
            .padding(top = MedLogSpacing.Tiny, bottom = MedLogSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
    ) {
        if (!canPin) {
            // 桌面不支持直接固定时，显示手动添加引导
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(MedLogSpacing.Medium),
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MedLogIcon(
                        MedLogIcons.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        stringResource(R.string.settings_widget_no_pin_hint, OemWidgetHelper.manualAddGuidance(context)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        } else {
            // 可以固定——显示通用提示
            Text(
                stringResource(R.string.settings_widget_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // OEM 专属权限提醒（小米 / OPPO / vivo）
            if (oemNeedsPermission) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(MedLogSpacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                            verticalAlignment = Alignment.Top,
                        ) {
                            MedLogIcon(
                                MedLogIcons.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                            OemWidgetHelper.permissionNote(context),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        OutlinedButton(
                            onClick = { OemWidgetHelper.openPermissionSettings(context) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = MedLogSpacing.Medium, vertical = MedLogSpacing.Small),
                        ) {
                            MedLogIcon(
                                MedLogIcons.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(MedLogSpacing.Small))
                            Text(stringResource(R.string.settings_widget_oem_btn), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // 今日进度小组件显示模式开关（SSOT：与小组件长按设置共享同一 DataStore）
        SettingsSwitchRow(
            title    = stringResource(R.string.widget_settings_show_actions),
            subtitle = if (uiState.widgetShowActions)
                stringResource(R.string.widget_settings_show_actions_body)
            else
                stringResource(R.string.widget_settings_status_body),
            checked         = uiState.widgetShowActions,
            onCheckedChange = { viewModel.setWidgetShowActions(it) },
            icon            = MedLogIcons.TouchApp,
        )

        WidgetPreviewCarousel(
            items = listOf(
                WidgetCarouselItem(
                    previewType = WidgetPreviewType.TODAY,
                    name = stringResource(R.string.settings_widget_today_name),
                    description = stringResource(R.string.settings_widget_today_desc),
                    sizes = listOf("2×2", "4×2", "4×4"),
                    canPin = canPin,
                    showActions = uiState.widgetShowActions,
                    onAdd = {
                        if (canPin) {
                            widgetManager.requestPinAppWidget(
                                ComponentName(context, MedLogWidgetReceiver::class.java), null, null,
                            )
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (oemNeedsPermission) msgWidgetPinOem else msgWidgetPinOk,
                                    duration = SnackbarDuration.Long,
                                )
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    OemWidgetHelper.manualAddGuidance(context),
                                    duration = SnackbarDuration.Long,
                                )
                            }
                        }
                    },
                ),
                WidgetCarouselItem(
                    previewType = WidgetPreviewType.NEXT_DOSE,
                    name = stringResource(R.string.settings_widget_next_name),
                    description = stringResource(R.string.settings_widget_next_desc),
                    sizes = listOf("2×2", "4×2"),
                    canPin = canPin,
                    showActions = uiState.widgetShowActions,
                    onAdd = {
                        if (canPin) {
                            widgetManager.requestPinAppWidget(
                                ComponentName(context, NextDoseWidgetReceiver::class.java), null, null,
                            )
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (oemNeedsPermission) msgWidgetPinOem else msgWidgetPinOk,
                                    duration = SnackbarDuration.Long,
                                )
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    OemWidgetHelper.manualAddGuidance(context),
                                    duration = SnackbarDuration.Long,
                                )
                            }
                        }
                    },
                ),
                WidgetCarouselItem(
                    previewType = WidgetPreviewType.STREAK,
                    name = stringResource(R.string.settings_widget_streak_name),
                    description = stringResource(R.string.settings_widget_streak_desc),
                    sizes = listOf("2×2", "4×2"),
                    canPin = canPin,
                    onAdd = {
                        if (canPin) {
                            widgetManager.requestPinAppWidget(
                                ComponentName(context, StreakWidgetReceiver::class.java), null, null,
                            )
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (oemNeedsPermission) msgWidgetPinOem else msgWidgetPinOk,
                                    duration = SnackbarDuration.Long,
                                )
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    OemWidgetHelper.manualAddGuidance(context),
                                    duration = SnackbarDuration.Long,
                                )
                            }
                        }
                    },
                ),
            ),
        )
    }
    }
}

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
