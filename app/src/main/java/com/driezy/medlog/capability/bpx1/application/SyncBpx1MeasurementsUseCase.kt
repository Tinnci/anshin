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

    /** Scanning started; the scan stops on its own after [durationMillis]. */
    data class Scanning(val durationMillis: Long) : Bpx1SyncState

    /** The configured monitor was heard for the first time in this session. */
    data object DeviceDetected : Bpx1SyncState
    data class MeasurementImported(val measurement: Bpx1Measurement, val importedCount: Int) : Bpx1SyncState

    /**
     * The scan window ended. [deviceDetected] separates "the monitor was never
     * heard" from "the monitor was heard but had nothing new" when nothing was imported.
     */
    data class Finished(val deviceDetected: Boolean) : Bpx1SyncState
    data class Failed(val reason: Bpx1SyncFailure) : Bpx1SyncState
}

enum class Bpx1SyncFailure {
    BLUETOOTH_DISABLED,
    BLUETOOTH_UNSUPPORTED,
    PERMISSION_DENIED,
    SCAN_FAILED,
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
    fun isConfigured(): Boolean = deviceStore.configuration.value.isConfigured

    suspend fun sync(
        scanDurationMillis: Long = DEFAULT_SCAN_DURATION_MILLIS,
        onEvent: suspend (Bpx1SyncState) -> Unit,
    ) {
        val configuration = deviceStore.configuration.value
        if (!configuration.isConfigured) {
            onEvent(Bpx1SyncState.NeedsConfiguration)
            return
        }
        when (bleClient.availability()) {
            Bpx1BluetoothAvailability.READY -> Unit
            Bpx1BluetoothAvailability.DISABLED -> {
                onEvent(Bpx1SyncState.Failed(Bpx1SyncFailure.BLUETOOTH_DISABLED))
                return
            }
            Bpx1BluetoothAvailability.UNSUPPORTED -> {
                onEvent(Bpx1SyncState.Failed(Bpx1SyncFailure.BLUETOOTH_UNSUPPORTED))
                return
            }
        }

        val bindKey = deviceStore.getBindKey()
        val configuredMac = Bpx1Protocol.normalizeMac(configuration.macAddress)
        var importedCount = 0
        var deviceDetected = false
        var failed = false
        onEvent(Bpx1SyncState.Scanning(scanDurationMillis))
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
                            if (!deviceDetected) {
                                deviceDetected = true
                                onEvent(Bpx1SyncState.DeviceDetected)
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
                            onEvent(Bpx1SyncState.Failed(event.toSyncFailure()))
                        }
                    }
                }
        }
        if (!failed) {
            onEvent(Bpx1SyncState.Finished(deviceDetected))
        }
    }

    private fun Bpx1ScanEvent.Failure.toSyncFailure(): Bpx1SyncFailure = when (errorCode) {
        Bpx1ScanEvent.Failure.PERMISSION_DENIED -> Bpx1SyncFailure.PERMISSION_DENIED
        Bpx1ScanEvent.Failure.BLUETOOTH_UNAVAILABLE -> Bpx1SyncFailure.BLUETOOTH_DISABLED
        else -> Bpx1SyncFailure.SCAN_FAILED
    }

    companion object {
        /**
         * A cuff measurement takes roughly 30–60 s from inflation to result, and the
         * BPX1 only broadcasts once it has a result, so the window must cover a full
         * measurement started after the user opens the sync screen.
         */
        const val DEFAULT_SCAN_DURATION_MILLIS = 60_000L
    }
}
