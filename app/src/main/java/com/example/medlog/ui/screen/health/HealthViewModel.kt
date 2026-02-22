package com.example.medlog.ui.screen.health

import androidx.lifecycle.ViewModel
import com.example.medlog.ui.BaseViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.HealthRecord
import com.example.medlog.data.model.HealthType
import com.example.medlog.data.repository.HealthRepository
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
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val repository: HealthRepository,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()

    init {
        collectRecords()
        collectStats()
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
                .catch { }
                .collect { records ->
                    _uiState.update { it.copy(records = records, isLoading = false) }
                }
        }
    }

    /** 收集各类型最新记录用于统计卡片 */
    private fun collectStats() {
        viewModelScope.launch {
            // 7 天 range
            val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            combine(
                repository.getLatestRecordPerType(),
                repository.getRecordsInRange(sevenDaysAgo, System.currentTimeMillis()),
            ) { latest, week ->
                buildStats(latest, week)
            }
                .catch { }
                .collect { stats -> _uiState.update { it.copy(stats = stats) } }
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
                    when {
                        late - early > 0.5 -> 1
                        early - late > 0.5 -> -1
                        else -> 0
                    }
                }
                else -> null
            }
            HealthTypeStat(
                type = type,
                latestValue = rec.value,
                latestSecondary = rec.secondaryValue,
                latestTime = rec.timestamp,
                avg7d = avg,
                trend = trend,
                isAbnormal = !type.isNormal(rec.value),
            )
        }
    }

    // ── 用户操作 ─────────────────────────────────────────────────────────────

    fun selectType(type: HealthType?) = _uiState.update { it.copy(selectedType = type) }

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

    fun startEdit(record: HealthRecord) {
        _uiState.update {
            it.copy(
                showAddSheet = true,
                draft = HealthDraftState(
                    type = HealthType.fromName(record.type),
                    value = record.value.let { v ->
                        if (v == v.toLong().toDouble()) v.toLong().toString()
                        else "%.1f".format(v)
                    },
                    secondaryValue = record.secondaryValue?.let { v ->
                        if (v == v.toLong().toDouble()) v.toLong().toString()
                        else "%.1f".format(v)
                    } ?: "",
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
        viewModelScope.launch {
            if (draft.editingId == null) repository.addRecord(record)
            else repository.updateRecord(record)
            _uiState.update { it.copy(showAddSheet = false) }
        }
    }

    fun requestDelete(record: HealthRecord) = _uiState.update { it.copy(deleteTarget = record) }
    fun cancelDelete() = _uiState.update { it.copy(deleteTarget = null) }
    fun confirmDelete() {
        val target = _uiState.value.deleteTarget ?: return
        viewModelScope.launch {
            repository.deleteRecord(target)
            _uiState.update { it.copy(deleteTarget = null) }
        }
    }
}
