package com.driezy.medlog.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driezy.medlog.data.model.HealthRecord
import com.driezy.medlog.data.model.HealthRecordSource
import com.driezy.medlog.data.model.HealthType
import com.driezy.medlog.data.repository.HealthRepository
import com.driezy.medlog.device.bpx1.Bpx1BleClient
import com.driezy.medlog.device.bpx1.Bpx1BluetoothAvailability
import com.driezy.medlog.device.bpx1.Bpx1ConnectionResult
import com.driezy.medlog.device.bpx1.Bpx1DeviceConfiguration
import com.driezy.medlog.device.bpx1.Bpx1DeviceStore
import com.driezy.medlog.device.bpx1.Bpx1DiscoveredDevice
import com.driezy.medlog.device.bpx1.Bpx1Measurement
import com.driezy.medlog.device.bpx1.Bpx1PayloadStatus
import com.driezy.medlog.device.bpx1.Bpx1Protocol
import com.driezy.medlog.device.bpx1.Bpx1ScanEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class Bpx1SettingsMessage {
    CONFIGURATION_SAVED,
    CONFIGURATION_CLEARED,
    SCAN_FAILED,
    MEASUREMENT_IMPORTED,
}

data class Bpx1DeviceSettingsUiState(
    val configuration: Bpx1DeviceConfiguration = Bpx1DeviceConfiguration(),
    val macInput: String = "",
    val bindKeyInput: String = "",
    val macInputInvalid: Boolean = false,
    val bindKeyInputInvalid: Boolean = false,
    val bluetoothAvailability: Bpx1BluetoothAvailability = Bpx1BluetoothAvailability.READY,
    val isScanning: Boolean = false,
    val isConnecting: Boolean = false,
    val discoveredDevices: List<Bpx1DiscoveredDevice> = emptyList(),
    val connectionResult: Bpx1ConnectionResult? = null,
    val configuredDevicePayloadStatus: Bpx1PayloadStatus? = null,
    val lastImportedMeasurement: Bpx1Measurement? = null,
    val importedMeasurementCount: Int = 0,
    val message: Bpx1SettingsMessage? = null,
)

