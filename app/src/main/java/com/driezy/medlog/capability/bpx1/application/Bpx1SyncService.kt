package com.driezy.medlog.capability.bpx1.application

import com.driezy.medlog.capability.bpx1.Bpx1BleClient
import com.driezy.medlog.capability.bpx1.Bpx1BluetoothAvailability
import com.driezy.medlog.capability.bpx1.Bpx1DeviceConfiguration
import com.driezy.medlog.capability.bpx1.Bpx1DeviceStore
import com.driezy.medlog.capability.bpx1.Bpx1Measurement
import com.driezy.medlog.capability.bpx1.Bpx1Protocol
import com.driezy.medlog.capability.bpx1.Bpx1ScanEvent
import com.driezy.medlog.capability.bpx1.Bpx1ScanMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

enum class Bpx1ServiceMode {
    UNCONFIGURED,
    AUTO_SYNC_DISABLED,
    BLUETOOTH_UNSUPPORTED,
    BLUETOOTH_OFF,
    PERMISSION_REQUIRED,
    IDLE,
    PASSIVE_LISTENING,
    ACTIVE_SYNCING,
}

data class Bpx1ServiceStatus(
    val configuration: Bpx1DeviceConfiguration = Bpx1DeviceConfiguration(),
    val bluetoothAvailability: Bpx1BluetoothAvailability = Bpx1BluetoothAvailability.READY,
    val mode: Bpx1ServiceMode = Bpx1ServiceMode.UNCONFIGURED,
    val isDeviceNearby: Boolean = false,
    val lastMeasurement: Bpx1Measurement? = null,
    val lastSyncedAtMillis: Long? = null,
    val sessionImportedCount: Int = 0,
) {
    val isAutoSyncActive: Boolean
        get() = mode == Bpx1ServiceMode.PASSIVE_LISTENING || mode == Bpx1ServiceMode.ACTIVE_SYNCING
}

sealed interface Bpx1AutoSyncEvent {
    data class MeasurementAutoImported(val measurement: Bpx1Measurement, val sessionImportedCount: Int) :
        Bpx1AutoSyncEvent
}

/**
 * Application-scoped coordinator for seamless BPX1 blood-pressure monitor syncing.
 *
 * Responsibilities:
 * 1. Maintains a hardware-filtered, duty-cycled passive BLE scan when the app is in the
 *    foreground and a paired BPX1 monitor has `autoImport` enabled.
 * 2. Deduplicates broadcast bursts in memory before querying Room, then imports new
 *    blood pressure + heart rate readings and records sync telemetry in [Bpx1DeviceStore].
 * 3. Arbitrates exclusive BLE access via [withManualOperation] so explicit scans or GATT
 *    connection checks pause passive listening and never collide or hit Android's
 *    5-scans-per-30-seconds rate limit.
 */
