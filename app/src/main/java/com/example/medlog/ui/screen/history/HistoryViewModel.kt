package com.example.medlog.ui.screen.history

import androidx.lifecycle.ViewModel
import com.example.medlog.ui.BaseViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.LogStatus
import com.example.medlog.data.model.MedicationLog
import com.example.medlog.data.repository.LogRepository
import com.example.medlog.data.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.medlog.domain.StreakCalculator
import java.time.Instant
import java.time.YearMonth
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** 单日坚持率汇总 */
data class AdherenceDay(
    val date: LocalDate,
    val taken: Int,
    val total: Int,
    val logs: List<Pair<MedicationLog, String>>,  // log + 药名
) {
    val rate: Float get() = if (total == 0) 0f else taken.toFloat() / total.toFloat()
}

data class HistoryUiState(
    /** 每日坚持率数据（近90天） */
    val calendarDays: Map<LocalDate, AdherenceDay> = emptyMap(),
    /** 当前展示月份 */
    val displayedMonth: YearMonth = YearMonth.now(),
    /** 选中日期 */
    val selectedDate: LocalDate? = null,
    /** 总体坚持率（近30天） */
    val overallAdherence: Float = 0f,
    /** 当前连续服药天数（从今天/昨天起，每天 ≥1 次 TAKEN 计入） */
    val currentStreak: Int = 0,
    /** 历史最长连续天数 */
    val longestStreak: Int = 0,
    val isLoading: Boolean = true,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val logRepo: LogRepository,
    private val medicationRepo: MedicationRepository,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val zone = ZoneId.systemDefault()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            // 1. 加载所有药品名称（含归档，历史记录需要显示药名）
            val medicationNames = mutableMapOf<Long, String>()
            combine(
                medicationRepo.getActiveMedications(),
                medicationRepo.getArchivedMedications(),
            ) { active, archived -> active + archived }
                .take(1)
                .catch { }
                .collect { meds -> meds.forEach { medicationNames[it.id] = it.name } }

            // 2. 加载近90天的日志，按日期聚合
            val now = System.currentTimeMillis()
            val startMs = now - 90L * 24 * 60 * 60 * 1000
            logRepo.getLogsForDateRange(startMs, now)
                .catch { }
                .collect { logs ->
                    val byDay = logs.groupBy { log ->
                        Instant.ofEpochMilli(log.scheduledTimeMs)
                            .atZone(zone)
                            .toLocalDate()
                    }
                    val calendarDays = byDay.mapValues { (date, dayLogs) ->
                        AdherenceDay(
                            date = date,
                            taken = dayLogs.count { it.status == LogStatus.TAKEN },
                            total = dayLogs.size,
                            logs = dayLogs
                                .sortedBy { it.scheduledTimeMs }
                                .map { log ->
                                    log to (medicationNames[log.medicationId] ?: "未知药品")
                                },
                        )
                    }

                    // 计算近30天坚持率
                    val thirtyDaysAgo = Instant.ofEpochMilli(now - 30L * 24 * 60 * 60 * 1000)
                        .atZone(zone).toLocalDate()
                    val recentDays = calendarDays.filterKeys { it >= thirtyDaysAgo }
                    val totalLogs = recentDays.values.sumOf { it.total }
                    val takenLogs = recentDays.values.sumOf { it.taken }
                    val overallAdherence = if (totalLogs == 0) 0f
                        else takenLogs.toFloat() / totalLogs.toFloat()

                    // 计算连续打卡 streak（每天 taken >= 1 即视为完成）
                    val activeDays = calendarDays.entries
                        .filter { it.value.taken > 0 }
                        .map { it.key }
                        .toSet()
                    val today = LocalDate.now()
                    val current = StreakCalculator.currentStreak(activeDays, today)
                    val longest = StreakCalculator.longestStreak(activeDays)

                    _uiState.update {
                        it.copy(
                            calendarDays = calendarDays,
                            overallAdherence = overallAdherence,
                            currentStreak = current,
                            longestStreak = longest,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = if (it.selectedDate == date) null else date) }
    }

    fun navigateMonthBy(delta: Int) {
        _uiState.update {
            it.copy(displayedMonth = it.displayedMonth.plusMonths(delta.toLong()))
        }
    }

    /**
     * 修改某次服药记录的实际服药时间。
     *
     * @param log    要修改的日志对象
     * @param newMs  新的实际服药时间戳（毫秒，UTC，来自设备时钟）
     *
     * 时区说明：所有时间戳均以 UTC 毫秒存储，显示时由 SimpleDateFormat 根据设备时区格式化。
     * 修改后数据库更新，日志页签由 Flow 自动刷新，无需手动触发。
     */
    fun editTakenTime(log: com.example.medlog.data.model.MedicationLog, newMs: Long) {
        viewModelScope.launch {
            logRepo.updateLog(log.copy(actualTakenTimeMs = newMs))
        }
    }
}
