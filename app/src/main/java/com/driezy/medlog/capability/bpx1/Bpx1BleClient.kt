package com.driezy.medlog.capability.bpx1

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattConnectionSettings
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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

enum class Bpx1ScanMode {
    PASSIVE,
    LOW_LATENCY,
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

    /**
     * [errorCode] is either a platform `ScanCallback.SCAN_FAILED_*` code or one of
     * the app-defined negative codes below.
     */
    data class Failure(val errorCode: Int) : Bpx1ScanEvent {
        companion object {
            const val BLUETOOTH_UNAVAILABLE = -1
            const val PERMISSION_DENIED = -2
        }
    }
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
    fun observeAvailability(): Flow<Bpx1BluetoothAvailability> = flowOf(availability())
    fun scan(bindKey: ByteArray?): Flow<Bpx1ScanEvent>
    fun scan(
        bindKey: ByteArray?,
        targetMacAddress: String? = null,
        scanMode: Bpx1ScanMode = Bpx1ScanMode.LOW_LATENCY,
    ): Flow<Bpx1ScanEvent> = scan(bindKey)
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

    override fun observeAvailability(): Flow<Bpx1BluetoothAvailability> = callbackFlow {
        trySend(availability())
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            close()
            return@callbackFlow
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    trySend(availability())
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }.distinctUntilChanged()

    override fun scan(bindKey: ByteArray?): Flow<Bpx1ScanEvent> = scan(
        bindKey = bindKey,
        targetMacAddress = null,
        scanMode = Bpx1ScanMode.LOW_LATENCY,
    )

    @SuppressLint("MissingPermission")
    override fun scan(bindKey: ByteArray?, targetMacAddress: String?, scanMode: Bpx1ScanMode): Flow<Bpx1ScanEvent> =
        callbackFlow {
            val adapter = bluetoothManager?.adapter
            val scanner = adapter?.bluetoothLeScanner
            if (adapter == null || !adapter.isEnabled || scanner == null) {
                trySend(Bpx1ScanEvent.Failure(ERROR_BLUETOOTH_UNAVAILABLE))
                close()
                return@callbackFlow
            }
            val normalizedTargetMac = targetMacAddress
                ?.let(Bpx1Protocol::normalizeMac)
                ?.takeIf(Bpx1Protocol::isValidMac)
            val frameCache = ConcurrentHashMap<String, CachedFrame>()
            val bindKeyHash = bindKey?.contentHashCode() ?: 0

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val receivedAtMillis = clock.millis()
                    val serviceData = result.scanRecord
                        ?.getServiceData(MIBEACON_PARCEL_UUID)
                        ?: return
                    val address = runCatching { Bpx1Protocol.normalizeMac(result.device.address) }.getOrNull() ?: return
                    if (normalizedTargetMac != null && !address.equals(normalizedTargetMac, ignoreCase = true)) {
                        return
                    }
                    val payloadHash = 31 * serviceData.contentHashCode() + bindKeyHash
                    val cached = frameCache[address]
                    val advertisement = if (
                        cached != null &&
                        cached.payloadHash == payloadHash &&
                        receivedAtMillis - cached.cachedAtMillis <= FRAME_CACHE_TTL_MILLIS
                    ) {
                        cached.advertisement
                    } else {
                        Bpx1MiBeaconDecoder.decode(
                            serviceData = serviceData,
                            sourceMac = address,
                            bindKey = bindKey,
                            receivedAtMillis = receivedAtMillis,
                        )?.also { decoded ->
                            frameCache[address] = CachedFrame(
                                payloadHash = payloadHash,
                                advertisement = decoded,
                                cachedAtMillis = receivedAtMillis,
                            )
                        } ?: return
                    }
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
                .apply {
                    if (normalizedTargetMac != null) {
                        setDeviceAddress(normalizedTargetMac)
                    }
                }
                .build()
            val settings = ScanSettings.Builder()
                .setScanMode(
                    when (scanMode) {
                        Bpx1ScanMode.PASSIVE -> ScanSettings.SCAN_MODE_BALANCED
                        Bpx1ScanMode.LOW_LATENCY -> ScanSettings.SCAN_MODE_LOW_LATENCY
                    },
                )
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
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
            val gatt = if (Build.VERSION.SDK_INT >= 37) {
                val settings = BluetoothGattConnectionSettings.Builder()
                    .setTransport(android.bluetooth.BluetoothDevice.TRANSPORT_LE)
                    .setAutoConnectEnabled(false)
                    .build()
                device.connectGatt(settings, context.mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
            }
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

    private data class CachedFrame(
        val payloadHash: Int,
        val advertisement: Bpx1Advertisement,
        val cachedAtMillis: Long,
    )

    private companion object {
        val MIBEACON_PARCEL_UUID: ParcelUuid =
            ParcelUuid(UUID.fromString(Bpx1Protocol.MIBEACON_SERVICE_UUID))
        const val CONNECTION_TIMEOUT_MILLIS = 12_000L
        const val FRAME_CACHE_TTL_MILLIS = 30_000L
        const val ERROR_BLUETOOTH_UNAVAILABLE = Bpx1ScanEvent.Failure.BLUETOOTH_UNAVAILABLE
        const val ERROR_PERMISSION_DENIED = Bpx1ScanEvent.Failure.PERMISSION_DENIED
    }
}
