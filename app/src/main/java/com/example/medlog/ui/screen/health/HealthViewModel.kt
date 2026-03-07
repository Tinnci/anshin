package com.example.medlog.ui.screen.health

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.medlog.ui.BaseViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.HealthRecord
import com.example.medlog.data.model.HealthType
import com.example.medlog.data.repository.HealthRepository
import com.example.medlog.data.repository.UserPreferencesRepository
import com.example.medlog.domain.SEVEN_DAYS_MS
import com.example.medlog.ui.ocr.ParsedHealthMetric
import com.example.medlog.ui.util.formatDose
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

// ─── Draft 状态（新增 / 编辑底部表单） ───────────────────────────────────────

data class HealthDraftState(
    val type: HealthType = HealthType.BLOOD_PRESSURE,
    /** 主值字符串（未验证） */
    val value: String = "",
    /** 次值字符串（仅血压舒张压） */
    val secondaryValue: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = "",
    /** 编辑时非空 */
    val editingId: Long? = null,
)

// ─── 统计摘要 ────────────────────────────────────────────────────────────────

data class HealthTypeStat(
    val type: HealthType,
    val latestValue: Double,
    val latestSecondary: Double?,
    val latestTime: Long,
    val avg7d: Double?,
    /** 趋势：+1 上升 / -1 下降 / 0 平稳 / null 数据不足 */
    val trend: Int?,
    val isAbnormal: Boolean,
    /** 血压分类 StringRes（仅 BLOOD_PRESSURE 类型使用） */
    val bpClassRes: Int? = null,
)

// ─── 主 UI 状态 ──────────────────────────────────────────────────────────────

