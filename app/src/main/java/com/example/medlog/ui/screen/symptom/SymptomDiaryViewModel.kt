package com.example.medlog.ui.screen.symptom

import androidx.lifecycle.ViewModel
import com.example.medlog.ui.BaseViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.SymptomLog
import com.example.medlog.data.repository.SymptomRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── 常用症状 / 副作用预设列表 ──────────────────────────────────────────────────

val PRESET_SYMPTOMS = listOf("头痛", "恶心", "疲倦", "失眠", "头晕", "发烧", "胃痛", "皮疹", "心悸", "口渴")
val PRESET_SIDE_EFFECTS = listOf("胃部不适", "口干", "食欲减退", "嗜睡", "皮肤瘙痒", "腹泻", "便秘", "视力模糊")

// ─── Dialog 草稿状态 ───────────────────────────────────────────────────────────

data class DiaryDraftState(
    val editingId: Long? = null,           // null = 新建；非 null = 编辑
    val rating: Int = 3,                   // 1–5
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
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class SymptomDiaryViewModel @Inject constructor(
    private val repo: SymptomRepository,
) : BaseViewModel() {

    private val _dialogState = MutableStateFlow<Pair<Boolean, DiaryDraftState>>(false to DiaryDraftState())

    val uiState = combine(
        repo.getAllLogs(),
        _dialogState,
    ) { logs, (showDialog, draft) ->
        SymptomDiaryUiState(
            logs = logs,
            isLoading = false,
            showDialog = showDialog,
            draft = draft,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SymptomDiaryUiState(),
    )

    // ── 打开新建 Dialog ──────────────────────────────────────────────────────

    fun startAdd() {
        _dialogState.update { _ -> true to DiaryDraftState() }
    }

    // ── 打开编辑 Dialog ──────────────────────────────────────────────────────

    fun startEdit(log: SymptomLog) {
        _dialogState.update { _ ->
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
        _dialogState.update { (_, draft) -> false to draft }
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

    // ── 保存 ─────────────────────────────────────────────────────────────────

    fun saveLog() {
        val draft = _dialogState.value.second
        viewModelScope.launch {
            if (draft.editingId == null) {
                repo.insert(
                    SymptomLog(
                        recordedAt = System.currentTimeMillis(),
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
                        recordedAt = System.currentTimeMillis(),
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
        viewModelScope.launch { repo.deleteById(id) }
    }

    // ── 私有帮助函数 ─────────────────────────────────────────────────────────

    private fun updateDraft(transform: (DiaryDraftState) -> DiaryDraftState) {
        _dialogState.update { (show, draft) -> show to transform(draft) }
    }
}
