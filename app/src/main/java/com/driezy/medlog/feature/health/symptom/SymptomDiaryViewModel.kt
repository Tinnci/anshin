package com.driezy.medlog.feature.health.symptom

import androidx.lifecycle.viewModelScope
import com.driezy.medlog.data.model.SymptomLog
import com.driezy.medlog.data.repository.SymptomRepository
import com.driezy.medlog.ui.BaseViewModel
import com.driezy.medlog.voice.VoiceInputController
import com.driezy.medlog.voice.VoiceInputEvent
import com.driezy.medlog.voice.VoiceInputPhase
import com.driezy.medlog.voice.VoiceInputUiState
import com.driezy.medlog.voice.VoiceTranscriptAppender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

// ─── 常用症状 / 副作用预设列表 ──────────────────────────────────────────────────
// 已迁移至 string-array 资源 R.array.preset_symptoms / R.array.preset_side_effects
// Compose UI 层通过 stringArrayResource() 加载，见 SymptomDiaryScreen.kt

// ─── Dialog 草稿状态 ───────────────────────────────────────────────────────────

data class DiaryDraftState(
    val editingId: Long? = null, // null = 新建；非 null = 编辑
    val rating: Int = 3, // 1–5
    val symptoms: Set<String> = emptySet(),
    val customSymptom: String = "",
    val sideEffects: Set<String> = emptySet(),
    val customSideEffect: String = "",
    val note: String = "",
    val linkedMedicationId: Long = -1L,
    val linkedMedicationName: String = "",
)

// ─── 整体 UI 状态 ──────────────────────────────────────────────────────────────

data class SymptomDiaryUiState(
    val logs: List<SymptomLog> = emptyList(),
    val isLoading: Boolean = true,
    val showDialog: Boolean = false,
    val draft: DiaryDraftState = DiaryDraftState(),
    val voiceInput: VoiceInputUiState = VoiceInputUiState(),
)

