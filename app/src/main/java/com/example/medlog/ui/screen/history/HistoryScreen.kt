package com.example.medlog.ui.screen.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medlog.R
import com.example.medlog.data.model.LogStatus
import com.example.medlog.data.model.MedicationLog
import com.example.medlog.ui.theme.calendarWarning
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.history_title))
                        if (!uiState.isLoading) {
                            Text(
                                stringResource(R.string.history_adherence_header, (uiState.overallAdherence * 100).toInt()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // 近30天坚持率概览
            item {
                AdherenceOverviewCard(
                    adherence = uiState.overallAdherence,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            // 连续打卡 streak 卡片（streak > 0 时展示）
            if (uiState.currentStreak > 0 || uiState.longestStreak > 0) {
                item {
                    StreakCard(
                        currentStreak = uiState.currentStreak,
                        longestStreak = uiState.longestStreak,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
                    )
                }
            }

            // 月历导航 + 日历
            item {
                MonthCalendarCard(
                    displayedMonth = uiState.displayedMonth,
                    calendarDays = uiState.calendarDays,
                    selectedDate = uiState.selectedDate,
                    today = LocalDate.now(),
                    onNavigate = viewModel::navigateMonthBy,
                    onSelectDate = viewModel::selectDate,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // 图例说明
            item {
                LegendRow(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            // 选中日期的详细日志
            val selected = uiState.selectedDate
            val selectedDay = selected?.let { uiState.calendarDays[it] }
            if (selected != null) {
                item(key = "detail_${selected}") {
                    DayDetailSection(
                        date = selected,
                        day = selectedDay,
                        onEditTakenTime = viewModel::editTakenTime,
                        modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

// ─── 近30天坚持率概览卡 ──────────────────────────────────────

@Composable
private fun AdherenceOverviewCard(adherence: Float, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val adherenceColor by animateColorAsState(
        targetValue = when {
            adherence >= 0.9f -> colorScheme.tertiary
            adherence >= 0.6f -> calendarWarning  // 琅珀色
            else              -> colorScheme.error
        },
        animationSpec = tween(600),
        label = "adherenceColor",
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 圆形进度
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { adherence },
                    modifier = Modifier.size(64.dp),
                    color = adherenceColor,
                    trackColor = adherenceColor.copy(alpha = 0.15f),
                    strokeWidth = 6.dp,
                )
                Text(
                    "${(adherence * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = adherenceColor,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.history_adherence_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        adherence >= 0.9f -> stringResource(R.string.history_adherence_excellent)
                        adherence >= 0.75f -> stringResource(R.string.history_adherence_good)
                        adherence >= 0.5f  -> stringResource(R.string.history_adherence_fair)
                        else               -> stringResource(R.string.history_adherence_poor)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── 月历卡 ──────────────────────────────────────────────────

@Composable
private fun MonthCalendarCard(
    displayedMonth: YearMonth,
    calendarDays: Map<LocalDate, AdherenceDay>,
    selectedDate: LocalDate?,
    today: LocalDate,
    onNavigate: (Int) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 月份导航栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onNavigate(-1) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.history_prev_month_cd))
                }
                Text(
                    stringResource(R.string.history_month_format, displayedMonth.year, displayedMonth.monthValue),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(
                    onClick = { onNavigate(1) },
                    enabled = displayedMonth < YearMonth.now(),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(R.string.history_next_month_cd))
                }
            }

            // 周标题（中国习惯：周一开始）
            Row(modifier = Modifier.fillMaxWidth()) {
                val weekDays = listOf(
                    stringResource(R.string.history_weekday_1),
                    stringResource(R.string.history_weekday_2),
                    stringResource(R.string.history_weekday_3),
                    stringResource(R.string.history_weekday_4),
                    stringResource(R.string.history_weekday_5),
                    stringResource(R.string.history_weekday_6),
                    stringResource(R.string.history_weekday_7),
                )
                weekDays.forEachIndexed { index, label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (index == 6) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // 日历格（周一开始：Mon=0, ..., Sun=6）
            val firstDay = displayedMonth.atDay(1)
            // dayOfWeek: 1=Mon..7=Sun → 周一对应 col 0
            val startOffset = firstDay.dayOfWeek.value - 1  // Mon=0, ..., Sun=6
            val daysInMonth = displayedMonth.lengthOfMonth()
            val totalCells = startOffset + daysInMonth
            val rows = (totalCells + 6) / 7

            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0..6) {
                        val cellIndex = row * 7 + col
                        val dayNum = cellIndex - startOffset + 1
                        if (dayNum < 1 || dayNum > daysInMonth) {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val date = displayedMonth.atDay(dayNum)
                            val adherenceDay = calendarDays[date]
                            val isSelected = date == selectedDate
                            val isToday = date == today
                            val isFuture = date > today
                            DayCell(
                                dayNum = dayNum,
                                adherenceDay = adherenceDay,
                                isSelected = isSelected,
                                isToday = isToday,
                                isFuture = isFuture,
                                onClick = { if (!isFuture) onSelectDate(date) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    dayNum: Int,
    adherenceDay: AdherenceDay?,
    isSelected: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected   -> colorScheme.primary
            isFuture || adherenceDay == null -> Color.Transparent
            adherenceDay.total == 0          -> Color.Transparent
            adherenceDay.rate >= 1f          -> colorScheme.tertiary.copy(alpha = 0.8f)
            adherenceDay.rate >= 0.5f        -> calendarWarning.copy(alpha = 0.7f)
            else                             -> colorScheme.error.copy(alpha = 0.7f)
        },
        animationSpec = tween(300),
        label = "dayCellBg",
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isSelected                          -> colorScheme.onPrimary
            isFuture                            -> colorScheme.onSurface.copy(alpha = 0.35f)
            adherenceDay != null && adherenceDay.total > 0 -> Color.White
            isToday                             -> colorScheme.primary
            else                                -> colorScheme.onSurface
        },
        label = "dayCellText",
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(bgColor)
            .then(
                if (isToday && !isSelected)
                    Modifier.border(1.5.dp, colorScheme.primary, CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = dayNum.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            color = textColor,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

// ─── 图例 ────────────────────────────────────────────────────

@Composable
private fun LegendRow(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(color = colorScheme.tertiary, label = stringResource(R.string.history_legend_all))
        LegendItem(color = calendarWarning, label = stringResource(R.string.history_legend_partial))
        LegendItem(color = colorScheme.error, label = stringResource(R.string.history_missed))
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─── 选中日期详情 ─────────────────────────────────────────────

@Composable
private fun DayDetailSection(
    date: LocalDate,
    day: AdherenceDay?,
    onEditTakenTime: (MedicationLog, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.history_date_format, date.monthValue, date.dayOfMonth,
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINESE)
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (day != null && day.total > 0) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                pluralStringResource(R.plurals.history_taken_count, day.taken, day.taken, day.total),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }

            if (day == null || day.total == 0) {
                Text(
                    stringResource(R.string.history_no_plan),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            } else {
                HorizontalDivider(color = colorScheme.outlineVariant)
                day.logs.forEach { (log, name) ->
                    DayLogRow(log = log, medicationName = name, onEditTakenTime = onEditTakenTime)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayLogRow(
    log: MedicationLog,
    medicationName: String,
    onEditTakenTime: (MedicationLog, Long) -> Unit = { _, _ -> },
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val colorScheme = MaterialTheme.colorScheme
    val takenLabel = stringResource(R.string.history_taken)
    val skippedLabel = stringResource(R.string.history_skipped)
    val missedLabel = stringResource(R.string.history_missed)
    val unrecordedLabel = stringResource(R.string.history_time_unrecorded)

    // 时间戳编辑对话框状态
    var showTimePicker by remember { mutableStateOf(false) }
    val takenCal = remember(log.actualTakenTimeMs) {
        Calendar.getInstance().apply {
            timeInMillis = log.actualTakenTimeMs ?: System.currentTimeMillis()
        }
    }
    val timePickerState = rememberTimePickerState(
        initialHour = takenCal.get(Calendar.HOUR_OF_DAY),
        initialMinute = takenCal.get(Calendar.MINUTE),
        is24Hour = true,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = when (log.status) {
                LogStatus.TAKEN   -> Icons.Rounded.CheckCircle
                LogStatus.SKIPPED -> Icons.Rounded.SkipNext
                LogStatus.MISSED  -> Icons.Rounded.Cancel
            },
            contentDescription = null,
            tint = when (log.status) {
                LogStatus.TAKEN   -> colorScheme.tertiary
                LogStatus.SKIPPED -> colorScheme.outline
                LogStatus.MISSED  -> colorScheme.error
            },
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(medicationName, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.history_scheduled_time, timeFmt.format(Date(log.scheduledTimeMs))),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
        Text(
            when (log.status) {
                LogStatus.TAKEN   -> log.actualTakenTimeMs?.let { stringResource(R.string.history_taken_time, timeFmt.format(Date(it))) } ?: takenLabel
                LogStatus.SKIPPED -> skippedLabel
                LogStatus.MISSED  -> missedLabel
            },
            style = MaterialTheme.typography.labelSmall,
            color = when (log.status) {
                LogStatus.TAKEN   -> colorScheme.tertiary
                LogStatus.SKIPPED -> colorScheme.outline
                LogStatus.MISSED  -> colorScheme.error
            },
            modifier = if (log.status == LogStatus.TAKEN)
                Modifier.clickable { showTimePicker = true }
            else Modifier,
        )
    }

    // 时间戳编辑对话框
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.history_edit_time_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.history_current_time_format, log.actualTakenTimeMs?.let { timeFmt.format(Date(it)) } ?: unrecordedLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // 将选择的 HH:mm 合并到原日期的时间戳
                    val base = Calendar.getInstance().apply {
                        timeInMillis = log.actualTakenTimeMs ?: log.scheduledTimeMs
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onEditTakenTime(log, base.timeInMillis)
                    showTimePicker = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

// ─── 连续打卡 Streak 卡片 ────────────────────────────────────────────────────

@Composable
private fun StreakCard(
    currentStreak: Int,
    longestStreak: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = pluralStringResource(R.plurals.history_streak_count, currentStreak, currentStreak),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.history_streak_title_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                )
            }
            if (longestStreak > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.history_streak_max_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f),
                    )
                    Text(
                        text = pluralStringResource(R.plurals.history_streak_max_days, longestStreak, longestStreak),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
