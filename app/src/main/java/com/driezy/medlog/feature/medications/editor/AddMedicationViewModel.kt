package com.driezy.medlog.feature.medications.editor

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.driezy.medlog.R
import com.driezy.medlog.capability.reminders.application.ReconcileRemindersUseCase
import com.driezy.medlog.data.model.Drug
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.model.TimePeriod
import com.driezy.medlog.data.repository.DrugRepository
import com.driezy.medlog.data.repository.MedicationRepository
import com.driezy.medlog.data.repository.SettingsPreferences
import com.driezy.medlog.data.repository.UserPreferencesRepository
import com.driezy.medlog.domain.ReminderReconcileReason
import com.driezy.medlog.domain.model.MedicationId
import com.driezy.medlog.domain.todayStart
import com.driezy.medlog.ui.BaseViewModel
import com.driezy.medlog.util.ReminderTimeUtils
import com.driezy.medlog.voice.VoiceInputController
import com.driezy.medlog.voice.VoiceInputEvent
import com.driezy.medlog.voice.VoiceInputPhase
import com.driezy.medlog.voice.VoiceInputUiState
import com.driezy.medlog.voice.VoiceTranscriptAppender
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject

/** 剂型选项 */
data class DrugForm(val key: String, val label: String, val icon: Int)

@Serializable
data class AddMedicationUiState(
    // ── 基础信息 ──────────────────────────────────────────────────
    val name: String = "",
    val category: String = "",
    val form: String = "tablet", // tablet/capsule/liquid/powder
    val isHighPriority: Boolean = false,
    val isCustomDrug: Boolean = false,

    // ── 剂量 ──────────────────────────────────────────────────────
    val doseQuantity: Double = 1.0, // 每次几片/粒/ml
    val doseUnit: String = "", // 由 ViewModel 初始化时从 R.string.default_dose_unit 填充

    // ── 按需 / PRN ────────────────────────────────────────────────
    val isPRN: Boolean = false,
    val maxDailyDose: String = "", // 每日最大剂量（字符串，便于输入）

    // ── 服药时段 & 提醒 ──────────────────────────────────────────
    val timePeriod: TimePeriod = TimePeriod.MORNING,
    val reminderTimes: List<String> = listOf("08:00"), // HH:mm 列表

    // ── 频率 ──────────────────────────────────────────────────────
    val frequencyType: String = "daily", // daily / interval / specific_days
    val frequencyInterval: Int = 1,
    val frequencyDays: String = "1,2,3,4,5,6,7", // 逗号分隔的周天

    // ── 起止日期 ─────────────────────────────────────────────────
    val startDate: Long = 0L,
    val endDate: Long? = null,

    // ── 库存 ─────────────────────────────────────────────────────
    val stock: String = "",
    val refillThreshold: String = "",
    /** 0=禁用, 7/14/30=N 天前不足时提醒备货（基于每日用量估算） */
    val refillReminderDays: Int = 0,

    // ── 其他 ─────────────────────────────────────────────────────
    val notes: String = "",
    // ── 间隔给药（v6） ─────────────────────────────────────────────────
    /** 0 = 不启用；>0 = 按固定小时间隔给药（适用于旅行跨时区 / 需精确间隔的药物） */
    val intervalHours: Int = 0,
    // ── 药品分类扩展 ──────────────────────────────────────────────
    /** 是否中成药（选药时从 Drug 填入） */
    val isTcm: Boolean = false,
    /** 完整分类路径（选药时从 Drug.fullPath 填入） */
    val fullPath: String = "",

    // ── UI 状态 ──────────────────────────────────────────────────
    @Transient val isSaving: Boolean = false,
    val enableTimePeriodMode: Boolean = true,
    @Transient val error: String? = null,
    /** 验证错误资源 ID（优先于 error 文本显示） */
    @Transient @param:StringRes val errorRes: Int? = null,
    @Transient val drugSuggestions: List<Drug> = emptyList(),
    @Transient val showDrugSuggestions: Boolean = false,
    @Transient val voiceInput: VoiceInputUiState = VoiceInputUiState(),
)

