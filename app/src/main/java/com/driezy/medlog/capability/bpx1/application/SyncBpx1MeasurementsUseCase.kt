package com.driezy.medlog.capability.bpx1.application

import com.driezy.medlog.capability.bpx1.Bpx1BleClient
import com.driezy.medlog.capability.bpx1.Bpx1BluetoothAvailability
import com.driezy.medlog.capability.bpx1.Bpx1DeviceStore
import com.driezy.medlog.capability.bpx1.Bpx1Measurement
import com.driezy.medlog.capability.bpx1.Bpx1Protocol
import com.driezy.medlog.capability.bpx1.Bpx1ScanEvent
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

sealed interface Bpx1SyncState {
    data object NeedsConfiguration : Bpx1SyncState
    data object Scanning : Bpx1SyncState
    data class MeasurementImported(val measurement: Bpx1Measurement, val importedCount: Int) : Bpx1SyncState

    data object Finished : Bpx1SyncState
    data class Failed(val reason: Bpx1SyncFailure) : Bpx1SyncState
}

enum class Bpx1SyncFailure {
    BLUETOOTH_UNAVAILABLE,
    SCAN_FAILED,
    UNKNOWN,
}

/**
 * One-shot explicit BPX1 sync: scan MiBeacon advertisements for the configured
 * device and import any new measurement packets into the health repository.
 *
 * This is intentionally decoupled from the settings screen so the same flow
 * can be launched from the health-record entry path without exposing BLE state
 * to the UI layer.
 */
@Singleton
class SyncBpx1MeasurementsUseCase @Inject constructor(
    private val deviceStore: Bpx1DeviceStore,
    private val bleClient: Bpx1BleClient,
    private val importer: Bpx1MeasurementImporter,
) {
    suspend fun sync(
        scanDurationMillis: Long = DEFAULT_SCAN_DURATION_MILLIS,
        onEvent: suspend (Bpx1SyncState) -> Unit,
    ) {
        val configuration = deviceStore.configuration.value
        if (!configuration.isConfigured) {
            onEvent(Bpx1SyncState.NeedsConfiguration)
            return
        }
        if (bleClient.availability() != Bpx1BluetoothAvailability.READY) {
            onEvent(Bpx1SyncState.Failed(Bpx1SyncFailure.BLUETOOTH_UNAVAILABLE))
            return
        }

        val bindKey = deviceStore.getBindKey()
        val configuredMac = Bpx1Protocol.normalizeMac(configuration.macAddress)
        var importedCount = 0
        var failed = false
        onEvent(Bpx1SyncState.Scanning)
        withTimeoutOrNull(scanDurationMillis) {
            bleClient.scan(bindKey)
                .takeWhile { !failed }
                .collect { event ->
                    when (event) {
                        is Bpx1ScanEvent.Device -> {
                            if (!Bpx1Protocol.normalizeMac(event.device.macAddress)
                                    .equals(configuredMac, ignoreCase = true)
                            ) {
                                return@collect
                            }
                            val measurement = event.measurement ?: return@collect
                            val result = importer.import(event.device.macAddress, measurement)
                            if (result.inserted) {
                                importedCount += result.importedCount
                                onEvent(
                                    Bpx1SyncState.MeasurementImported(
                                        measurement = measurement,
                                        importedCount = importedCount,
                                    ),
                                )
                            }
                        }
                        is Bpx1ScanEvent.Failure -> {
                            failed = true
                            onEvent(Bpx1SyncState.Failed(Bpx1SyncFailure.SCAN_FAILED))
                        }
                    }
                }
        }
        if (!failed) {
            onEvent(Bpx1SyncState.Finished)
        }
    }

    private companion object {
        const val DEFAULT_SCAN_DURATION_MILLIS = 20_000L
    }
}
