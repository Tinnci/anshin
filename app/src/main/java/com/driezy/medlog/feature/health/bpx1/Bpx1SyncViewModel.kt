package com.driezy.medlog.feature.health.bpx1

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driezy.medlog.capability.bpx1.Bpx1Measurement
import com.driezy.medlog.capability.bpx1.application.Bpx1SyncFailure
import com.driezy.medlog.capability.bpx1.application.Bpx1SyncState
import com.driezy.medlog.capability.bpx1.application.SyncBpx1MeasurementsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** Whether the configured monitor has been heard during this session. */
    val deviceDetected: Boolean = false,
    val scanDurationSeconds: Int = 0,
    val remainingSeconds: Int = 0,
)

@HiltViewModel
class Bpx1SyncViewModel @Inject constructor(private val syncUseCase: SyncBpx1MeasurementsUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(Bpx1SyncUiState())
    val uiState: StateFlow<Bpx1SyncUiState> = _uiState.asStateFlow()

    private var syncJob: Job? = null
    private var countdownJob: Job? = null

    fun isConfigured(): Boolean = syncUseCase.isConfigured()

    fun startSync() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            _uiState.value = Bpx1SyncUiState()
            syncUseCase.sync { state ->
                when (state) {
                    Bpx1SyncState.NeedsConfiguration -> {
                        _uiState.update { it.copy(isNeedsConfiguration = true) }
                    }
                    is Bpx1SyncState.Scanning -> {
                        val seconds = (state.durationMillis / 1_000L).toInt()
                        _uiState.update {
                            it.copy(isScanning = true, scanDurationSeconds = seconds, remainingSeconds = seconds)
                        }
                        startCountdown(seconds)
                    }
                    Bpx1SyncState.DeviceDetected -> {
                        _uiState.update { it.copy(deviceDetected = true) }
                    }
                    is Bpx1SyncState.MeasurementImported -> {
                        _uiState.update {
                            it.copy(
                                isScanning = true,
                                deviceDetected = true,
                                lastImportedMeasurement = state.measurement,
                                importedCount = state.importedCount,
                            )
                        }
                    }
                    is Bpx1SyncState.Finished -> {
                        countdownJob?.cancel()
                        _uiState.update {
                            it.copy(
                                isScanning = false,
                                isFinished = true,
                                deviceDetected = state.deviceDetected,
                                remainingSeconds = 0,
                            )
                        }
                    }
                    is Bpx1SyncState.Failed -> {
                        countdownJob?.cancel()
                        _uiState.update { it.copy(isScanning = false, failure = state.reason) }
                    }
                }
            }
        }
    }

    /** The user declined the Bluetooth permission request, so scanning cannot start. */
    fun onPermissionDenied() {
        stopSync()
        _uiState.value = Bpx1SyncUiState(failure = Bpx1SyncFailure.PERMISSION_DENIED)
    }

    fun stopSync() {
        syncJob?.cancel()
        syncJob = null
        countdownJob?.cancel()
        countdownJob = null
        _uiState.update { it.copy(isScanning = false) }
    }

    private fun startCountdown(seconds: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (remaining in seconds - 1 downTo 0) {
                delay(1_000L)
                _uiState.update { if (it.isScanning) it.copy(remainingSeconds = remaining) else it }
            }
        }
    }

    override fun onCleared() {
        stopSync()
        super.onCleared()
    }
}
