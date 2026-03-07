package com.example.medlog.ui.screen.history

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.medlog.ui.BaseViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.LogStatus
import com.example.medlog.data.model.Medication
import com.example.medlog.data.model.MedicationLog
import com.example.medlog.data.repository.LogRepository
import com.example.medlog.data.repository.MedicationRepository
import com.example.medlog.domain.FuturePlanCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.medlog.domain.NINETY_DAYS_MS
import com.example.medlog.domain.StreakCalculator
import com.example.medlog.domain.THIRTY_DAYS_MS
import java.time.Instant
import java.time.YearMonth
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** 单日坚持率汇总 */
data class AdherenceDay(
    val date: LocalDate,
    val taken: Int,
    val partial: Int,
    val total: Int,
    val logs: List<Pair<MedicationLog, String>>,  // log + 药名
) {
    /** 待服药条目数（仅 PENDING） */
    val pending: Int get() = logs.count { (log, _) -> log.status == LogStatus.PENDING }
    /** 已决定状态的条目数（排除 PENDING）— 用于日历颜色计算 */
    val resolved: Int get() = logs.count { (log, _) -> log.status != LogStatus.PENDING }
    /** 将 partial 按 0.5 权重计入合规率；仅对已决定（resolved）条目计算，PENDING 不影响颜色 */
    val rate: Float get() = if (resolved == 0) 0f else (taken + partial * 0.5f) / resolved.toFloat()
}

