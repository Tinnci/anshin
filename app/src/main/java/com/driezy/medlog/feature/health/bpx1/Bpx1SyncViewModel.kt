package com.driezy.medlog.feature.health.bpx1

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driezy.medlog.capability.bpx1.Bpx1Measurement
import com.driezy.medlog.capability.bpx1.application.Bpx1SyncFailure
import com.driezy.medlog.capability.bpx1.application.Bpx1SyncState
import com.driezy.medlog.capability.bpx1.application.SyncBpx1MeasurementsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Bpx1SyncUiState(
    val isNeedsConfiguration: Boolean = false,
    val isScanning: Boolean = false,
    val isFinished: Boolean = false,
    val failure: Bpx1SyncFailure? = null,
    val lastImportedMeasurement: Bpx1Measurement? = null,
    val importedCount: Int = 0,
)

@HiltViewModel
class Bpx1SyncViewModel @Inject constructor(private val syncUseCase: SyncBpx1MeasurementsUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(Bpx1SyncUiState())
    val uiState: StateFlow<Bpx1SyncUiState> = _uiState.asStateFlow()

    private var syncJob: Job? = null

    fun startSync() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            _uiState.value = Bpx1SyncUiState()
            syncUseCase.sync { state ->
                when (state) {
                    Bpx1SyncState.NeedsConfiguration -> {
                        _uiState.update { it.copy(isNeedsConfiguration = true) }
                    }
                    Bpx1SyncState.Scanning -> {
                        _uiState.update { it.copy(isScanning = true) }
                    }
                    is Bpx1SyncState.MeasurementImported -> {
                        _uiState.update {
                            it.copy(
                                isScanning = true,
                                lastImportedMeasurement = state.measurement,
                                importedCount = state.importedCount,
                            )
                        }
                    }
                    Bpx1SyncState.Finished -> {
                        _uiState.update { it.copy(isScanning = false, isFinished = true) }
                    }
                    is Bpx1SyncState.Failed -> {
                        _uiState.update { it.copy(isScanning = false, failure = state.reason) }
                    }
                }
            }
        }
    }

    fun stopSync() {
        syncJob?.cancel()
        syncJob = null
        _uiState.update { it.copy(isScanning = false) }
    }

    override fun onCleared() {
        stopSync()
        super.onCleared()
    }
}
