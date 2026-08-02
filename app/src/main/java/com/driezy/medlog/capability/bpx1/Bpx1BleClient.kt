package com.driezy.medlog.capability.bpx1

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

enum class Bpx1BluetoothAvailability {
    READY,
    DISABLED,
    UNSUPPORTED,
}

data class Bpx1DiscoveredDevice(
    val macAddress: String,
    val name: String,
    val rssi: Int,
    val registered: Boolean,
    val payloadStatus: Bpx1PayloadStatus,
    val lastSeenMillis: Long,
)

sealed interface Bpx1ScanEvent {
    data class Device(val device: Bpx1DiscoveredDevice, val measurement: Bpx1Measurement?) : Bpx1ScanEvent

    data class Failure(val errorCode: Int) : Bpx1ScanEvent
}

enum class Bpx1ConnectionFailure {
    INVALID_ADDRESS,
    BLUETOOTH_UNAVAILABLE,
    PERMISSION_DENIED,
    CONNECT_FAILED,
    SERVICE_DISCOVERY_FAILED,
    TIMEOUT,
}

sealed interface Bpx1ConnectionResult {
    data class Connected(
        val serviceCount: Int,
        val hasMiBeaconService: Boolean,
        val hasStandardBloodPressureService: Boolean,
    ) : Bpx1ConnectionResult

    data class Failed(val reason: Bpx1ConnectionFailure) : Bpx1ConnectionResult
}

interface Bpx1BleClient {
    fun availability(): Bpx1BluetoothAvailability
    fun scan(bindKey: ByteArray?): Flow<Bpx1ScanEvent>
    suspend fun checkConnection(macAddress: String): Bpx1ConnectionResult
}

