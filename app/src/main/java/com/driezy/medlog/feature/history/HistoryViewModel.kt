package com.driezy.medlog.feature.history

import androidx.lifecycle.viewModelScope
import com.driezy.medlog.data.model.LogRevisionType
import com.driezy.medlog.data.model.MedicationLog
import com.driezy.medlog.data.repository.LogRepository
import com.driezy.medlog.feature.medications.application.ObserveMedicationAdherence
import com.driezy.medlog.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

typealias AdherenceDay = com.driezy.medlog.feature.medications.application.AdherenceDay

data class HistoryUiState(
    /** Clock-derived current date used by both ViewModel and Content. */
    val today: LocalDate = LocalDate.ofEpochDay(0),
    /** 每日坚持率数据（近90天） */
    val calendarDays: Map<LocalDate, AdherenceDay> = emptyMap(),
    /** 当前展示月份 */
    val displayedMonth: YearMonth = YearMonth.of(1970, 1),
    /** 选中日期（默认选中今天，立即展示当日计划） */
    val selectedDate: LocalDate? = null,
    /** 总体坚持率（近30天） */
    val overallAdherence: Float = 0f,
    val taken30d: Int = 0,
    val partial30d: Int = 0,
    val total30d: Int = 0,
    /** 当前连续服药天数（从今天/昨天起，每天 ≥1 次 TAKEN 计入） */
    val currentStreak: Int = 0,
    /** 历史最长连续天数 */
    val longestStreak: Int = 0,
    val isLoading: Boolean = true,
    val error: Boolean = false,
    val zone: ZoneId = ZoneId.systemDefault(),
)

sealed interface HistoryUiAction {
    data object RefreshTime : HistoryUiAction
    data object NavigateToToday : HistoryUiAction
    data class NavigateMonth(val delta: Int) : HistoryUiAction
    data class SelectDate(val date: LocalDate) : HistoryUiAction
    data class EditTakenTime(val log: MedicationLog, val newTimeMs: Long) : HistoryUiAction
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val logRepo: LogRepository,
    private val observeAdherence: ObserveMedicationAdherence,
    private val clock: Clock,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(
        HistoryUiState(
            today = LocalDate.now(clock),
            displayedMonth = YearMonth.now(clock),
            selectedDate = LocalDate.now(clock),
        ),
    )
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val time = MutableStateFlow(clock.instant())
    private var zone = clock.zone

    companion object {
        /** 哨兵值：未知药品名，Compose UI 层用 stringResource 解析显示文本 */
        const val UNKNOWN_MEDICATION_NAME = "\u0000__unknown__"

        /** 日志与计划条目匹配的时间窗口（4 小时） */
        private const val SLOT_WINDOW_MS = 4 * 3_600_000L
    }

    init {
        loadData()
    }

    fun onAction(action: HistoryUiAction) {
        when (action) {
            HistoryUiAction.RefreshTime -> {
                time.value = clock.instant()
                if (_uiState.value.error) loadData()
            }
            HistoryUiAction.NavigateToToday -> navigateToToday()
            is HistoryUiAction.NavigateMonth -> navigateMonthBy(action.delta)
            is HistoryUiAction.SelectDate -> selectDate(action.date)
            is HistoryUiAction.EditTakenTime -> editTakenTime(action.log, action.newTimeMs)
        }
    }

    private var observation: Job? = null

    private fun loadData() {
        observation?.cancel()
        observation = viewModelScope.launch {
            observeAdherence(time)
                .catch { _uiState.update { it.copy(isLoading = false, error = true) } }
                .collect { summary ->
                    zone = summary.zone
                    _uiState.update {
                        it.copy(
                            today = summary.today,
                            zone = summary.zone,
                            calendarDays = summary.days,
                            overallAdherence = summary.rate30d,
                            taken30d = summary.taken30d, partial30d = summary.partial30d, total30d = summary.total30d,
                            currentStreak = summary.currentStreak,
                            longestStreak = summary.longestStreak,
                            isLoading = false,
                            error = false,
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

    fun navigateToToday() {
        val today = LocalDate.now(clock.withZone(zone))
        _uiState.update {
            it.copy(
                displayedMonth = YearMonth.from(today),
                selectedDate = today,
            )
        }
    }

    /**
     * 修改某次服药记录的实际服药时间。
     *
     * @param log    要修改的日志对象
     * @param newMs  新的实际服药时间戳（毫秒，UTC，来自设备时钟）
     *
     * 时区说明：所有时间戳均以 UTC 毫秒存储，显示时由 java.time 根据设备时区格式化。
     * 修改后数据库更新，日志页签由 Flow 自动刷新，无需手动触发。
     */
    fun editTakenTime(log: com.driezy.medlog.data.model.MedicationLog, newMs: Long) {
        safeLaunch(onError = { _uiState.update { it.copy(error = true) } }) {
            val now = clock.millis()
            val scheduledDate = Instant.ofEpochMilli(log.scheduledTimeMs)
                .atZone(zone)
                .toLocalDate()
            val revisionType = if (scheduledDate < LocalDate.now(clock)) {
                LogRevisionType.RETROACTIVE_EDIT
            } else {
                LogRevisionType.SAME_DAY_EDIT
            }
            logRepo.updateLog(
                log.copy(
                    actualTakenTimeMs = newMs,
                    updatedAtMs = now,
                    revisionType = revisionType,
                ),
            )
        }
    }
}