sealed interface AddMedicationUiAction {
    data class PrefillDrug(val name: String, val category: String) : AddMedicationUiAction
    data class LoadExisting(val id: Long) : AddMedicationUiAction
    data class NameChanged(val value: String) : AddMedicationUiAction
    data class DrugSelected(val drug: Drug) : AddMedicationUiAction
    data object DismissDrugSuggestions : AddMedicationUiAction
    data class CategoryChanged(val value: String) : AddMedicationUiAction
    data class FormChanged(val value: String) : AddMedicationUiAction
    data class HighPriorityChanged(val enabled: Boolean) : AddMedicationUiAction
    data class DoseQuantityChanged(val value: Double) : AddMedicationUiAction
    data class DoseUnitChanged(val value: String) : AddMedicationUiAction
    data class PrnChanged(val enabled: Boolean) : AddMedicationUiAction
    data class MaxDailyDoseChanged(val value: String) : AddMedicationUiAction
    data class IntervalHoursChanged(val value: Int) : AddMedicationUiAction
    data class TimePeriodChanged(val value: TimePeriod) : AddMedicationUiAction
    data class AddReminderTime(val value: String) : AddMedicationUiAction
    data class RemoveReminderTime(val value: String) : AddMedicationUiAction
    data class FrequencyTypeChanged(val value: String) : AddMedicationUiAction
    data class FrequencyIntervalChanged(val value: Int) : AddMedicationUiAction
    data class ToggleFrequencyDay(val day: Int) : AddMedicationUiAction
    data class StartDateChanged(val value: Long) : AddMedicationUiAction
    data class EndDateChanged(val value: Long?) : AddMedicationUiAction
    data class StockChanged(val value: String) : AddMedicationUiAction
    data class RefillThresholdChanged(val value: String) : AddMedicationUiAction
    data class RefillReminderDaysChanged(val value: Int) : AddMedicationUiAction
    data class NotesChanged(val value: String) : AddMedicationUiAction
    data object StartVoiceInput : AddMedicationUiAction
    data object StopVoiceInput : AddMedicationUiAction
    data object DiscardDraft : AddMedicationUiAction
    data class Save(val existingId: Long?) : AddMedicationUiAction
}

sealed interface AddMedicationUiEffect {
    data object Saved : AddMedicationUiEffect
}