@HiltViewModel
class Bpx1DeviceSettingsViewModel @Inject constructor(
    private val deviceStore: Bpx1DeviceStore,
    private val bleClient: Bpx1BleClient,
    private val healthRepository: HealthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        Bpx1DeviceSettingsUiState(
            configuration = deviceStore.configuration.value,
            macInput = deviceStore.configuration.value.macAddress,
            bluetoothAvailability = bleClient.availability(),
        ),
    )
    val uiState: StateFlow<Bpx1DeviceSettingsUiState> = _uiState.asStateFlow()
    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            deviceStore.configuration.collect { configuration ->
                _uiState.update { state ->
                    state.copy(
                        configuration = configuration,
                        macInput = if (state.macInput.isBlank()) configuration.macAddress else state.macInput,
                    )
                }
            }
        }
    }

    fun updateMacInput(value: String) {
        _uiState.update { it.copy(macInput = value, macInputInvalid = false, connectionResult = null) }
    }

    fun updateBindKeyInput(value: String) {
        _uiState.update { it.copy(bindKeyInput = value, bindKeyInputInvalid = false) }
    }

    fun chooseDevice(device: Bpx1DiscoveredDevice) {
        _uiState.update {
            it.copy(
                macInput = device.macAddress,
                macInputInvalid = false,
                configuredDevicePayloadStatus = device.payloadStatus,
                connectionResult = null,
            )
        }
    }

    fun saveConfiguration() {
        val state = _uiState.value
        val normalizedMac = Bpx1Protocol.normalizeMac(state.macInput)
        val newBindKey = state.bindKeyInput.takeIf { it.isNotBlank() }?.let(Bpx1Protocol::decodeBindKey)
        val macInvalid = !Bpx1Protocol.isValidMac(normalizedMac)
        val bindKeyInvalid = when {
            state.bindKeyInput.isNotBlank() -> newBindKey == null
            !state.configuration.hasBindKey -> true
            else -> false
        }
        if (macInvalid || bindKeyInvalid) {
            _uiState.update {
                it.copy(macInputInvalid = macInvalid, bindKeyInputInvalid = bindKeyInvalid)
            }
            return
        }
        viewModelScope.launch {
            deviceStore.save(normalizedMac, newBindKey)
            _uiState.update {
                it.copy(
                    macInput = normalizedMac,
                    bindKeyInput = "",
                    macInputInvalid = false,
                    bindKeyInputInvalid = false,
                    message = Bpx1SettingsMessage.CONFIGURATION_SAVED,
                )
            }
        }
    }

    fun clearConfiguration() {
        stopScan()
        viewModelScope.launch {
            deviceStore.clear()
            _uiState.update {
                it.copy(
                    macInput = "",
                    bindKeyInput = "",
                    discoveredDevices = emptyList(),
                    connectionResult = null,
                    configuredDevicePayloadStatus = null,
                    message = Bpx1SettingsMessage.CONFIGURATION_CLEARED,
                )
            }
        }
    }

    fun setAutoImport(enabled: Boolean) {
        viewModelScope.launch { deviceStore.setAutoImport(enabled) }
    }

    fun refreshBluetoothAvailability() {
        _uiState.update { it.copy(bluetoothAvailability = bleClient.availability()) }
    }

    fun startScan() {
        if (scanJob?.isActive == true) return
        val availability = bleClient.availability()
        if (availability != Bpx1BluetoothAvailability.READY) {
            _uiState.update { it.copy(bluetoothAvailability = availability) }
            return
        }
        scanJob = viewModelScope.launch {
            val bindKey = deviceStore.getBindKey()
            _uiState.update {
                it.copy(
                    bluetoothAvailability = availability,
                    isScanning = true,
                    discoveredDevices = emptyList(),
                    message = null,
                )
            }
            withTimeoutOrNull(SCAN_DURATION_MILLIS) {
                bleClient.scan(bindKey).collect { event ->
                    when (event) {
                        is Bpx1ScanEvent.Device -> handleScanDevice(event)
                        is Bpx1ScanEvent.Failure -> {
                            _uiState.update { it.copy(message = Bpx1SettingsMessage.SCAN_FAILED) }
                        }
                    }
                }
            }
            _uiState.update { it.copy(isScanning = false) }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false) }
    }

    fun checkConnection() {
        val macAddress = Bpx1Protocol.normalizeMac(_uiState.value.macInput)
        if (!Bpx1Protocol.isValidMac(macAddress)) {
            _uiState.update { it.copy(macInputInvalid = true) }
            return
        }
        if (_uiState.value.isConnecting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, connectionResult = null) }
            val result = bleClient.checkConnection(macAddress)
            _uiState.update { it.copy(isConnecting = false, connectionResult = result) }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun handleScanDevice(event: Bpx1ScanEvent.Device) {
        val configuredMac = Bpx1Protocol.normalizeMac(deviceStore.configuration.value.macAddress)
        _uiState.update { state ->
            val devices = (state.discoveredDevices.filterNot {
                it.macAddress.equals(event.device.macAddress, ignoreCase = true)
            } + event.device).sortedByDescending { it.rssi }
            state.copy(
                discoveredDevices = devices,
                configuredDevicePayloadStatus = if (configuredMac.equals(event.device.macAddress, ignoreCase = true)) {
                    event.device.payloadStatus
                } else {
                    state.configuredDevicePayloadStatus
                },
            )
        }
        val measurement = event.measurement ?: return
        val configuration = deviceStore.configuration.value
        if (!configuration.autoImport ||
            !Bpx1Protocol.normalizeMac(configuration.macAddress)
                .equals(event.device.macAddress, ignoreCase = true)
        ) {
            return
        }
        importMeasurement(event.device.macAddress, measurement)
    }

    private suspend fun importMeasurement(macAddress: String, measurement: Bpx1Measurement) {
        val baseKey = buildString {
            append("bpx1:")
            append(Bpx1Protocol.normalizeMac(macAddress).replace(":", "").lowercase())
            append(':')
            append(measurement.timestampMillis / 1_000L)
            append(':')
            append(measurement.systolic)
            append(':')
            append(measurement.diastolic)
            append(':')
            append(measurement.heartRate)
        }
        var inserted = false
        val bloodPressureKey = "$baseKey:bp"
        if (!healthRepository.hasSourceCacheKey(bloodPressureKey)) {
            healthRepository.addRecord(
                HealthRecord(
                    type = HealthType.BLOOD_PRESSURE.name,
                    value = measurement.systolic.toDouble(),
                    secondaryValue = measurement.diastolic.toDouble(),
                    timestamp = measurement.timestampMillis,
                    source = HealthRecordSource.IMPORT,
                    sourceProvider = Bpx1Protocol.MODEL,
                    sourceCacheKey = bloodPressureKey,
                    confirmedAt = System.currentTimeMillis(),
                ),
            )
            inserted = true
        }
        val heartRateKey = "$baseKey:hr"
        if (!healthRepository.hasSourceCacheKey(heartRateKey)) {
            healthRepository.addRecord(
                HealthRecord(
                    type = HealthType.HEART_RATE.name,
                    value = measurement.heartRate.toDouble(),
                    timestamp = measurement.timestampMillis,
                    source = HealthRecordSource.IMPORT,
                    sourceProvider = Bpx1Protocol.MODEL,
                    sourceCacheKey = heartRateKey,
                    confirmedAt = System.currentTimeMillis(),
                ),
            )
            inserted = true
        }
        if (inserted) {
            _uiState.update {
                it.copy(
                    lastImportedMeasurement = measurement,
                    importedMeasurementCount = it.importedMeasurementCount + 1,
                    message = Bpx1SettingsMessage.MEASUREMENT_IMPORTED,
                )
            }
        }
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }

    private companion object {
        const val SCAN_DURATION_MILLIS = 20_000L
    }
}
