package com.driezy.medlog.ui.screen.history

import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.driezy.medlog.ui.theme.emphasizedTypography
import com.driezy.medlog.ui.theme.MedLogSpacing
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.data.model.MedicationLog
import com.driezy.medlog.ui.components.MedLogScreenScaffold
import com.driezy.medlog.ui.components.ScreenChromeState
import com.driezy.medlog.ui.components.ScreenFab
import com.driezy.medlog.ui.components.TopBarAction
import com.driezy.medlog.ui.components.TopBarActionPriority
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryScreen(
    onOpenSettings: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MedLogScreenScaffold(
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
        actions = listOf(
            TopBarAction(
                id = "settings",
                label = stringResource(R.string.settings_action_open),
                icon = MedLogIcons.Settings,
                priority = TopBarActionPriority.Secondary,
                onClick = onOpenSettings,
            ),
        ),
        chromeState = ScreenChromeState(
            isLoading = uiState.isLoading,
            fab = ScreenFab(
                label = stringResource(R.string.history_today_button),
                icon = MedLogIcons.Today,
                onClick = viewModel::navigateToToday,
            ),
        ),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = MedLogSpacing.ScreenContentWithFab,
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            // 近30天坚持率概览
            item {
                AdherenceOverviewCard(
                    adherence = uiState.overallAdherence,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            // 连续打卡 streak 卡片（streak > 0 时展示）
            if (uiState.currentStreak > 0 || uiState.longestStreak > 0) {
                item {
                    StreakCard(
                        currentStreak = uiState.currentStreak,
                        longestStreak = uiState.longestStreak,
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
                )
            }

            // 图例说明
            item {
                LegendRow(modifier = Modifier.padding(vertical = 4.dp))
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
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}
