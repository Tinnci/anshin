package com.driezy.medlog.ui.screen.health

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.driezy.medlog.ai.AiApiKeyStore
import com.driezy.medlog.data.model.AiUsageFeature
import com.driezy.medlog.data.model.HealthRecord
import com.driezy.medlog.data.model.HealthRecordSource
import com.driezy.medlog.data.model.HealthType
import com.driezy.medlog.data.model.ParsedHealthMetric
import com.driezy.medlog.data.repository.HealthRepository
import com.driezy.medlog.data.repository.UserPreferencesRepository
import com.driezy.medlog.domain.SEVEN_DAYS_MS
import com.driezy.medlog.domain.health.AiExecutionStatus
import com.driezy.medlog.domain.health.HealthInsight
import com.driezy.medlog.domain.health.HealthInsightGenerationUseCase
import com.driezy.medlog.ui.BaseViewModel
import com.driezy.medlog.ui.util.formatDose
import com.driezy.medlog.voice.VoiceInputController
import com.driezy.medlog.voice.VoiceInputEvent
import com.driezy.medlog.voice.VoiceInputPhase
import com.driezy.medlog.voice.VoiceInputUiState
import com.driezy.medlog.voice.VoiceTranscriptAppender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val source: HealthRecordSource = HealthRecordSource.MANUAL,
    val sourceFeature: AiUsageFeature? = null,
    val sourceProvider: String? = null,
    val sourceModel: String? = null,
    val sourceConfidence: Float? = null,
    val sourceCacheKey: String? = null,
    val confirmedAt: Long? = null,
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
    val selectedType: HealthType? = null, // null = 全部
    val records: List<HealthRecord> = emptyList(),
    val stats: List<HealthTypeStat> = emptyList(),
    val showAddSheet: Boolean = false,
    val draft: HealthDraftState = HealthDraftState(),
    val isLoading: Boolean = true,
    val deleteTarget: HealthRecord? = null, // 待确认删除的记录
    /** 图表数据点：按时间正序排列的 (timestamp, value, secondaryValue?) */
    val chartPoints: List<HealthRecord> = emptyList(),
    /** BMI（体重 + 身高可用时计算） */
    val bmi: Double? = null,
    val bmiClassRes: Int? = null,
    /** 用户身高（cm），0 = 未设置 */
    val userHeightCm: Float = 0f,
    /** 后台智能聚合生成的健康建议，不需要用户输入对话 */
    val insights: List<HealthInsight> = emptyList(),
    val isInsightRefreshing: Boolean = false,
    /** AI 执行/回退内部状态，普通用户界面不显著展示。 */
    val insightExecutionStatus: AiExecutionStatus = AiExecutionStatus.LocalOnly,
    val voiceInput: VoiceInputUiState = VoiceInputUiState(),
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val repository: HealthRepository,
    private val prefsRepository: UserPreferencesRepository,
    private val insightGeneration: HealthInsightGenerationUseCase,
    private val apiKeyStore: AiApiKeyStore,
    private val voiceInputController: VoiceInputController,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()
    private var acceptsVoiceInput = false
    private var transcriptAppender: VoiceTranscriptAppender? = null

    /** 用户身高（cm），从偏好设置读取 */
    private val heightCmFlow = prefsRepository.settingsFlow
        .map { it.userHeightCm }
        .distinctUntilChanged()

    init {
        collectRecords()
        collectStats()
        collectChartData()
        collectInsights()
        collectVoiceInput()
    }

    private fun collectVoiceInput() {
        viewModelScope.launch {
            voiceInputController.events.collect { event ->
                if (!acceptsVoiceInput) return@collect
                when (event) {
                    VoiceInputEvent.Connecting -> _uiState.update {
                        it.copy(voiceInput = VoiceInputUiState(VoiceInputPhase.CONNECTING))
                    }
                    VoiceInputEvent.Listening -> {
                        transcriptAppender = VoiceTranscriptAppender(_uiState.value.draft.notes)
                        _uiState.update { it.copy(voiceInput = VoiceInputUiState(VoiceInputPhase.LISTENING)) }
                    }
                    VoiceInputEvent.Stopped -> {
                        acceptsVoiceInput = false
                        transcriptAppender = null
                        _uiState.update { it.copy(voiceInput = VoiceInputUiState()) }
                    }
                    is VoiceInputEvent.Transcript -> applyVoiceTranscript(event)
                    is VoiceInputEvent.Failed -> {
                        acceptsVoiceInput = false
                        transcriptAppender = null
                        _uiState.update {
                            it.copy(
                                voiceInput = VoiceInputUiState(
                                    phase = VoiceInputPhase.ERROR,
                                    error = event.error,
                                    detail = event.detail,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    /** 收集过滤后的记录列表 */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun collectRecords() {
        viewModelScope.launch {
            _uiState
                .map { it.selectedType }
                .distinctUntilChanged()
                .flatMapLatest { type ->
                    if (type == null) {
                        repository.getAllRecords()
                    } else {
                        repository.getRecordsByType(type.name)
                    }
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
                } else {
                    null
                }
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
                    if (type == null) {
                        flowOf(emptyList())
                    } else {
                        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                        repository.getRecordsByTypeInRange(
                            type.name,
                            thirtyDaysAgo,
                            System.currentTimeMillis(),
                        )
                    }
                }
                .catch { e -> Log.e("HealthVM", "Failed to collect chart data", e) }
                .collect { points ->
                    _uiState.update { it.copy(chartPoints = points) }
                }
        }
    }

    /** 收集全量记录并生成智能洞察。云端可用时优先使用云端，失败时自动回退本地规则。 */
    private fun collectInsights() {
        viewModelScope.launch {
            combine(
                repository.getAllRecords(),
                prefsRepository.settingsFlow,
                apiKeyStore.availableProviders,
            ) { records, settings, _ ->
                records to settings.userHeightCm
            }
                .catch { e -> Log.e("HealthVM", "Failed to build health insights", e) }
                .collectLatest { (records, heightCm) ->
                    _uiState.update { it.copy(isInsightRefreshing = true) }
                    val result = insightGeneration.generateWithStatus(
                        records = records,
                        userHeightCm = heightCm,
                    )
                    _uiState.update {
                        it.copy(
                            insights = result.insights,
                            insightExecutionStatus = result.executionStatus,
                            isInsightRefreshing = false,
                        )
                    }
                }
        }
    }

    private fun buildStats(latest: List<HealthRecord>, weekRecords: List<HealthRecord>): List<HealthTypeStat> {
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
            } else {
                null
            }

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
        val valueStr = if (metric.value == 0.0) {
            ""
        } else {
            if (metric.value == metric.value.toLong().toDouble()) {
                metric.value.toLong().toString()
            } else {
                metric.value.toString()
            }
        }
        val secondaryStr = metric.secondaryValue?.let {
            if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
        } ?: ""
        _uiState.update { s ->
            s.copy(
                showAddSheet = true,
                draft = s.draft.copy(
                    type = metric.type,
                    value = valueStr,
                    secondaryValue = secondaryStr,
                    notes = "",
                    source = metric.source,
                    sourceFeature = metric.sourceFeature,
                    sourceProvider = metric.sourceProvider,
                    sourceModel = metric.sourceModel,
                    sourceConfidence = metric.confidence,
                    sourceCacheKey = metric.sourceCacheKey,
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
                    source = record.source,
                    sourceFeature = record.sourceFeature,
                    sourceProvider = record.sourceProvider,
                    sourceModel = record.sourceModel,
                    sourceConfidence = record.sourceConfidence,
                    sourceCacheKey = record.sourceCacheKey,
                    confirmedAt = record.confirmedAt,
                    editingId = record.id,
                ),
            )
        }
    }

    fun dismissSheet() {
        stopVoiceInput()
        _uiState.update { it.copy(showAddSheet = false) }
    }

    fun onDraftTypeChange(type: HealthType) = _uiState.update { s ->
        s.copy(draft = s.draft.copy(type = type, value = "", secondaryValue = ""))
    }
    fun onDraftValueChange(v: String) = _uiState.update { s -> s.copy(draft = s.draft.copy(value = v)) }
    fun onDraftSecondaryChange(v: String) = _uiState.update { s -> s.copy(draft = s.draft.copy(secondaryValue = v)) }
    fun onDraftTimeChange(ts: Long) = _uiState.update { s -> s.copy(draft = s.draft.copy(timestamp = ts)) }
    fun onDraftNotesChange(v: String) = _uiState.update { s -> s.copy(draft = s.draft.copy(notes = v)) }

    fun startVoiceInput() {
        acceptsVoiceInput = true
        _uiState.update { it.copy(voiceInput = VoiceInputUiState(VoiceInputPhase.CONNECTING)) }
        voiceInputController.start()
    }

    fun stopVoiceInput() {
        acceptsVoiceInput = false
        transcriptAppender = null
        voiceInputController.stop()
        _uiState.update { it.copy(voiceInput = VoiceInputUiState()) }
    }

    fun saveRecord() {
        val draft = _uiState.value.draft
        val value = draft.value.toDoubleOrNull() ?: return
        val secondary = if (draft.type == HealthType.BLOOD_PRESSURE) {
            draft.secondaryValue.toDoubleOrNull()
        } else {
            null
        }
        val record = HealthRecord(
            id = draft.editingId ?: 0,
            type = draft.type.name,
            value = value,
            secondaryValue = secondary,
            timestamp = draft.timestamp,
            notes = draft.notes,
            source = draft.source,
            sourceFeature = draft.sourceFeature,
            sourceProvider = draft.sourceProvider,
            sourceModel = draft.sourceModel,
            sourceConfidence = draft.sourceConfidence,
            sourceCacheKey = draft.sourceCacheKey,
            confirmedAt = draft.confirmedAt ?: System.currentTimeMillis(),
        )
        safeLaunch {
            stopVoiceInput()
            if (draft.editingId == null) {
                repository.addRecord(record)
            } else {
                repository.updateRecord(record)
            }
            _uiState.update { it.copy(showAddSheet = false) }
        }
    }

    private fun applyVoiceTranscript(event: VoiceInputEvent.Transcript) {
        val currentNotes = _uiState.value.draft.notes
        val appender = transcriptAppender ?: VoiceTranscriptAppender(currentNotes).also {
            transcriptAppender = it
        }
        val nextNotes = if (event.isFinal) {
            appender.commit(event.text, insertSeparator = currentNotes.isNotBlank())
        } else {
            appender.preview(event.text, insertSeparator = currentNotes.isNotBlank())
        }
        _uiState.update { it.copy(draft = it.draft.copy(notes = nextNotes)) }
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
