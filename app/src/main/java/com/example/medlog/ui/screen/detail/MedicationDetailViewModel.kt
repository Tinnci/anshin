package com.example.medlog.ui.screen.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.Medication
import com.example.medlog.data.model.MedicationLog
import com.example.medlog.data.repository.MedicationRepository
import com.example.medlog.notification.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val medication: Medication? = null,
    val logs: List<MedicationLog> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class MedicationDetailViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val notificationHelper: NotificationHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadMedication(id: Long) {
        viewModelScope.launch {
            val med = repository.getMedicationById(id)
            _uiState.value = _uiState.value.copy(medication = med, isLoading = false)
            if (med != null) {
                repository.getLogsForMedication(id, limit = 60)
                    .catch { }
                    .collect { logs ->
                        _uiState.value = _uiState.value.copy(logs = logs)
                    }
            }
        }
    }

    fun archiveMedication() {
        val id = _uiState.value.medication?.id ?: return
        viewModelScope.launch {
            repository.archiveMedication(id)
            notificationHelper.cancelAlarm(id)
        }
    }

    fun deleteMedication() {
        val med = _uiState.value.medication ?: return
        viewModelScope.launch {
            repository.deleteMedication(med)
            notificationHelper.cancelAlarm(med.id)
        }
    }
}
