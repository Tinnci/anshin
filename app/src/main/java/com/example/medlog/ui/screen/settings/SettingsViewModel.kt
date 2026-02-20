package com.example.medlog.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.Medication
import com.example.medlog.data.repository.MedicationRepository
import com.example.medlog.data.repository.UserPreferencesRepository
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
    private val prefsRepository: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.getArchivedMedications().catch { emit(emptyList()) },
        prefsRepository.settingsFlow,
    ) { archived, prefs ->
        SettingsUiState(
            archivedMedications     = archived,
            persistentReminder      = prefs.persistentReminder,
            persistentIntervalMinutes = prefs.persistentIntervalMinutes,
            wakeHour      = prefs.wakeHour,      wakeMinute      = prefs.wakeMinute,
            breakfastHour = prefs.breakfastHour, breakfastMinute = prefs.breakfastMinute,
            lunchHour     = prefs.lunchHour,     lunchMinute     = prefs.lunchMinute,
            dinnerHour    = prefs.dinnerHour,    dinnerMinute    = prefs.dinnerMinute,
            bedHour       = prefs.bedHour,       bedMinute       = prefs.bedMinute,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setPersistentReminder(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.updatePersistentReminder(enabled) }
    }

    fun setPersistentInterval(minutes: Int) {
        viewModelScope.launch { prefsRepository.updatePersistentInterval(minutes) }
    }

    fun unarchiveMedication(id: Long) {
        viewModelScope.launch { repository.unarchiveMedication(id) }
    }

    fun updateRoutineTime(field: String, hour: Int, minute: Int) {
        viewModelScope.launch { prefsRepository.updateRoutineTime(field, hour, minute) }
    }
}