data class HealthUiState(
    val selectedType: HealthType? = null,    // null = 全部
    val records: List<HealthRecord> = emptyList(),
    val stats: List<HealthTypeStat> = emptyList(),
    val showAddSheet: Boolean = false,
    val draft: HealthDraftState = HealthDraftState(),
    val isLoading: Boolean = true,
    val deleteTarget: HealthRecord? = null,  // 待确认删除的记录
    /** 图表数据点：按时间正序排列的 (timestamp, value, secondaryValue?) */
    val chartPoints: List<HealthRecord> = emptyList(),
    /** BMI（体重 + 身高可用时计算） */
    val bmi: Double? = null,
    val bmiClassRes: Int? = null,
    /** 用户身高（cm），0 = 未设置 */
    val userHeightCm: Float = 0f,
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val repository: HealthRepository,
    private val prefsRepository: UserPreferencesRepository,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()

    /** 用户身高（cm），从偏好设置读取 */
    private val heightCmFlow = prefsRepository.settingsFlow
        .map { it.userHeightCm }
        .distinctUntilChanged()

    init {
        collectRecords()
        collectStats()
        collectChartData()
    }

    /** 收集过滤后的记录列表 */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun collectRecords() {
        viewModelScope.launch {
            _uiState
                .map { it.selectedType }
                .distinctUntilChanged()
                .flatMapLatest { type ->
                    if (type == null) repository.getAllRecords()
                    else repository.getRecordsByType(type.name)
                }
                .catch { e -> Log.e("HealthVM", "Failed to collect health records", e) }
                .collect { records ->
                    _uiState.update { it.copy(records = records, isLoading = false) }
                }
        }
    }

    /** 收集各类型最新记录用于统计卡片 + BMI */
    private fun collectStats() {
        viewModelScope.launch {
            val sevenDaysAgo = System.currentTimeMillis() - SEVEN_DAYS_MS
            combine(
                repository.getLatestRecordPerType(),
                repository.getRecordsInRange(sevenDaysAgo, System.currentTimeMillis()),
                heightCmFlow,
            ) { latest, week, heightCm ->
                val stats = buildStats(latest, week)
                val weightStat = stats.firstOrNull { it.type == HealthType.WEIGHT }
                val bmi = if (weightStat != null && heightCm > 0f) {
                    HealthType.calculateBmi(weightStat.latestValue, heightCm.toDouble())
                } else null
                Triple(stats, bmi, bmi?.let { HealthType.classifyBmi(it) })
            }
                .catch { e -> Log.e("HealthVM", "Failed to collect health stats", e) }
                .collect { (stats, bmi, bmiClassRes) ->
                    _uiState.update { it.copy(stats = stats, bmi = bmi, bmiClassRes = bmiClassRes) }
                }
        }
        // 单独收集身高到 UI 状态
        viewModelScope.launch {
            heightCmFlow.collect { h ->
                _uiState.update { it.copy(userHeightCm = h) }
            }
        }
    }

    /** 收集选中类型的近 30 天图表数据 */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun collectChartData() {
        viewModelScope.launch {
            _uiState
                .map { it.selectedType }
                .distinctUntilChanged()
                .flatMapLatest { type ->
                    if (type == null) flowOf(emptyList())
                    else {
                        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                        repository.getRecordsByTypeInRange(
                            type.name, thirtyDaysAgo, System.currentTimeMillis()
                        )
                    }
                }
                .catch { e -> Log.e("HealthVM", "Failed to collect chart data", e) }
                .collect { points ->
                    _uiState.update { it.copy(chartPoints = points) }
                }
        }
    }

    private fun buildStats(
        latest: List<HealthRecord>,
        weekRecords: List<HealthRecord>,
    ): List<HealthTypeStat> {
        val latestMap = latest.associateBy { HealthType.fromName(it.type) }
        val weekByType = weekRecords.groupBy { HealthType.fromName(it.type) }

        return HealthType.entries.mapNotNull { type ->
            val rec = latestMap[type] ?: return@mapNotNull null
            val weekValues = weekByType[type]?.map { it.value } ?: emptyList()
            val avg = if (weekValues.size >= 2) weekValues.average() else null
            val trend: Int? = when {
                weekValues.size >= 2 -> {
                    val half = weekValues.size / 2
                    val early = weekValues.take(half).average()
                    val late = weekValues.drop(half).average()
                    val threshold = type.trendThreshold
                    when {
                        late - early > threshold -> 1
                        early - late > threshold -> -1
                        else -> 0
                    }
                }
                else -> null
            }
            // 血压分类
            val bpClass = if (type == HealthType.BLOOD_PRESSURE && rec.secondaryValue != null) {
                HealthType.classifyBloodPressure(rec.value, rec.secondaryValue)
            } else null

            HealthTypeStat(
                type = type,
                latestValue = rec.value,
                latestSecondary = rec.secondaryValue,
                latestTime = rec.timestamp,
                avg7d = avg,
                trend = trend,
                isAbnormal = !type.isNormal(rec.value),
                bpClassRes = bpClass,
            )
        }
    }

    // ── 用户操作 ─────────────────────────────────────────────────────────────

    fun selectType(type: HealthType?) = _uiState.update { it.copy(selectedType = type) }

    fun updateHeight(heightCm: Float) = safeLaunch {
        prefsRepository.updateUserHeight(heightCm)
    }

    fun startAdd() {
        _uiState.update {
            it.copy(
                showAddSheet = true,
                draft = HealthDraftState(
                    type = it.selectedType ?: HealthType.BLOOD_PRESSURE,
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** 从 OCR 解析结果自动填充草稿并打开表单 */
    fun applyOcrMetric(metric: ParsedHealthMetric) {
        // value == 0.0 表示用户选择了原始文本行（无数字可自动填充）
        val valueStr = if (metric.value == 0.0) "" else {
            if (metric.value == metric.value.toLong().toDouble()) {
                metric.value.toLong().toString()
            } else {
                metric.value.toString()
            }
        }
        val secondaryStr = metric.secondaryValue?.let {
            if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
        } ?: ""
        _uiState.update {
            it.copy(
                showAddSheet = true,
                draft = HealthDraftState(
                    type = metric.type,
                    value = valueStr,
                    secondaryValue = secondaryStr,
                    timestamp = System.currentTimeMillis(),
                    notes = metric.rawText,
                ),
            )
        }
    }

    fun startEdit(record: HealthRecord) {
        _uiState.update {
            it.copy(
                showAddSheet = true,
                draft = HealthDraftState(
                    type = HealthType.fromName(record.type),
                    value = record.value.formatDose(),
                    secondaryValue = record.secondaryValue?.formatDose() ?: "",
                    timestamp = record.timestamp,
                    notes = record.notes,
                    editingId = record.id,
                ),
            )
        }
    }

    fun dismissSheet() = _uiState.update { it.copy(showAddSheet = false) }

    fun onDraftTypeChange(type: HealthType) = _uiState.update { s ->
        s.copy(draft = s.draft.copy(type = type, value = "", secondaryValue = ""))
    }
    fun onDraftValueChange(v: String) = _uiState.update { s -> s.copy(draft = s.draft.copy(value = v)) }
    fun onDraftSecondaryChange(v: String) = _uiState.update { s -> s.copy(draft = s.draft.copy(secondaryValue = v)) }
    fun onDraftTimeChange(ts: Long) = _uiState.update { s -> s.copy(draft = s.draft.copy(timestamp = ts)) }
    fun onDraftNotesChange(v: String) = _uiState.update { s -> s.copy(draft = s.draft.copy(notes = v)) }

    fun saveRecord() {
        val draft = _uiState.value.draft
        val value = draft.value.toDoubleOrNull() ?: return
        val secondary = if (draft.type == HealthType.BLOOD_PRESSURE) {
            draft.secondaryValue.toDoubleOrNull()
        } else null
        val record = HealthRecord(
            id             = draft.editingId ?: 0,
            type           = draft.type.name,
            value          = value,
            secondaryValue = secondary,
            timestamp      = draft.timestamp,
            notes          = draft.notes,
        )
        safeLaunch {
            if (draft.editingId == null) repository.addRecord(record)
            else repository.updateRecord(record)
            _uiState.update { it.copy(showAddSheet = false) }
        }
    }

    fun requestDelete(record: HealthRecord) = _uiState.update { it.copy(deleteTarget = record) }
    fun cancelDelete() = _uiState.update { it.copy(deleteTarget = null) }
    fun confirmDelete() {
        val target = _uiState.value.deleteTarget ?: return
        safeLaunch {
            repository.deleteRecord(target)
            _uiState.update { it.copy(deleteTarget = null) }
        }
    }
}
