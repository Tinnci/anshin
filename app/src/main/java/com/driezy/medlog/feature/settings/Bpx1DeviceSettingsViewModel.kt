package com.driezy.medlog.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driezy.medlog.capability.bpx1.Bpx1BleClient
import com.driezy.medlog.capability.bpx1.Bpx1BluetoothAvailability
import com.driezy.medlog.capability.bpx1.Bpx1ConnectionResult
import com.driezy.medlog.capability.bpx1.Bpx1DeviceConfiguration
import com.driezy.medlog.capability.bpx1.Bpx1DeviceStore
import com.driezy.medlog.capability.bpx1.Bpx1DiscoveredDevice
import com.driezy.medlog.capability.bpx1.Bpx1Measurement
import com.driezy.medlog.capability.bpx1.Bpx1PayloadStatus
import com.driezy.medlog.capability.bpx1.Bpx1Protocol
import com.driezy.medlog.capability.bpx1.Bpx1ScanEvent
import com.driezy.medlog.capability.bpx1.application.Bpx1MeasurementImporter
import com.driezy.medlog.capability.bpx1.canRetainBindKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

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
    val hasAttemptedScan: Boolean = false,
    val discoveredDevices: List<Bpx1DiscoveredDevice> = emptyList(),
    val connectionResult: Bpx1ConnectionResult? = null,
    val configuredDevicePayloadStatus: Bpx1PayloadStatus? = null,
    val lastImportedMeasurement: Bpx1Measurement? = null,
    val importedMeasurementCount: Int = 0,
) {
    val isBusy: Boolean get() = isScanning || isConnecting
    val canRetainStoredBindKey: Boolean get() = canRetainBindKey(configuration, macInput)
}

sealed interface Bpx1SettingsUiAction {
    data class MacChanged(val value: String) : Bpx1SettingsUiAction
    data class BindKeyChanged(val value: String) : Bpx1SettingsUiAction
    data class DeviceSelected(val device: Bpx1DiscoveredDevice) : Bpx1SettingsUiAction
    data object ConfigurationSaved : Bpx1SettingsUiAction
    data object ConfigurationCleared : Bpx1SettingsUiAction
    data class AutoImportChanged(val enabled: Boolean) : Bpx1SettingsUiAction
    data object AvailabilityRefreshed : Bpx1SettingsUiAction
    data object ScanStarted : Bpx1SettingsUiAction
    data object ScanStopped : Bpx1SettingsUiAction
    data object ConnectionChecked : Bpx1SettingsUiAction
}

sealed interface Bpx1SettingsUiEffect {
    data class Message(val value: Bpx1SettingsMessage) : Bpx1SettingsUiEffect
}

@HiltViewModel
class Bpx1DeviceSettingsViewModel @Inject constructor(
    private val deviceStore: Bpx1DeviceStore,
    private val bleClient: Bpx1BleClient,
    private val measurementImporter: Bpx1MeasurementImporter,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        Bpx1DeviceSettingsUiState(
            configuration = deviceStore.configuration.value,
            macInput = deviceStore.configuration.value.macAddress,
            bluetoothAvailability = bleClient.availability(),
        ),
    )
    val uiState: StateFlow<Bpx1DeviceSettingsUiState> = _uiState.asStateFlow()
    private val effectChannel = Channel<Bpx1SettingsUiEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()
    private var scanJob: Job? = null
    private var connectionJob: Job? = null

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

    fun onAction(action: Bpx1SettingsUiAction) {
        when (action) {
            is Bpx1SettingsUiAction.MacChanged -> updateMacInput(action.value)
            is Bpx1SettingsUiAction.BindKeyChanged -> updateBindKeyInput(action.value)
            is Bpx1SettingsUiAction.DeviceSelected -> chooseDevice(action.device)
            Bpx1SettingsUiAction.ConfigurationSaved -> saveConfiguration()
            Bpx1SettingsUiAction.ConfigurationCleared -> clearConfiguration()
            is Bpx1SettingsUiAction.AutoImportChanged -> setAutoImport(action.enabled)
            Bpx1SettingsUiAction.AvailabilityRefreshed -> refreshBluetoothAvailability()
            Bpx1SettingsUiAction.ScanStarted -> startScan()
            Bpx1SettingsUiAction.ScanStopped -> stopScan()
            Bpx1SettingsUiAction.ConnectionChecked -> checkConnection()
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
            !state.canRetainStoredBindKey -> true
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
                )
            }
            effectChannel.send(Bpx1SettingsUiEffect.Message(Bpx1SettingsMessage.CONFIGURATION_SAVED))
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
                    hasAttemptedScan = false,
                    connectionResult = null,
                    configuredDevicePayloadStatus = null,
                )
            }
            effectChannel.send(Bpx1SettingsUiEffect.Message(Bpx1SettingsMessage.CONFIGURATION_CLEARED))
        }
    }

    fun setAutoImport(enabled: Boolean) {
        viewModelScope.launch { deviceStore.setAutoImport(enabled) }
    }

    fun refreshBluetoothAvailability() {
        _uiState.update { it.copy(bluetoothAvailability = bleClient.availability()) }
    }

    fun startScan() {
        if (scanJob?.isActive == true || connectionJob?.isActive == true) return
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
                    hasAttemptedScan = true,
                    discoveredDevices = emptyList(),
                    connectionResult = null,
                )
            }
            withTimeoutOrNull(SCAN_DURATION_MILLIS) {
                bleClient.scan(bindKey).collect { event ->
                    when (event) {
                        is Bpx1ScanEvent.Device -> handleScanDevice(event)
                        is Bpx1ScanEvent.Failure -> {
                            effectChannel.send(Bpx1SettingsUiEffect.Message(Bpx1SettingsMessage.SCAN_FAILED))
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
        if (connectionJob?.isActive == true) return
        stopScan()
        connectionJob = viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, connectionResult = null) }
            try {
                val result = bleClient.checkConnection(macAddress)
                _uiState.update { it.copy(connectionResult = result) }
            } finally {
                _uiState.update { it.copy(isConnecting = false) }
            }
        }
    }

    private suspend fun handleScanDevice(event: Bpx1ScanEvent.Device) {
        val configuredMac = Bpx1Protocol.normalizeMac(deviceStore.configuration.value.macAddress)
        _uiState.update { state ->
            val devices = state.discoveredDevices
                .filterNot {
                    it.macAddress.equals(event.device.macAddress, ignoreCase = true)
                }
                .plus(event.device)
                .sortedByDescending { it.rssi }
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
        val result = measurementImporter.import(macAddress, measurement)
        if (result.inserted) {
            _uiState.update {
                it.copy(
                    lastImportedMeasurement = measurement,
                    importedMeasurementCount = it.importedMeasurementCount + result.importedCount,
                )
            }
            effectChannel.send(Bpx1SettingsUiEffect.Message(Bpx1SettingsMessage.MEASUREMENT_IMPORTED))
        }
    }

    override fun onCleared() {
        stopScan()
        connectionJob?.cancel()
        connectionJob = null
        super.onCleared()
    }

    private companion object {
        const val SCAN_DURATION_MILLIS = 20_000L
    }
}