sealed interface SymptomDiaryUiAction {
    data object StartAdd : SymptomDiaryUiAction
    data class StartEdit(val log: SymptomLog) : SymptomDiaryUiAction
    data class Delete(val id: Long) : SymptomDiaryUiAction
    data object DismissDialog : SymptomDiaryUiAction
    data class RatingChanged(val value: Int) : SymptomDiaryUiAction
    data class ToggleSymptom(val value: String) : SymptomDiaryUiAction
    data class CustomSymptomChanged(val value: String) : SymptomDiaryUiAction
    data object AddCustomSymptom : SymptomDiaryUiAction
    data class ToggleSideEffect(val value: String) : SymptomDiaryUiAction
    data class CustomSideEffectChanged(val value: String) : SymptomDiaryUiAction
    data object AddCustomSideEffect : SymptomDiaryUiAction
    data class NoteChanged(val value: String) : SymptomDiaryUiAction
    data object StartVoiceInput : SymptomDiaryUiAction
    data object StopVoiceInput : SymptomDiaryUiAction
    data object Save : SymptomDiaryUiAction
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class SymptomDiaryViewModel @Inject constructor(
    private val repo: SymptomRepository,
    private val voiceInputController: VoiceInputController,
    private val clock: Clock,
) : BaseViewModel() {

    private val dialogStateFlow = MutableStateFlow<Pair<Boolean, DiaryDraftState>>(false to DiaryDraftState())
    private val voiceInputStateFlow = MutableStateFlow(VoiceInputUiState())
    private var acceptsVoiceInput = false
    private var transcriptAppender: VoiceTranscriptAppender? = null

    val uiState = combine(
        repo.getAllLogs(),
        dialogStateFlow,
        voiceInputStateFlow,
    ) { logs, (showDialog, draft), voiceInput ->
        SymptomDiaryUiState(
            logs = logs,
            isLoading = false,
            showDialog = showDialog,
            draft = draft,
            voiceInput = voiceInput,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SymptomDiaryUiState(),
    )

    init {
        viewModelScope.launch {
            voiceInputController.events.collect { event ->
                if (!acceptsVoiceInput) return@collect
                when (event) {
                    VoiceInputEvent.Connecting ->
                        voiceInputStateFlow.value =
                            VoiceInputUiState(VoiceInputPhase.CONNECTING)
                    VoiceInputEvent.Listening -> {
                        transcriptAppender = VoiceTranscriptAppender(dialogStateFlow.value.second.note)
                        voiceInputStateFlow.value = VoiceInputUiState(VoiceInputPhase.LISTENING)
                    }
                    VoiceInputEvent.Stopped -> {
                        acceptsVoiceInput = false
                        transcriptAppender = null
                        voiceInputStateFlow.value = VoiceInputUiState()
                    }
                    is VoiceInputEvent.Transcript -> applyVoiceTranscript(event)
                    is VoiceInputEvent.Failed -> {
                        acceptsVoiceInput = false
                        transcriptAppender = null
                        voiceInputStateFlow.value = VoiceInputUiState(
                            phase = VoiceInputPhase.ERROR,
                            error = event.error,
                            detail = event.detail,
                        )
                    }
                }
            }
        }
    }

    fun onAction(action: SymptomDiaryUiAction) {
        when (action) {
            SymptomDiaryUiAction.StartAdd -> startAdd()
            is SymptomDiaryUiAction.StartEdit -> startEdit(action.log)
            is SymptomDiaryUiAction.Delete -> deleteLog(action.id)
            SymptomDiaryUiAction.DismissDialog -> dismissDialog()
            is SymptomDiaryUiAction.RatingChanged -> onRatingChange(action.value)
            is SymptomDiaryUiAction.ToggleSymptom -> onToggleSymptom(action.value)
            is SymptomDiaryUiAction.CustomSymptomChanged -> onCustomSymptomChange(action.value)
            SymptomDiaryUiAction.AddCustomSymptom -> onAddCustomSymptom()
            is SymptomDiaryUiAction.ToggleSideEffect -> onToggleSideEffect(action.value)
            is SymptomDiaryUiAction.CustomSideEffectChanged -> onCustomSideEffectChange(action.value)
            SymptomDiaryUiAction.AddCustomSideEffect -> onAddCustomSideEffect()
            is SymptomDiaryUiAction.NoteChanged -> onNoteChange(action.value)
            SymptomDiaryUiAction.StartVoiceInput -> startVoiceInput()
            SymptomDiaryUiAction.StopVoiceInput -> stopVoiceInput()
            SymptomDiaryUiAction.Save -> saveLog()
        }
    }

    // ── 打开新建 Dialog ──────────────────────────────────────────────────────

    fun startAdd() {
        dialogStateFlow.update { _ -> true to DiaryDraftState() }
    }

    // ── 打开编辑 Dialog ──────────────────────────────────────────────────────

    fun startEdit(log: SymptomLog) {
        dialogStateFlow.update { _ ->
            true to DiaryDraftState(
                editingId = log.id,
                rating = log.overallRating,
                symptoms = log.symptomList.toSet(),
                sideEffects = log.sideEffectList.toSet(),
                note = log.note,
                linkedMedicationId = log.medicationId,
                linkedMedicationName = log.medicationName,
            )
        }
    }

    // ── 关闭 Dialog ──────────────────────────────────────────────────────────

    fun dismissDialog() {
        voiceInputController.stop()
        acceptsVoiceInput = false
        dialogStateFlow.update { (_, draft) -> false to draft }
    }

    // ── 草稿字段更新 ─────────────────────────────────────────────────────────

    fun onRatingChange(rating: Int) = updateDraft { it.copy(rating = rating.coerceIn(1, 5)) }

    fun onToggleSymptom(symptom: String) = updateDraft { d ->
        val s = d.symptoms.toMutableSet()
        if (!s.add(symptom)) s.remove(symptom)
        d.copy(symptoms = s)
    }

    fun onCustomSymptomChange(value: String) = updateDraft { it.copy(customSymptom = value) }

    fun onAddCustomSymptom() = updateDraft { d ->
        val trimmed = d.customSymptom.trim()
        if (trimmed.isBlank()) return@updateDraft d
        d.copy(symptoms = d.symptoms + trimmed, customSymptom = "")
    }

    fun onToggleSideEffect(se: String) = updateDraft { d ->
        val s = d.sideEffects.toMutableSet()
        if (!s.add(se)) s.remove(se)
        d.copy(sideEffects = s)
    }

    fun onCustomSideEffectChange(value: String) = updateDraft { it.copy(customSideEffect = value) }

    fun onAddCustomSideEffect() = updateDraft { d ->
        val trimmed = d.customSideEffect.trim()
        if (trimmed.isBlank()) return@updateDraft d
        d.copy(sideEffects = d.sideEffects + trimmed, customSideEffect = "")
    }

    fun onNoteChange(note: String) = updateDraft { it.copy(note = note) }

    fun startVoiceInput() {
        acceptsVoiceInput = true
        voiceInputStateFlow.value = VoiceInputUiState(VoiceInputPhase.CONNECTING)
        voiceInputController.start()
    }

    fun stopVoiceInput() {
        acceptsVoiceInput = false
        voiceInputController.stop()
    }

    // ── 保存 ─────────────────────────────────────────────────────────────────

    fun saveLog() {
        val draft = dialogStateFlow.value.second
        safeLaunch {
            voiceInputController.stop()
            if (draft.editingId == null) {
                repo.insert(
                    SymptomLog(
                        recordedAt = clock.millis(),
                        overallRating = draft.rating,
                        symptoms = draft.symptoms.joinToString(","),
                        sideEffects = draft.sideEffects.joinToString(","),
                        note = draft.note.trim(),
                        medicationId = draft.linkedMedicationId,
                        medicationName = draft.linkedMedicationName.trim(),
                    ),
                )
            } else {
                repo.update(
                    SymptomLog(
                        id = draft.editingId,
                        recordedAt = clock.millis(),
                        overallRating = draft.rating,
                        symptoms = draft.symptoms.joinToString(","),
                        sideEffects = draft.sideEffects.joinToString(","),
                        note = draft.note.trim(),
                        medicationId = draft.linkedMedicationId,
                        medicationName = draft.linkedMedicationName.trim(),
                    ),
                )
            }
            dismissDialog()
        }
    }

    // ── 删除 ─────────────────────────────────────────────────────────────────

    fun deleteLog(id: Long) {
        safeLaunch { repo.deleteById(id) }
    }

    // ── 私有帮助函数 ─────────────────────────────────────────────────────────

    private fun updateDraft(transform: (DiaryDraftState) -> DiaryDraftState) {
        dialogStateFlow.update { (show, draft) -> show to transform(draft) }
    }

    private fun applyVoiceTranscript(event: VoiceInputEvent.Transcript) {
        val appender = transcriptAppender ?: VoiceTranscriptAppender(dialogStateFlow.value.second.note).also {
            transcriptAppender = it
        }
        val nextNote = if (event.isFinal) {
            appender.commit(event.text, insertSeparator = dialogStateFlow.value.second.note.isNotBlank())
        } else {
            appender.preview(event.text, insertSeparator = dialogStateFlow.value.second.note.isNotBlank())
        }
        updateDraft { it.copy(note = nextNote) }
    }
}
