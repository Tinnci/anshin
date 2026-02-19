package com.example.medlog.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.LogStatus
import com.example.medlog.data.model.Medication
import com.example.medlog.data.model.MedicationLog
import com.example.medlog.data.repository.MedicationRepository
import com.example.medlog.notification.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class MedicationWithStatus(
    val medication: Medication,
    val log: MedicationLog? = null,
) {
    val isTaken get() = log?.status == LogStatus.TAKEN
    val isSkipped get() = log?.status == LogStatus.SKIPPED
}

data class HomeUiState(
    val items: List<MedicationWithStatus> = emptyList(),
    val takenCount: Int = 0,
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val notificationHelper: NotificationHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeMedications()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeMedications() {
        viewModelScope.launch {
            val today = todayRange()
            combine(
                repository.getActiveMedications(),
                repository.getLogsForDateRange(today.first, today.second),
            ) { meds, logs ->
                val items = meds.map { med ->
                    val log = logs.find { it.medicationId == med.id }
                    MedicationWithStatus(med, log)
                }
                HomeUiState(
                    items = items,
                    takenCount = items.count { it.isTaken },
                    totalCount = items.size,
                    isLoading = false,
                )
            }.catch { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun toggleMedicationStatus(item: MedicationWithStatus) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val today = todayRange()
            if (item.isTaken) {
                // Undo
                item.log?.let { repository.deleteLog(it) }
                // Restore stock
                item.medication.stock?.let { stock ->
                    repository.updateStock(item.medication.id, stock + item.medication.dose)
                }
                // Re-schedule alarm
                notificationHelper.scheduleAlarm(item.medication, nextAlarmMs(item.medication))
            } else {
                // Mark taken
                repository.deleteLogsForDate(item.medication.id, today.first, today.second)
                repository.logMedication(
                    MedicationLog(
                        medicationId = item.medication.id,
                        scheduledTimeMs = scheduledMs(item.medication),
                        actualTakenTimeMs = now,
                        status = LogStatus.TAKEN,
                    )
                )
                // Deduct stock
                item.medication.stock?.let { stock ->
                    val newStock = (stock - item.medication.dose).coerceAtLeast(0.0)
                    repository.updateStock(item.medication.id, newStock)
                }
                notificationHelper.cancelReminder(item.medication.id)
            }
        }
    }

    fun skipMedication(item: MedicationWithStatus) {
        viewModelScope.launch {
            val today = todayRange()
            repository.deleteLogsForDate(item.medication.id, today.first, today.second)
            repository.logMedication(
                MedicationLog(
                    medicationId = item.medication.id,
                    scheduledTimeMs = scheduledMs(item.medication),
                    actualTakenTimeMs = null,
                    status = LogStatus.SKIPPED,
                )
            )
            notificationHelper.cancelReminder(item.medication.id)
        }
    }

    fun takeAll() {
        _uiState.value.items
            .filter { !it.isTaken && !it.isSkipped }
            .forEach { toggleMedicationStatus(it) }
    }

    private fun todayRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val end = start + 24 * 60 * 60 * 1000 - 1
        return start to end
    }

    private fun scheduledMs(med: Medication): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, med.reminderHour)
        cal.set(Calendar.MINUTE, med.reminderMinute)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun nextAlarmMs(med: Medication): Long {
        val ms = scheduledMs(med)
        return if (ms > System.currentTimeMillis()) ms else ms + 24 * 60 * 60 * 1000
    }
}