@Singleton
class Bpx1SyncService(
    private val deviceStore: Bpx1DeviceStore,
    private val bleClient: Bpx1BleClient,
    private val importer: Bpx1MeasurementImporter,
    private val clock: Clock,
    scope: CoroutineScope,
) {
    @Inject
    constructor(
        deviceStore: Bpx1DeviceStore,
        bleClient: Bpx1BleClient,
        importer: Bpx1MeasurementImporter,
        clock: Clock,
    ) : this(
        deviceStore = deviceStore,
        bleClient = bleClient,
        importer = importer,
        clock = clock,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    constructor(
        deviceStore: Bpx1DeviceStore,
        bleClient: Bpx1BleClient,
        importer: Bpx1MeasurementImporter,
        clock: Clock,
        dispatcher: CoroutineDispatcher,
    ) : this(
        deviceStore = deviceStore,
        bleClient = bleClient,
        importer = importer,
        clock = clock,
        scope = CoroutineScope(SupervisorJob() + dispatcher),
    )

    private val foregroundActive = MutableStateFlow(false)
    private val manualOpsCount = MutableStateFlow(0)
    private val permissionDenied = MutableStateFlow(false)
    private val availabilityState = MutableStateFlow(bleClient.availability())
    private val manualRefCounter = AtomicInteger(0)

    private val _status = MutableStateFlow(
        Bpx1ServiceStatus(
            configuration = deviceStore.configuration.value,
            bluetoothAvailability = availabilityState.value,
            mode = computeMode(
                config = deviceStore.configuration.value,
                availability = availabilityState.value,
                foreground = false,
                manualCount = 0,
                permDenied = false,
            ),
            lastSyncedAtMillis = deviceStore.configuration.value.lastSyncedAtMillis,
        ),
    )
    val status: StateFlow<Bpx1ServiceStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<Bpx1AutoSyncEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<Bpx1AutoSyncEvent> = _events.asSharedFlow()

    private var passiveScanJob: Job? = null
    private var lastProcessedBurstKey: String? = null

    init {
        scope.launch {
            bleClient.observeAvailability().collect { availability ->
                availabilityState.value = availability
            }
        }
        scope.launch {
            combine(
                deviceStore.configuration,
                availabilityState,
                foregroundActive,
                manualOpsCount,
                permissionDenied,
            ) { config, availability, foreground, manualCount, permDenied ->
                OrchestrationState(
                    config = config,
                    availability = availability,
                    foreground = foreground,
                    manualCount = manualCount,
                    permDenied = permDenied,
                )
            }.collectLatest { state ->
                val nextMode = computeMode(
                    config = state.config,
                    availability = state.availability,
                    foreground = state.foreground,
                    manualCount = state.manualCount,
                    permDenied = state.permDenied,
                )
                _status.update { current ->
                    current.copy(
                        configuration = state.config,
                        bluetoothAvailability = state.availability,
                        mode = nextMode,
                        isDeviceNearby = if (state.config.isConfigured) current.isDeviceNearby else false,
                        lastSyncedAtMillis = state.config.lastSyncedAtMillis ?: current.lastSyncedAtMillis,
                    )
                }
                if (nextMode == Bpx1ServiceMode.PASSIVE_LISTENING) {
                    runPassiveAutoSyncLoop(state.config)
                } else {
                    stopPassiveLoop()
                }
            }
        }
    }

    fun setForegroundActive(active: Boolean) {
        if (active) {
            availabilityState.value = bleClient.availability()
        }
        foregroundActive.value = active
    }

    fun refreshAvailabilityAndPermissions() {
        permissionDenied.value = false
        availabilityState.value = bleClient.availability()
    }

    suspend fun <T> withManualOperation(block: suspend () -> T): T {
        manualOpsCount.value = manualRefCounter.incrementAndGet()
        stopPassiveLoop()
        return try {
            block()
        } finally {
            manualOpsCount.value = manualRefCounter.decrementAndGet().coerceAtLeast(0)
        }
    }

    suspend fun recordImportedMeasurement(measurement: Bpx1Measurement, importedCount: Int, isAutoSync: Boolean) {
        val syncedAt = clock.millis()
        deviceStore.recordSyncedMeasurement(measurement, syncedAt)
        var updatedSessionCount = 0
        _status.update { current ->
            updatedSessionCount = current.sessionImportedCount + importedCount
            current.copy(
                isDeviceNearby = true,
                lastMeasurement = measurement,
                lastSyncedAtMillis = syncedAt,
                sessionImportedCount = updatedSessionCount,
            )
        }
        if (isAutoSync) {
            _events.tryEmit(
                Bpx1AutoSyncEvent.MeasurementAutoImported(
                    measurement = measurement,
                    sessionImportedCount = updatedSessionCount,
                ),
            )
        }
    }

    fun markDeviceNearby() {
        _status.update { it.copy(isDeviceNearby = true) }
    }

    private suspend fun runPassiveAutoSyncLoop(config: Bpx1DeviceConfiguration) {
        stopPassiveLoop()
        val configuredMac = Bpx1Protocol.normalizeMac(config.macAddress)
        val bindKey = deviceStore.getBindKey() ?: return

        passiveScanJob = kotlinx.coroutines.currentCoroutineContext()[Job]
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            var fatalFailure = false
            withTimeoutOrNull(PASSIVE_SCAN_WINDOW_MILLIS) {
                bleClient.scan(
                    bindKey = bindKey,
                    targetMacAddress = configuredMac,
                    scanMode = Bpx1ScanMode.PASSIVE,
                )
                    .takeWhile { !fatalFailure }
                    .collect { event ->
                        when (event) {
                            is Bpx1ScanEvent.Device -> {
                                if (!Bpx1Protocol.normalizeMac(event.device.macAddress)
                                        .equals(configuredMac, ignoreCase = true)
                                ) {
                                    return@collect
                                }
                                _status.update { it.copy(isDeviceNearby = true) }
                                val measurement = event.measurement ?: return@collect
                                val burstKey =
                                    "$configuredMac:${measurement.packetCounter}:${measurement.timestampMillis / 1_000L}"
                                if (burstKey == lastProcessedBurstKey) {
                                    return@collect
                                }
                                lastProcessedBurstKey = burstKey
                                val result = importer.import(configuredMac, measurement)
                                if (result.inserted) {
                                    recordImportedMeasurement(
                                        measurement = measurement,
                                        importedCount = result.importedCount,
                                        isAutoSync = true,
                                    )
                                }
                            }
                            is Bpx1ScanEvent.Failure -> {
                                fatalFailure = true
                                when (event.errorCode) {
                                    Bpx1ScanEvent.Failure.PERMISSION_DENIED -> {
                                        permissionDenied.value = true
                                    }
                                    Bpx1ScanEvent.Failure.BLUETOOTH_UNAVAILABLE -> {
                                        availabilityState.value = Bpx1BluetoothAvailability.DISABLED
                                    }
                                }
                            }
                        }
                    }
            }
            if (fatalFailure) break
            delay(PASSIVE_SCAN_COOLDOWN_MILLIS)
        }
    }

    private fun stopPassiveLoop() {
        passiveScanJob = null
    }

    private fun computeMode(
        config: Bpx1DeviceConfiguration,
        availability: Bpx1BluetoothAvailability,
        foreground: Boolean,
        manualCount: Int,
        permDenied: Boolean,
    ): Bpx1ServiceMode = when {
        manualCount > 0 -> Bpx1ServiceMode.ACTIVE_SYNCING
        !config.isConfigured -> Bpx1ServiceMode.UNCONFIGURED
        !config.autoImport -> Bpx1ServiceMode.AUTO_SYNC_DISABLED
        availability == Bpx1BluetoothAvailability.UNSUPPORTED -> Bpx1ServiceMode.BLUETOOTH_UNSUPPORTED
        availability == Bpx1BluetoothAvailability.DISABLED -> Bpx1ServiceMode.BLUETOOTH_OFF
        permDenied -> Bpx1ServiceMode.PERMISSION_REQUIRED
        foreground -> Bpx1ServiceMode.PASSIVE_LISTENING
        else -> Bpx1ServiceMode.IDLE
    }

    private data class OrchestrationState(
        val config: Bpx1DeviceConfiguration,
        val availability: Bpx1BluetoothAvailability,
        val foreground: Boolean,
        val manualCount: Int,
        val permDenied: Boolean,
    )

    companion object {
        const val PASSIVE_SCAN_WINDOW_MILLIS = 25_000L
        const val PASSIVE_SCAN_COOLDOWN_MILLIS = 5_000L
    }
}
