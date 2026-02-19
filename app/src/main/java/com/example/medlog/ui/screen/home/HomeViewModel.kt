package com.example.medlog.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.LogStatus
import com.example.medlog.data.model.Medication
import com.example.medlog.data.model.MedicationLog
import com.example.medlog.data.repository.LogRepository
import com.example.medlog.data.repository.MedicationRepository
import com.example.medlog.notification.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val medicationRepo: MedicationRepository,
    private val logRepo: LogRepository,
    private val notificationHelper: NotificationHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeMedications()
    }

    private fun observeMedications() {
        viewModelScope.launch {
            val today = todayRange()
            combine(
                medicationRepo.getActiveMedications(),
                logRepo.getLogsForDateRange(today.first, today.second),
            ) { meds, logs ->
                val items = meds.map { med ->
                    MedicationWithStatus(med, logs.find { it.medicationId == med.id })
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
            }.collect { _uiState.value = it }
        }
    }

    fun toggleMedicationStatus(item: MedicationWithStatus) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val today = todayRange()
            if (item.isTaken) {
                item.log?.let { logRepo.deleteLog(it) }
                item.medication.stock?.let { stock ->
                    medicationRepo.updateStock(item.medication.id, stock + item.medication.doseQuantity)
                }
                notificationHelper.scheduleAllReminders(item.medication)
            } else {
                logRepo.deleteLogsForDate(item.medication.id, today.first, today.second)
                logRepo.insertLog(
                    MedicationLog(
                        medicationId = item.medication.id,
                        scheduledTimeMs = scheduledMs(item.medication),
                        actualTakenTimeMs = now,
                        status = LogStatus.TAKEN,
                    )
                )
                item.medication.stock?.let { stock ->
                    medicationRepo.updateStock(
                        item.medication.id,
                        (stock - item.medication.doseQuantity).coerceAtLeast(0.0),
                    )
                }
                notificationHelper.cancelAllReminders(item.medication.id)
            }
        }
    }

    fun skipMedication(item: MedicationWithStatus) {
        viewModelScope.launch {
            val today = todayRange()
            logRepo.deleteLogsForDate(item.medication.id, today.first, today.second)
            logRepo.insertLog(
                MedicationLog(
                    medicationId = item.medication.id,
                    scheduledTimeMs = scheduledMs(item.medication),
                    actualTakenTimeMs = null,
                    status = LogStatus.SKIPPED,
                )
            )
            notificationHelper.cancelAllReminders(item.medication.id)
        }
    }

    fun takeAll() {
        _uiState.value.items
            .filter { !it.isTaken && !it.isSkipped }
            .forEach { toggleMedicationStatus(it) }
    }

    private fun todayRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        return start to (start + 24 * 60 * 60 * 1000 - 1)
    }

    private fun scheduledMs(med: Medication): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, med.reminderHour)
            set(Calendar.MINUTE, med.reminderMinute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}