@HiltViewModel
class AddMedicationViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val reconcileReminders: ReconcileRemindersUseCase,
    private val prefsRepository: UserPreferencesRepository,
    private val drugRepository: DrugRepository,
    private val voiceInputController: VoiceInputController,
    @param:ApplicationContext private val appContext: Context,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : BaseViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private val restoredDraft: AddMedicationUiState? = runCatching {
        savedStateHandle.get<String>(DRAFT_KEY)?.let { json.decodeFromString<AddMedicationUiState>(it) }
    }.getOrNull()
    private val _uiState = MutableStateFlow(
        restoredDraft ?: AddMedicationUiState(
            doseUnit = appContext.getString(R.string.default_dose_unit),
            startDate = todayStart(clock),
        ),
    )
    val uiState: StateFlow<AddMedicationUiState> = _uiState.asStateFlow()
    private val effectChannel = Channel<AddMedicationUiEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()
    private var baselineState: AddMedicationUiState = _uiState.value
    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    /** 最新作息时间设置缓存，用于运算添加时段自动时间 */
    private val latestPrefs = MutableStateFlow(SettingsPreferences())

    /** 药品名称搜索查询 Flow，用于 debounce */
    private val nameQuery = MutableStateFlow("")
    private var acceptsVoiceInput = false
    private var transcriptAppender: VoiceTranscriptAppender? = null

    /** 作息时间段模式开关：false 时隐藏作息模式相关 UI，始终以精确时间模式运行 */
    init {
        viewModelScope.launch {
            prefsRepository.settingsFlow.collect {
                latestPrefs.value = it
                update { copy(enableTimePeriodMode = it.enableTimePeriodMode) }
            }
        }
        viewModelScope.launch {
            voiceInputController.events.collect { event ->
                if (!acceptsVoiceInput) return@collect
                when (event) {
                    VoiceInputEvent.Connecting -> update {
                        copy(voiceInput = VoiceInputUiState(VoiceInputPhase.CONNECTING))
                    }
                    VoiceInputEvent.Listening -> {
                        transcriptAppender = VoiceTranscriptAppender(_uiState.value.notes)
                        update { copy(voiceInput = VoiceInputUiState(VoiceInputPhase.LISTENING)) }
                    }
                    VoiceInputEvent.Stopped -> {
                        acceptsVoiceInput = false
                        transcriptAppender = null
                        update { copy(voiceInput = VoiceInputUiState()) }
                    }
                    is VoiceInputEvent.Transcript -> applyVoiceTranscript(event)
                    is VoiceInputEvent.Failed -> {
                        acceptsVoiceInput = false
                        transcriptAppender = null
                        update {
                            copy(
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
        // 搜索建议：300ms debounce 避免每次击键都触发全库搜索
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            nameQuery
                .debounce(300)
                .collectLatest { query ->
                    if (query.isNotBlank()) {
                        val results = drugRepository.searchDrugsRanked(query).take(8)
                        update { copy(drugSuggestions = results, showDrugSuggestions = results.isNotEmpty()) }
                    } else {
                        update { copy(drugSuggestions = emptyList(), showDrugSuggestions = false) }
                    }
                }
        }
    }

    fun onAction(action: AddMedicationUiAction) {
        when (action) {
            is AddMedicationUiAction.PrefillDrug -> prefillFromDrug(action.name, action.category)
            is AddMedicationUiAction.LoadExisting -> loadExisting(action.id)
            is AddMedicationUiAction.NameChanged -> onNameChange(action.value)
            is AddMedicationUiAction.DrugSelected -> onDrugSelected(action.drug)
            AddMedicationUiAction.DismissDrugSuggestions -> dismissDrugSuggestions()
            is AddMedicationUiAction.CategoryChanged -> onCategoryChange(action.value)
            is AddMedicationUiAction.FormChanged -> onFormChange(action.value)
            is AddMedicationUiAction.HighPriorityChanged -> onHighPriorityChange(action.enabled)
            is AddMedicationUiAction.DoseQuantityChanged -> onDoseQuantityChange(action.value)
            is AddMedicationUiAction.DoseUnitChanged -> onDoseUnitChange(action.value)
            is AddMedicationUiAction.PrnChanged -> onIsPRNChange(action.enabled)
            is AddMedicationUiAction.MaxDailyDoseChanged -> onMaxDailyDoseChange(action.value)
            is AddMedicationUiAction.IntervalHoursChanged -> onIntervalHoursChange(action.value)
            is AddMedicationUiAction.TimePeriodChanged -> onTimePeriodChange(action.value)
            is AddMedicationUiAction.AddReminderTime -> addReminderTime(action.value)
            is AddMedicationUiAction.RemoveReminderTime -> removeReminderTime(action.value)
            is AddMedicationUiAction.FrequencyTypeChanged -> onFrequencyTypeChange(action.value)
            is AddMedicationUiAction.FrequencyIntervalChanged -> onFrequencyIntervalChange(action.value)
            is AddMedicationUiAction.ToggleFrequencyDay -> toggleFrequencyDay(action.day)
            is AddMedicationUiAction.StartDateChanged -> onStartDateChange(action.value)
            is AddMedicationUiAction.EndDateChanged -> onEndDateChange(action.value)
            is AddMedicationUiAction.StockChanged -> onStockChange(action.value)
            is AddMedicationUiAction.RefillThresholdChanged -> onRefillThresholdChange(action.value)
            is AddMedicationUiAction.RefillReminderDaysChanged -> onRefillReminderDaysChange(action.value)
            is AddMedicationUiAction.NotesChanged -> onNotesChange(action.value)
            AddMedicationUiAction.StartVoiceInput -> startVoiceInput()
            AddMedicationUiAction.StopVoiceInput -> stopVoiceInput()
            AddMedicationUiAction.DiscardDraft -> discardDraft()
            is AddMedicationUiAction.Save -> save(action.existingId)
        }
    }

    /** 从药品数据库选药后预填名称和分类（仅新增时生效） */
    fun prefillFromDrug(name: String, category: String) {
        if (_uiState.value.name.isEmpty()) {
            _uiState.value = _uiState.value.copy(name = name, category = category)
            markBaseline()
        }
    }

    /** 加载已有药品进行编辑 */
    fun loadExisting(medicationId: Long) {
        safeLaunch(onError = { e -> update { copy(error = e.message) } }) {
            val med = repository.getMedicationById(medicationId) ?: return@safeLaunch
            _uiState.value = AddMedicationUiState(
                name = med.name,
                category = med.category,
                isTcm = med.isTcm,
                fullPath = med.fullPath,
                form = med.form,
                isHighPriority = med.isHighPriority,
                isCustomDrug = med.isCustomDrug,
                doseQuantity = med.doseQuantity,
                doseUnit = med.doseUnit,
                isPRN = med.isPRN,
                maxDailyDose = med.maxDailyDose?.toString() ?: "",
                timePeriod = TimePeriod.fromKey(med.timePeriod),
                reminderTimes = med.reminderTimes.split(",").filter { it.isNotBlank() }
                    .ifEmpty { listOf("08:00") },
                frequencyType = med.frequencyType,
                frequencyInterval = med.frequencyInterval,
                frequencyDays = med.frequencyDays,
                startDate = med.startDate,
                endDate = med.endDate,
                stock = med.stock?.toString() ?: "",
                refillThreshold = med.refillThreshold?.toString() ?: "",
                refillReminderDays = med.refillReminderDays,
                notes = med.notes,
                intervalHours = med.intervalHours,
            )
            markBaseline()
        }
    }

    // ── 字段 setters ────────────────────────────────────────────

    fun onNameChange(v: String) {
        update { copy(name = v, error = null, errorRes = null) }
        nameQuery.value = v
    }

    /** 从下拉建议中选中一种药，自动填入名称+分类+完整路径+是否中成药并关闭建议列表 */
    fun onDrugSelected(drug: Drug) {
        // 多路径药品（复方/多效药）用换行符连接所有路径存储；单路径直接取 fullPath
        val storedPath = if (drug.allPaths.size > 1) drug.allPaths.joinToString("\n") else drug.fullPath
        update {
            copy(
                name = drug.name,
                category = drug.category,
                isTcm = drug.isTcm,
                fullPath = storedPath,
                showDrugSuggestions = false,
                drugSuggestions = emptyList(),
                error = null,
                errorRes = null,
            )
        }
    }

    /** 关闭建议下拉（用户点击外部时） */
    fun dismissDrugSuggestions() = update { copy(showDrugSuggestions = false) }

    fun onCategoryChange(v: String) = update { copy(category = v) }
    fun onFormChange(v: String) = update { copy(form = v) }
    fun onHighPriorityChange(v: Boolean) = update { copy(isHighPriority = v) }
    fun onCustomDrugChange(v: Boolean) = update { copy(isCustomDrug = v) }

    fun onDoseQuantityChange(v: Double) = update { copy(doseQuantity = v) }
    fun onDoseUnitChange(v: String) = update { copy(doseUnit = v) }

    fun onIsPRNChange(v: Boolean) = update { copy(isPRN = v) }
    fun onMaxDailyDoseChange(v: String) = update { copy(maxDailyDose = v) }
    fun onIntervalHoursChange(v: Int) = update { copy(intervalHours = v.coerceAtLeast(0)) }

    fun onTimePeriodChange(v: TimePeriod) {
        val autoTime = if (v == TimePeriod.EXACT) {
            _uiState.value.reminderTimes.firstOrNull() ?: "08:00"
        } else {
            ReminderTimeUtils.timePeriodToReminderTime(v, latestPrefs.value)
        }
        update {
            copy(
                timePeriod = v,
                reminderTimes = if (v == TimePeriod.EXACT) reminderTimes else listOf(autoTime),
            )
        }
    }

    fun addReminderTime(hhmm: String) {
        val existing = _uiState.value.reminderTimes.toMutableList()
        if (!existing.contains(hhmm)) {
            existing += hhmm
            existing.sort()
        }
        update { copy(reminderTimes = existing) }
    }
    fun removeReminderTime(hhmm: String) = update {
        copy(reminderTimes = reminderTimes.filterNot { it == hhmm }.ifEmpty { listOf("08:00") })
    }

    fun onFrequencyTypeChange(v: String) = update { copy(frequencyType = v) }
    fun onFrequencyIntervalChange(v: Int) = update { copy(frequencyInterval = v.coerceIn(1, 90)) }
    fun toggleFrequencyDay(day: Int) {
        val current = _uiState.value.frequencyDays.split(",").filter { it.isNotBlank() }.toMutableList()
        val s = day.toString()
        if (current.contains(s)) current.remove(s) else current.add(s)
        val sorted = current.distinct().sortedBy { it.toIntOrNull() ?: 0 }.joinToString(",")
        update { copy(frequencyDays = sorted.ifBlank { "1" }) }
    }

    fun onStartDateChange(v: Long) = update { copy(startDate = v) }
    fun onEndDateChange(v: Long?) = update { copy(endDate = v) }

    fun onStockChange(v: String) = update { copy(stock = v) }
    fun onRefillThresholdChange(v: String) = update { copy(refillThreshold = v) }
    fun onRefillReminderDaysChange(v: Int) = update { copy(refillReminderDays = v) }
    fun onNotesChange(v: String) = update { copy(notes = v) }

    fun startVoiceInput() {
        acceptsVoiceInput = true
        update { copy(voiceInput = VoiceInputUiState(VoiceInputPhase.CONNECTING)) }
        voiceInputController.start()
    }

    fun stopVoiceInput() {
        acceptsVoiceInput = false
        transcriptAppender = null
        voiceInputController.stop()
        update { copy(voiceInput = VoiceInputUiState()) }
    }

    // ── 保存 ─────────────────────────────────────────────────────

    fun save(existingId: Long?) {
        val state = _uiState.value
        if (state.name.isBlank()) {
            update { copy(errorRes = R.string.error_name_required) }
            return
        }
        safeLaunch(onError = { e -> update { copy(isSaving = false, error = e.message) } }) {
            stopVoiceInput()
            update { copy(isSaving = true) }
            // 取第一个提醒时间作为 reminderHour/Minute（向后兼容通知调度）
            val firstTime = state.reminderTimes.firstOrNull() ?: "08:00"
            val (h, m) = firstTime.split(":").let {
                (it.getOrNull(0)?.toIntOrNull() ?: 8) to (it.getOrNull(1)?.toIntOrNull() ?: 0)
            }
            val medication = Medication(
                id = existingId ?: 0,
                name = state.name.trim(),
                category = state.category.trim(),
                isTcm = state.isTcm,
                fullPath = state.fullPath.trim(),
                form = state.form,
                isHighPriority = state.isHighPriority,
                isCustomDrug = state.isCustomDrug,
                dose = state.doseQuantity, // 兼容旧字段
                doseUnit = state.doseUnit,
                doseQuantity = state.doseQuantity,
                isPRN = state.isPRN,
                maxDailyDose = state.maxDailyDose.toDoubleOrNull(),
                timePeriod = state.timePeriod.key,
                reminderTimes = state.reminderTimes.joinToString(","),
                reminderHour = h,
                reminderMinute = m,
                frequencyType = state.frequencyType,
                frequencyInterval = state.frequencyInterval,
                frequencyDays = state.frequencyDays,
                startDate = state.startDate,
                endDate = state.endDate,
                stock = state.stock.toDoubleOrNull(),
                refillThreshold = state.refillThreshold.toDoubleOrNull(),
                refillReminderDays = state.refillReminderDays,
                notes = state.notes,
                intervalHours = state.intervalHours,
            )
            val savedId = if (existingId == null) {
                val newId = repository.addMedication(medication)
                newId
            } else {
                repository.updateMedication(medication)
                existingId
            }
            reconcileReminders.medication(MedicationId(savedId), ReminderReconcileReason.MEDICATION_CHANGED)
            clearDraft()
            update { copy(isSaving = false) }
            _isDirty.value = false
            effectChannel.send(AddMedicationUiEffect.Saved)
        }
    }

    private inline fun update(block: AddMedicationUiState.() -> AddMedicationUiState) {
        _uiState.value = _uiState.value.block()
        persistDraft()
        _isDirty.value = isDraftDirty(_uiState.value)
    }

    private fun persistDraft() {
        savedStateHandle[DRAFT_KEY] = runCatching { json.encodeToString(_uiState.value) }.getOrNull()
    }

    private fun clearDraft() {
        savedStateHandle.remove<String>(DRAFT_KEY)
    }

    private fun normalizedDraft(state: AddMedicationUiState) = state.copy(
        isSaving = false,
        error = null,
        errorRes = null,
        drugSuggestions = emptyList(),
        showDrugSuggestions = false,
        voiceInput = VoiceInputUiState(),
    )

    private fun isDraftDirty(state: AddMedicationUiState): Boolean =
        normalizedDraft(state) != normalizedDraft(baselineState)

    private fun markBaseline() {
        baselineState = normalizedDraft(_uiState.value)
        _isDirty.value = false
        persistDraft()
    }

    fun discardDraft() {
        stopVoiceInput()
        clearDraft()
        _isDirty.value = false
    }

    private companion object {
        const val DRAFT_KEY = "add_medication_draft"
    }

    private fun applyVoiceTranscript(event: VoiceInputEvent.Transcript) {
        val currentNotes = _uiState.value.notes
        val appender = transcriptAppender ?: VoiceTranscriptAppender(currentNotes).also {
            transcriptAppender = it
        }
        val nextNotes = if (event.isFinal) {
            appender.commit(event.text, insertSeparator = currentNotes.isNotBlank())
        } else {
            appender.preview(event.text, insertSeparator = currentNotes.isNotBlank())
        }
        update { copy(notes = nextNotes) }
    }
}
