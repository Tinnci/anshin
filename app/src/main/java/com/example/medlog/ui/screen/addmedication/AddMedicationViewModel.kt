package com.example.medlog.ui.screen.addmedication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.Medication
import com.example.medlog.data.model.TimePeriod
import com.example.medlog.data.repository.MedicationRepository
import com.example.medlog.notification.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class AddMedicationUiState(
    val name: String = "",
    val dose: String = "1",
    val doseUnit: String = "片",
    val category: String = "",
    val timePeriod: TimePeriod = TimePeriod.EXACT,
    val reminderHour: Int = 8,
    val reminderMinute: Int = 0,
    val daysOfWeek: String = "1,2,3,4,5,6,7",
    val stock: String = "",
    val refillThreshold: String = "",
    val note: String = "",
    val isCustomDrug: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AddMedicationViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val notificationHelper: NotificationHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddMedicationUiState())
    val uiState: StateFlow<AddMedicationUiState> = _uiState.asStateFlow()

    fun loadExisting(medicationId: Long) {
        viewModelScope.launch {
            val med = repository.getMedicationById(medicationId) ?: return@launch
            _uiState.value = AddMedicationUiState(
                name              = med.name,
                dose              = med.dose.toString(),
                doseUnit          = med.doseUnit,
                category          = med.category,
                timePeriod        = TimePeriod.fromKey(med.timePeriod),
                reminderHour      = med.reminderHour,
                reminderMinute    = med.reminderMinute,
                daysOfWeek        = med.daysOfWeek,
                stock             = med.stock?.toString() ?: "",
                refillThreshold   = med.refillThreshold?.toString() ?: "",
                note              = med.note,
                isCustomDrug      = med.isCustomDrug,
            )
        }
    }

    fun onNameChange(v: String)            { _uiState.value = _uiState.value.copy(name = v) }
    fun onDoseChange(v: String)            { _uiState.value = _uiState.value.copy(dose = v) }
    fun onDoseUnitChange(v: String)        { _uiState.value = _uiState.value.copy(doseUnit = v) }
    fun onCategoryChange(v: String)        { _uiState.value = _uiState.value.copy(category = v) }
    fun onTimePeriodChange(v: TimePeriod)  { _uiState.value = _uiState.value.copy(timePeriod = v) }
    fun onReminderTimeChange(h: Int, m: Int) {
        _uiState.value = _uiState.value.copy(reminderHour = h, reminderMinute = m)
    }
    fun onStockChange(v: String)           { _uiState.value = _uiState.value.copy(stock = v) }
    fun onRefillThresholdChange(v: String) { _uiState.value = _uiState.value.copy(refillThreshold = v) }
    fun onNoteChange(v: String)            { _uiState.value = _uiState.value.copy(note = v) }

    fun save(existingId: Long?) {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.value = state.copy(error = "药品名称不能为空")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            val medication = Medication(
                id               = existingId ?: 0,
                name             = state.name.trim(),
                dose             = state.dose.toDoubleOrNull() ?: 1.0,
                doseUnit         = state.doseUnit,
                category         = state.category.trim(),
                timePeriod       = state.timePeriod.key,
                reminderHour     = state.reminderHour,
                reminderMinute   = state.reminderMinute,
                daysOfWeek       = state.daysOfWeek,
                stock            = state.stock.toDoubleOrNull(),
                refillThreshold  = state.refillThreshold.toDoubleOrNull(),
                note             = state.note,
                isCustomDrug     = state.isCustomDrug,
            )
            if (existingId == null) {
                val newId = repository.addMedication(medication)
                notificationHelper.scheduleAlarm(
                    medication.copy(id = newId),
                    nextAlarmMs(state.reminderHour, state.reminderMinute),
                )
            } else {
                repository.updateMedication(medication)
                notificationHelper.cancelAlarm(existingId)
                notificationHelper.scheduleAlarm(
                    medication,
                    nextAlarmMs(state.reminderHour, state.reminderMinute),
                )
            }
            _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
        }
    }

    private fun nextAlarmMs(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }
}
