package com.driezy.medlog.feature.medications.detail

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.driezy.medlog.capability.reminders.application.ReconcileRemindersUseCase
import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.model.MedicationLog
import com.driezy.medlog.data.repository.LogRepository
import com.driezy.medlog.data.repository.MedicationRepository
import com.driezy.medlog.domain.ReminderReconcileReason
import com.driezy.medlog.domain.THIRTY_DAYS_MS
import com.driezy.medlog.domain.model.MedicationId
import com.driezy.medlog.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class DetailUiState(
    val medication: Medication? = null,
    val logs: List<MedicationLog> = emptyList(),
    /** 近30天服药坚持率 */
    val adherence30d: Float = 0f,
    /** 近30天已服次数 */
    val taken30d: Int = 0,
    /** 近30天计划次数 */
    val total30d: Int = 0,
    /** 当前库存占初始设置的比率（0-1） */
    val isLoading: Boolean = true,
)

sealed interface DetailUiAction {
    data class Load(val medicationId: Long) : DetailUiAction
    data object Archive : DetailUiAction
    data object Delete : DetailUiAction
    data class AdjustStock(val delta: Double) : DetailUiAction
}

sealed interface DetailUiEffect {
    data object NavigateBack : DetailUiEffect
}

@HiltViewModel
class MedicationDetailViewModel @Inject constructor(
    private val medicationRepo: MedicationRepository,
    private val logRepo: LogRepository,
    private val reconcileReminders: ReconcileRemindersUseCase,
    private val clock: Clock,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()
    private val effectChannel = Channel<DetailUiEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    fun onAction(action: DetailUiAction) {
        when (action) {
            is DetailUiAction.Load -> loadMedication(action.medicationId)
            DetailUiAction.Archive -> archiveMedication()
            DetailUiAction.Delete -> deleteMedication()
            is DetailUiAction.AdjustStock -> adjustStock(action.delta)
        }
    }

    fun loadMedication(id: Long) {
        viewModelScope.launch {
            val med = medicationRepo.getMedicationById(id)
            _uiState.value = _uiState.value.copy(medication = med, isLoading = false)
            if (med != null) {
                // 加载最近60条日志
                logRepo.getLogsForMedication(id, limit = 60)
                    .catch { e -> Log.e("DetailVM", "Failed to load medication logs", e) }
                    .collect { logs ->
                        // 计算近30天坚持率
                        val now = clock.millis()
                        val thirtyDaysAgoMs = now - THIRTY_DAYS_MS
                        val recent = logs.filter { it.scheduledTimeMs >= thirtyDaysAgoMs }
                        val taken = recent.count { it.status == LogStatus.TAKEN }
                        val total = recent.size
                        val adherence = if (total == 0) 0f else taken.toFloat() / total.toFloat()
                        _uiState.value = _uiState.value.copy(
                            logs = logs,
                            taken30d = taken,
                            total30d = total,
                            adherence30d = adherence,
                        )
                    }
            }
        }
    }

    fun archiveMedication() {
        val id = _uiState.value.medication?.id ?: return
        safeLaunch {
            medicationRepo.archiveMedication(id)
            reconcileReminders.medication(MedicationId(id), ReminderReconcileReason.MEDICATION_CHANGED)
            effectChannel.send(DetailUiEffect.NavigateBack)
        }
    }

    fun deleteMedication() {
        val med = _uiState.value.medication ?: return
        safeLaunch {
            medicationRepo.deleteMedication(med)
            reconcileReminders.medication(MedicationId(med.id), ReminderReconcileReason.MEDICATION_CHANGED)
            effectChannel.send(DetailUiEffect.NavigateBack)
        }
    }

    /** 快捷调整库存，delta > 0 补药，< 0 扩展消耗 */
    fun adjustStock(delta: Double) {
        val med = _uiState.value.medication ?: return
        val currentStock = med.stock ?: return
        safeLaunch {
            val newStock = (currentStock + delta).coerceAtLeast(0.0)
            medicationRepo.updateStock(med.id, newStock)
            reconcileReminders.medication(MedicationId(med.id), ReminderReconcileReason.MEDICATION_CHANGED)
            // 重载最新状态
            val updated = medicationRepo.getMedicationById(med.id)
            _uiState.value = _uiState.value.copy(medication = updated)
        }
    }
}
