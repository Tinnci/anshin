package com.driezy.medlog.ui.screen.addmedication

import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.driezy.medlog.ui.theme.emphasizedTypography
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.data.model.TimePeriod
import com.driezy.medlog.ui.util.icon
import com.driezy.medlog.ui.util.labelRes
import com.driezy.medlog.ui.util.formatDosePrecise
import java.text.SimpleDateFormat
import java.util.*




@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MedicationUsageFrequencySection(
    uiState: AddMedicationUiState,
    viewModel: AddMedicationViewModel,
) {
    val motionScheme = MaterialTheme.motionScheme
    // ── 按需用药 ─────────────────────────────────────────
    FormSection(title = stringResource(R.string.add_section_usage), icon = MedLogIcons.EventRepeat) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small)) {
                MedLogIcon(MedLogIcons.HourglassBottom, null, tint = MaterialTheme.colorScheme.secondary)
                Column {
                    Text(stringResource(R.string.add_prn_label), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.add_prn_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = uiState.isPRN, onCheckedChange = viewModel::onIsPRNChange)
        }
        AnimatedVisibility(
            visible = uiState.isPRN,
            enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            OutlinedTextField(
                value = uiState.maxDailyDose,
                onValueChange = viewModel::onMaxDailyDoseChange,
                label = { Text(stringResource(R.string.add_max_daily_dose)) },
                modifier = Modifier.fillMaxWidth().padding(top = MedLogSpacing.Small),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text(stringResource(R.string.add_times_per_day)) },
                singleLine = true,
            )
        }

        // ── 服药频率（非PRN才显示）──────────────────────
        AnimatedVisibility(
            visible = !uiState.isPRN,
            enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium)) {
                HorizontalDivider(Modifier.padding(vertical = MedLogSpacing.Tiny))
                Text(stringResource(R.string.add_freq_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val freqOptions = listOf("daily" to stringResource(R.string.add_freq_daily), "interval" to stringResource(R.string.add_freq_interval), "specific_days" to stringResource(R.string.add_freq_specific))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    freqOptions.forEachIndexed { index, (key, label) ->
                        ToggleButton(
                            checked = uiState.frequencyType == key,
                            onCheckedChange = { viewModel.onFrequencyTypeChange(key) },
                            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                freqOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) {
                            Text(label)
                        }
                    }
                }
                AnimatedVisibility(
                    visible = uiState.frequencyType == "interval",
                    enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small)) {
                        Text(stringResource(R.string.add_freq_every_n), style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = uiState.frequencyInterval.toString(),
                            onValueChange = { viewModel.onFrequencyIntervalChange(it.toIntOrNull() ?: 1) },
                            modifier = Modifier.width(80.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                        Text(stringResource(R.string.add_freq_interval_suffix), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                AnimatedVisibility(
                    visible = uiState.frequencyType == "specific_days",
                    enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
                ) {
                    val days = uiState.frequencyDays.split(",").filter { it.isNotBlank() }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                    ) {
                        val weekLabels = listOf(
                            stringResource(R.string.history_weekday_1) to 1,
                            stringResource(R.string.history_weekday_2) to 2,
                            stringResource(R.string.history_weekday_3) to 3,
                            stringResource(R.string.history_weekday_4) to 4,
                            stringResource(R.string.history_weekday_5) to 5,
                            stringResource(R.string.history_weekday_6) to 6,
                            stringResource(R.string.history_weekday_7) to 7,
                        )
                        weekLabels.forEach { (label, day) ->
                            FilterChip(
                                selected = days.contains(day.toString()),
                                onClick = { viewModel.toggleFrequencyDay(day) },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }

}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MedicationReminderScheduleSection(
    uiState: AddMedicationUiState,
    enableTimePeriodMode: Boolean,
    viewModel: AddMedicationViewModel,
) {
    val motionScheme = MaterialTheme.motionScheme
    // ── 服药时段 & 提醒（PRN 时作为可选提醒时间）─────────────────
    FormSection(
        title = if (uiState.isPRN) stringResource(R.string.add_section_reminder_optional) else stringResource(R.string.add_section_time_period),
        icon = MedLogIcons.Schedule,
    ) {
        Text(
            text = if (uiState.isPRN)
                stringResource(R.string.add_prn_reminder_hint)
            else
                stringResource(R.string.add_select_reminder_mode),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // ── 精确时间 / 作息时间 模式切换 ─────────────────────────
        // 若用户在设置中关闭了作息时间段模式，强制为精确时间模式
        val isExactMode = !enableTimePeriodMode || uiState.timePeriod == TimePeriod.EXACT
        AnimatedVisibility(
            visible = enableTimePeriodMode,
            enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            val periodLabel = stringResource(R.string.add_time_period_mode)
            val exactLabel = stringResource(R.string.add_exact_time_mode)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                ToggleButton(
                    checked = !isExactMode,
                    onCheckedChange = { viewModel.onTimePeriodChange(TimePeriod.MORNING) },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                ) {
                    MedLogIcon(MedLogIcons.WbSunny, null, Modifier.size(16.dp))
                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                    Text(periodLabel)
                }
                ToggleButton(
                    checked = isExactMode,
                    onCheckedChange = { viewModel.onTimePeriodChange(TimePeriod.EXACT) },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                ) {
                    MedLogIcon(MedLogIcons.Schedule, null, Modifier.size(16.dp))
                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                    Text(exactLabel)
                }
            }
        }
        // 作息时间模式：时段选择芯片 + 自动提醒时间提示
        AnimatedVisibility(
            visible = !isExactMode,
            enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small)) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                ) {
                    TimePeriod.entries.filter { it != TimePeriod.EXACT }.forEach { tp ->
                        FilterChip(
                            selected = tp == uiState.timePeriod,
                            onClick = { viewModel.onTimePeriodChange(tp) },
                            label = { Text(stringResource(tp.labelRes), style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                MedLogIcon(tp.icon, null, Modifier.size(FilterChipDefaults.IconSize))
                            },
                        )
                    }
                }
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            stringResource(R.string.add_reminder_hint_format, uiState.reminderTimes.firstOrNull().orEmpty()),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    icon = {
                        MedLogIcon(
                            MedLogIcons.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(SuggestionChipDefaults.IconSize),
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.secondary,
                    ),
                )
            }
        }
        // 精确时间模式：用户手动设置多个提醒时间 + 可选间隔给药
        AnimatedVisibility(
            visible = isExactMode,
            enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small)) {
                ReminderTimesRow(
                    times = uiState.reminderTimes,
                    onAdd = viewModel::addReminderTime,
                    onRemove = viewModel::removeReminderTime,
                )
                // 间隔给药开关（适用于旅行跨时区、抗生素等需精确间隔的场景）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                    ) {
                        MedLogIcon(
                            MedLogIcons.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column {
                            Text(stringResource(R.string.add_interval_dosing), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.add_interval_dosing_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = uiState.intervalHours > 0,
                        onCheckedChange = { on ->
                            viewModel.onIntervalHoursChange(if (on) 8 else 0)
                        },
                    )
                }
                AnimatedVisibility(
                    visible = uiState.intervalHours > 0,
                    enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
                ) {
                    OutlinedTextField(
                        value = if (uiState.intervalHours > 0) uiState.intervalHours.toString() else "",
                        onValueChange = { v -> v.toIntOrNull()?.let { viewModel.onIntervalHoursChange(it) } },
                        label = { Text(stringResource(R.string.add_interval_hours_label)) },
                        suffix = { Text(stringResource(R.string.add_interval_hours_unit)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text(stringResource(R.string.add_interval_hours_hint)) },
                    )
                }
            }
        }
    }

}
