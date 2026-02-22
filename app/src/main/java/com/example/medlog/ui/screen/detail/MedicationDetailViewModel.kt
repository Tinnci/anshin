package com.example.medlog.ui.screen.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.LogStatus
import com.example.medlog.data.model.Medication
import com.example.medlog.data.model.MedicationLog
import com.example.medlog.data.repository.LogRepository
import com.example.medlog.data.repository.MedicationRepository
import com.example.medlog.domain.ToggleMedicationDoseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
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

@HiltViewModel
class MedicationDetailViewModel @Inject constructor(
    private val medicationRepo: MedicationRepository,
    private val logRepo: LogRepository,
    private val toggleDoseUseCase: ToggleMedicationDoseUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadMedication(id: Long) {
        viewModelScope.launch {
            val med = medicationRepo.getMedicationById(id)
            _uiState.value = _uiState.value.copy(medication = med, isLoading = false)
            if (med != null) {
                // 加载最近60条日志
                logRepo.getLogsForMedication(id, limit = 60)
                    .catch { }
                    .collect { logs ->
                        // 计算近30天坚持率
                        val now = System.currentTimeMillis()
                        val thirtyDaysAgoMs = now - 30L * 24 * 60 * 60 * 1000
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
        viewModelScope.launch {
            medicationRepo.archiveMedication(id)
            toggleDoseUseCase.cancelAllReminders(id)
        }
    }

    fun deleteMedication() {
        val med = _uiState.value.medication ?: return
        viewModelScope.launch {
            medicationRepo.deleteMedication(med)
            toggleDoseUseCase.cancelAllReminders(med.id)
        }
    }

    /** 快捷调整库存，delta > 0 补药，< 0 扩展消耗 */
    fun adjustStock(delta: Double) {
        val med = _uiState.value.medication ?: return
        val currentStock = med.stock ?: return
        viewModelScope.launch {
            val newStock = (currentStock + delta).coerceAtLeast(0.0)
            medicationRepo.updateStock(med.id, newStock)
            // 重载最新状态
            val updated = medicationRepo.getMedicationById(med.id)
            _uiState.value = _uiState.value.copy(medication = updated)
        }
    }
}
