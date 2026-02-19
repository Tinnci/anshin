package com.example.medlog.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.Medication
import com.example.medlog.data.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val archivedMedications: List<Medication> = emptyList(),
    val persistentReminder: Boolean = false,
    val persistentIntervalMinutes: Int = 5,
    val wakeHour: Int = 7, val wakeMinute: Int = 0,
    val breakfastHour: Int = 8, val breakfastMinute: Int = 0,
    val lunchHour: Int = 12, val lunchMinute: Int = 0,
    val dinnerHour: Int = 18, val dinnerMinute: Int = 0,
    val bedHour: Int = 22, val bedMinute: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: MedicationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getArchivedMedications()
                .catch { }
                .collect { archived ->
                    _uiState.value = _uiState.value.copy(archivedMedications = archived)
                }
        }
    }

    fun setPersistentReminder(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(persistentReminder = enabled)
    }

    fun setPersistentInterval(minutes: Int) {
        _uiState.value = _uiState.value.copy(persistentIntervalMinutes = minutes)
    }

    fun unarchiveMedication(id: Long) {
        viewModelScope.launch { repository.unarchiveMedication(id) }
    }

    fun updateRoutineTime(field: String, hour: Int, minute: Int) {
        _uiState.value = when (field) {
            "wake"      -> _uiState.value.copy(wakeHour = hour, wakeMinute = minute)
            "breakfast" -> _uiState.value.copy(breakfastHour = hour, breakfastMinute = minute)
            "lunch"     -> _uiState.value.copy(lunchHour = hour, lunchMinute = minute)
            "dinner"    -> _uiState.value.copy(dinnerHour = hour, dinnerMinute = minute)
            "bed"       -> _uiState.value.copy(bedHour = hour, bedMinute = minute)
            else        -> _uiState.value
        }
    }
}