data class HistoryUiState(
    /** 每日坚持率数据（近90天） */
    val calendarDays: Map<LocalDate, AdherenceDay> = emptyMap(),
    /** 当前展示月份 */
    val displayedMonth: YearMonth = YearMonth.now(),
    /** 选中日期（默认选中今天，立即展示当日计划） */
    val selectedDate: LocalDate? = LocalDate.now(),
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
    private val futurePlanCalculator: FuturePlanCalculator,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val zone = ZoneId.systemDefault()

    companion object {
        /** 哨兵值：未知药品名，Compose UI 层用 stringResource 解析显示文本 */
        const val UNKNOWN_MEDICATION_NAME = "\u0000__unknown__"
        /** 日志与计划条目匹配的时间窗口（4 小时） */
        private const val SLOT_WINDOW_MS = 4 * 3_600_000L
    }

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            // 1. 加载所有药品（含归档，历史记录需要显示药名 + 计算计划条目）
            val allMedications = mutableListOf<Medication>()
            val medicationNames = mutableMapOf<Long, String>()
            combine(
                medicationRepo.getActiveMedications(),
                medicationRepo.getArchivedMedications(),
            ) { active, archived -> active + archived }
                .take(1)
                .catch { e -> Log.e("HistoryVM", "Failed to load medications", e) }
                .collect { meds ->
                    allMedications.addAll(meds)
                    meds.forEach { medicationNames[it.id] = it.name }
                }

            // 2. 用 FuturePlanCalculator 计算近90天的所有计划条目
            //    归档药品仅在设有 endDate 时参与计算（无 archivedAt 字段无法推断归档时间）
            val now = System.currentTimeMillis()
            val startMs = now - NINETY_DAYS_MS
            val medsForPlanning = allMedications.filter { med ->
                !med.isArchived || med.endDate != null
            }
            val plannedItems = futurePlanCalculator.calculate(
                medications = medsForPlanning,
                days = 91, // 含今天
                fromMs = startMs,
                includeArchived = true,
            )
            // 按日期分组计划条目
            val plannedByDay = plannedItems.groupBy { item ->
                Instant.ofEpochMilli(item.scheduledMs).atZone(zone).toLocalDate()
            }

            // 3. 加载近90天的日志，与计划合并
            //    endMs 延伸到今天 23:59:59，确保当日所有时间槽的日志都被覆盖
            val endOfToday = now - now % 86_400_000L + 86_400_000L - 1L
            logRepo.getLogsForDateRange(startMs, endOfToday)
                .catch { e -> Log.e("HistoryVM", "Failed to load medication logs", e) }
                .collect { logs ->
                    val logsByDay = logs.groupBy { log ->
                        Instant.ofEpochMilli(log.scheduledTimeMs)
                            .atZone(zone)
                            .toLocalDate()
                    }
                    val today = LocalDate.now()

                    // 合并计划条目与实际日志；始终包含今天（保证当日计划可见）
                    val allDates = (plannedByDay.keys + logsByDay.keys + setOf(today))
                        .filter { it <= today }
                    val calendarDays = allDates.associateWith { date ->
                        val dayLogs = logsByDay[date] ?: emptyList()
                        val dayPlanned = plannedByDay[date] ?: emptyList()
                        buildAdherenceDay(date, dayLogs, dayPlanned, today, medicationNames)
                    }

                    // 计算近30天坚持率（仅统计 resolved 条目，PENDING 不计入）
                    val thirtyDaysAgo = Instant.ofEpochMilli(now - THIRTY_DAYS_MS)
                        .atZone(zone).toLocalDate()
                    val recentDays = calendarDays.filterKeys { it >= thirtyDaysAgo }
                    val resolvedTotal = recentDays.values.sumOf { it.resolved }
                    val takenLogs = recentDays.values.sumOf { it.taken } +
                        recentDays.values.sumOf { it.partial } * 0.5
                    val overallAdherence = if (resolvedTotal == 0) 0f
                        else (takenLogs / resolvedTotal.toDouble()).toFloat()

                    // 计算连续打卡 streak（每天 taken >= 1 或 partial >= 1 即视为完成）
                    val activeDays = calendarDays.entries
                        .filter { it.value.taken > 0 || it.value.partial > 0 }
                        .map { it.key }
                        .toSet()
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

    /**
     * 将一天的计划条目与实际日志合并为 [AdherenceDay]。
     *
     * - 有匹配日志的计划条目 → 使用日志（TAKEN / SKIPPED / PARTIAL）
     * - 无匹配日志的过去计划 → 生成虚拟 MISSED 条目
     * - 无匹配日志的今天计划 → 生成虚拟 PENDING 条目
     * - 不在计划中但有日志的条目（如 PRN）→ 保留原始日志
     */
    private fun buildAdherenceDay(
        date: LocalDate,
        dayLogs: List<MedicationLog>,
        dayPlanned: List<com.example.medlog.domain.FuturePlanItem>,
        today: LocalDate,
        medicationNames: Map<Long, String>,
    ): AdherenceDay {
        val mergedLogs = mutableListOf<MedicationLog>()
        val matchedLogIds = mutableSetOf<Long>()

        for (plan in dayPlanned) {
            // 在实际日志中寻找匹配项（同一药品 + 计划时间 ±4 小时窗口）
            val matchedLog = dayLogs.firstOrNull { log ->
                log.medicationId == plan.medication.id &&
                    log.id !in matchedLogIds &&
                    kotlin.math.abs(log.scheduledTimeMs - plan.scheduledMs) < SLOT_WINDOW_MS
            }
            if (matchedLog != null) {
                mergedLogs.add(matchedLog)
                matchedLogIds.add(matchedLog.id)
            } else {
                // 无匹配日志：过去 = MISSED，今天 = PENDING
                mergedLogs.add(
                    MedicationLog(
                        id = -(plan.medication.id * 100 + plan.timeSlotIndex),
                        medicationId = plan.medication.id,
                        scheduledTimeMs = plan.scheduledMs,
                        status = if (date < today) LogStatus.MISSED else LogStatus.PENDING,
                    ),
                )
            }
        }

        // 保留不在计划中但有日志的条目（如 PRN 按需用药）
        for (log in dayLogs) {
            if (log.id !in matchedLogIds) {
                mergedLogs.add(log)
            }
        }

        val sorted = mergedLogs.sortedBy { it.scheduledTimeMs }
        return AdherenceDay(
            date = date,
            taken = sorted.count { it.status == LogStatus.TAKEN },
            partial = sorted.count { it.status == LogStatus.PARTIAL },
            total = sorted.size,
            logs = sorted.map { log ->
                log to (medicationNames[log.medicationId] ?: UNKNOWN_MEDICATION_NAME)
            },
        )
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