@Singleton
class AndroidBpx1BleClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : Bpx1BleClient {
    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(BluetoothManager::class.java)
    }

    override fun availability(): Bpx1BluetoothAvailability {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return Bpx1BluetoothAvailability.UNSUPPORTED
        }
        val adapter = bluetoothManager?.adapter ?: return Bpx1BluetoothAvailability.UNSUPPORTED
        return if (adapter.isEnabled) Bpx1BluetoothAvailability.READY else Bpx1BluetoothAvailability.DISABLED
    }

    @SuppressLint("MissingPermission")
    override fun scan(bindKey: ByteArray?): Flow<Bpx1ScanEvent> = callbackFlow {
        val adapter = bluetoothManager?.adapter
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || scanner == null) {
            trySend(Bpx1ScanEvent.Failure(ERROR_BLUETOOTH_UNAVAILABLE))
            close()
            return@callbackFlow
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val receivedAtMillis = clock.millis()
                val serviceData = result.scanRecord
                    ?.getServiceData(MIBEACON_PARCEL_UUID)
                    ?: return
                val address = runCatching { Bpx1Protocol.normalizeMac(result.device.address) }.getOrNull() ?: return
                val advertisement = Bpx1MiBeaconDecoder.decode(
                    serviceData = serviceData,
                    sourceMac = address,
                    bindKey = bindKey,
                    receivedAtMillis = receivedAtMillis,
                ) ?: return
                val name = result.scanRecord?.deviceName
                    ?: runCatching { result.device.name }.getOrNull()
                    ?: "BPX1"
                trySend(
                    Bpx1ScanEvent.Device(
                        device = Bpx1DiscoveredDevice(
                            macAddress = address,
                            name = name,
                            rssi = result.rssi,
                            registered = advertisement.registered,
                            payloadStatus = advertisement.payloadStatus,
                            lastSeenMillis = receivedAtMillis,
                        ),
                        measurement = advertisement.measurement,
                    ),
                )
            }

            override fun onScanFailed(errorCode: Int) {
                trySend(Bpx1ScanEvent.Failure(errorCode))
                close()
            }
        }
        val filter = ScanFilter.Builder()
            .setServiceUuid(MIBEACON_PARCEL_UUID)
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()
        try {
            scanner.startScan(listOf(filter), settings, callback)
        } catch (_: SecurityException) {
            trySend(Bpx1ScanEvent.Failure(ERROR_PERMISSION_DENIED))
            close()
            return@callbackFlow
        }
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    @SuppressLint("MissingPermission")
    override suspend fun checkConnection(macAddress: String): Bpx1ConnectionResult {
        val normalizedMac = Bpx1Protocol.normalizeMac(macAddress)
        if (!Bpx1Protocol.isValidMac(normalizedMac)) {
            return Bpx1ConnectionResult.Failed(Bpx1ConnectionFailure.INVALID_ADDRESS)
        }
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            return Bpx1ConnectionResult.Failed(Bpx1ConnectionFailure.BLUETOOTH_UNAVAILABLE)
        }
        return try {
            withTimeoutOrNull(CONNECTION_TIMEOUT_MILLIS) {
                discoverServices(adapter, normalizedMac)
            } ?: Bpx1ConnectionResult.Failed(Bpx1ConnectionFailure.TIMEOUT)
        } catch (_: SecurityException) {
            Bpx1ConnectionResult.Failed(Bpx1ConnectionFailure.PERMISSION_DENIED)
        } catch (_: IllegalArgumentException) {
            Bpx1ConnectionResult.Failed(Bpx1ConnectionFailure.INVALID_ADDRESS)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun discoverServices(adapter: BluetoothAdapter, macAddress: String): Bpx1ConnectionResult =
        suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            val gattReference = AtomicReference<BluetoothGatt?>()

            fun finish(result: Bpx1ConnectionResult) {
                if (!completed.compareAndSet(false, true)) return
                val gatt = gattReference.getAndSet(null)
                runCatching { gatt?.disconnect() }
                runCatching { gatt?.close() }
                if (continuation.isActive) continuation.resume(result)
            }

            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        finish(Bpx1ConnectionResult.Failed(Bpx1ConnectionFailure.CONNECT_FAILED))
                        return
                    }
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            if (!gatt.discoverServices()) {
                                finish(Bpx1ConnectionResult.Failed(Bpx1ConnectionFailure.SERVICE_DISCOVERY_FAILED))
                            }
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            finish(Bpx1ConnectionResult.Failed(Bpx1ConnectionFailure.CONNECT_FAILED))
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        finish(Bpx1ConnectionResult.Failed(Bpx1ConnectionFailure.SERVICE_DISCOVERY_FAILED))
                        return
                    }
                    finish(gatt.services.toConnectionResult())
                }
            }

            continuation.invokeOnCancellation {
                completed.set(true)
                val gatt = gattReference.getAndSet(null)
                runCatching { gatt?.disconnect() }
                runCatching { gatt?.close() }
            }

            val device = adapter.getRemoteDevice(macAddress)
            val gatt = device.connectGatt(context, false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
            gattReference.set(gatt)
            if (gatt == null) {
                finish(Bpx1ConnectionResult.Failed(Bpx1ConnectionFailure.CONNECT_FAILED))
            }
        }

    private fun List<BluetoothGattService>.toConnectionResult(): Bpx1ConnectionResult.Connected {
        val normalizedUuids = map { it.uuid.toString().lowercase(Locale.ROOT) }.toSet()
        return Bpx1ConnectionResult.Connected(
            serviceCount = size,
            hasMiBeaconService = Bpx1Protocol.MIBEACON_SERVICE_UUID in normalizedUuids,
            hasStandardBloodPressureService = Bpx1Protocol.STANDARD_BLOOD_PRESSURE_SERVICE_UUID in normalizedUuids,
        )
    }

    private companion object {
        val MIBEACON_PARCEL_UUID: ParcelUuid =
            ParcelUuid(UUID.fromString(Bpx1Protocol.MIBEACON_SERVICE_UUID))
        const val CONNECTION_TIMEOUT_MILLIS = 12_000L
        const val ERROR_BLUETOOTH_UNAVAILABLE = -1
        const val ERROR_PERMISSION_DENIED = -2
    }
}
