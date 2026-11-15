package com.driezy.medlog.capability.bpx1

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.driezy.medlog.R
import com.driezy.medlog.capability.bpx1.application.Bpx1MeasurementImporter
import com.driezy.medlog.capability.bpx1.application.Bpx1SyncService
import com.driezy.medlog.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/**
 * Receives hardware-offloaded BLE scan [PendingIntent] broadcasts when the app is
 * backgrounded or closed, decodes BPX1 MiBeacon measurements, imports them into Room,
 * and posts a lightweight confirmation notification.
 */
@AndroidEntryPoint
class Bpx1BackgroundScanReceiver : BroadcastReceiver() {

    @Inject lateinit var deviceStore: Bpx1DeviceStore

    @Inject lateinit var bleClient: Bpx1BleClient

    @Inject lateinit var importer: Bpx1MeasurementImporter

    @Inject lateinit var syncService: Bpx1SyncService

    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_SCAN_RESULT -> handleScanResult(context, intent)
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> rearmIfEnabled()
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_ON) {
                    rearmIfEnabled()
                }
            }
        }
    }

    private fun rearmIfEnabled() {
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val config = deviceStore.configuration.first()
                if (config.isConfigured && config.autoImport) {
                    bleClient.startBackgroundScan(config.macAddress)
                } else {
                    bleClient.stopBackgroundScan()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleScanResult(context: Context, intent: Intent) {
        val results = extractScanResults(intent)
        if (results.isEmpty()) return

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val config = deviceStore.configuration.first()
                if (!config.isConfigured || !config.autoImport) return@launch
                val bindKey = deviceStore.getBindKey() ?: return@launch
                val configuredMac = Bpx1Protocol.normalizeMac(config.macAddress)
                val seenCounters = HashSet<Int>()

                for (result in results) {
                    val address = runCatching { Bpx1Protocol.normalizeMac(result.device.address) }.getOrNull()
                        ?: continue
                    if (!address.equals(configuredMac, ignoreCase = true)) continue
                    val serviceData = result.scanRecord?.getServiceData(MIBEACON_PARCEL_UUID) ?: continue
                    val advertisement = Bpx1MiBeaconDecoder.decode(
                        serviceData = serviceData,
                        sourceMac = address,
                        bindKey = bindKey,
                        receivedAtMillis = clock.millis(),
                    ) ?: continue
                    val measurement = advertisement.measurement ?: continue
                    if (!seenCounters.add(measurement.packetCounter)) continue
                    val importResult = importer.import(configuredMac, measurement)
                    if (importResult.inserted) {
                        syncService.recordImportedMeasurement(
                            measurement = measurement,
                            importedCount = importResult.importedCount,
                            isAutoSync = false,
                        )
                        postImportedNotification(context, measurement)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun extractScanResults(intent: Intent): List<ScanResult> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java)
                .orEmpty()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<ScanResult>(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)
                .orEmpty()
        }

    private fun postImportedNotification(context: Context, measurement: Bpx1Measurement) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.bpx1_bg_sync_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager.createNotificationChannel(channel)

        val openAppIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.bpx1_bg_sync_notif_title))
            .setContentText(
                context.getString(
                    R.string.bpx1_bg_sync_notif_body,
                    measurement.systolic,
                    measurement.diastolic,
                    measurement.heartRate,
                ),
            )
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_SCAN_RESULT = "com.driezy.medlog.action.BPX1_BG_SCAN_RESULT"
        private const val CHANNEL_ID = "bpx1_background_sync"
        private const val NOTIFICATION_ID = 4202
        private val MIBEACON_PARCEL_UUID: ParcelUuid =
            ParcelUuid(UUID.fromString(Bpx1Protocol.MIBEACON_SERVICE_UUID))
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
