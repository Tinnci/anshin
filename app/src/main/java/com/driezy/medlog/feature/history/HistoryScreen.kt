package com.driezy.medlog.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.ui.components.MedLogScreenScaffold
import com.driezy.medlog.ui.components.ScreenChromeState
import com.driezy.medlog.ui.components.ScreenFab
import com.driezy.medlog.ui.components.TopBarAction
import com.driezy.medlog.ui.components.TopBarActionPriority
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryScreen(onOpenSettings: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryContent(uiState, onOpenSettings, viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryContent(uiState: HistoryUiState, onOpenSettings: () -> Unit, onAction: (HistoryUiAction) -> Unit) {
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
            ),
        ),
        chromeState = ScreenChromeState(
            isLoading = uiState.isLoading,
            fab = ScreenFab(
                id = "today",
                label = stringResource(R.string.history_today_button),
                icon = MedLogIcons.Today,
            ),
        ),
        onChromeAction = { id ->
            when (id) {
                "settings" -> onOpenSettings()
                "today" -> onAction(HistoryUiAction.NavigateToToday)
            }
        },
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
                    today = uiState.today,
                    onNavigate = { onAction(HistoryUiAction.NavigateMonth(it)) },
                    onSelectDate = { onAction(HistoryUiAction.SelectDate(it)) },
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
                item(key = "detail_$selected") {
                    DayDetailSection(
                        date = selected,
                        day = selectedDay,
                        onEditTakenTime = { log, time ->
                            onAction(HistoryUiAction.EditTakenTime(log, time))
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}
