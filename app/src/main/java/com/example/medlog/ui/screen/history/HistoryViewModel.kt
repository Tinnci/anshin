package com.example.medlog.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.MedicationLog
import com.example.medlog.data.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HistoryUiState(
    val groupedLogs: Map<String, List<MedicationLog>> = emptyMap(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val logRepo: LogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadLast30Days()
    }

    private fun loadLast30Days() {
        viewModelScope.launch {
            val end = System.currentTimeMillis()
            val start = end - 30L * 24 * 60 * 60 * 1000
            logRepo.getLogsForDateRange(start, end)
                .catch { }
                .collect { logs ->
                    val grouped = logs.groupBy { log ->
                        val cal = Calendar.getInstance().apply { timeInMillis = log.scheduledTimeMs }
                        "%d/%02d/%02d".format(
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH) + 1,
                            cal.get(Calendar.DAY_OF_MONTH),
                        )
                    }.toSortedMap(compareByDescending { it })
                    _uiState.value = HistoryUiState(groupedLogs = grouped, isLoading = false)
                }
        }
    }
}
