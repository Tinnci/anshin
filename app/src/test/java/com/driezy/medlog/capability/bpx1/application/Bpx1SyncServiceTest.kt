package com.driezy.medlog.capability.bpx1.application

import com.driezy.medlog.capability.bpx1.Bpx1BleClient
import com.driezy.medlog.capability.bpx1.Bpx1BluetoothAvailability
import com.driezy.medlog.capability.bpx1.Bpx1ConnectionFailure
import com.driezy.medlog.capability.bpx1.Bpx1ConnectionResult
import com.driezy.medlog.capability.bpx1.Bpx1DeviceConfiguration
import com.driezy.medlog.capability.bpx1.Bpx1DiscoveredDevice
import com.driezy.medlog.capability.bpx1.Bpx1Measurement
import com.driezy.medlog.capability.bpx1.Bpx1PayloadStatus
import com.driezy.medlog.capability.bpx1.Bpx1ScanEvent
import com.driezy.medlog.capability.bpx1.Bpx1ScanMode
import com.driezy.medlog.capability.bpx1.InMemoryBpx1DeviceStore
import com.driezy.medlog.data.repository.FakeHealthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class Bpx1SyncServiceTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000L), ZoneOffset.UTC)

    @Test
    fun `passive auto sync imports measurement once updates device store and emits event`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryBpx1DeviceStore(
            initial = Bpx1DeviceConfiguration(macAddress = MAC, autoImport = true),
            bindKey = ByteArray(16) { it.toByte() },
        )
        val repository = FakeHealthRepository()
        val importer = Bpx1MeasurementImporter(repository, clock)
        val bleClient = FakeReactiveBleClient(
            events = listOf(
                deviceEvent(sampleMeasurement),
                deviceEvent(sampleMeasurement),
            ),
        )
        val service = Bpx1SyncService(
            deviceStore = store,
            bleClient = bleClient,
            importer = importer,
            clock = clock,
            dispatcher = dispatcher,
        )
        val autoEventDeferred = async(dispatcher) { service.events.first() }

        service.setForegroundActive(true)
        runCurrent()

        assertEquals(Bpx1ServiceMode.PASSIVE_LISTENING, service.status.value.mode)
        assertTrue(service.status.value.isDeviceNearby)
        assertEquals(1, service.status.value.sessionImportedCount)
        assertEquals(sampleMeasurement, service.status.value.lastMeasurement)
        assertEquals(122, store.configuration.value.lastSystolic)
        assertEquals(78, store.configuration.value.lastDiastolic)
        assertEquals(64, store.configuration.value.lastHeartRate)
        assertEquals(clock.millis(), store.configuration.value.lastSyncedAtMillis)
        assertEquals(Bpx1ScanMode.PASSIVE, bleClient.lastRequestedScanMode)
        assertEquals(MAC, bleClient.lastRequestedTargetMac)

        val autoEvent = autoEventDeferred.await() as Bpx1AutoSyncEvent.MeasurementAutoImported
        assertEquals(sampleMeasurement, autoEvent.measurement)
        assertEquals(1, autoEvent.sessionImportedCount)

        val records = repository.getAllRecords().first()
        assertEquals(2, records.size)

        service.setForegroundActive(false)
        runCurrent()
        assertEquals(Bpx1ServiceMode.IDLE, service.status.value.mode)
    }

    @Test
    fun `manual operation arbitrates exclusive scan and resumes passive listening afterward`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryBpx1DeviceStore(
            initial = Bpx1DeviceConfiguration(macAddress = MAC, autoImport = true),
            bindKey = ByteArray(16) { it.toByte() },
        )
        val service = Bpx1SyncService(
            deviceStore = store,
            bleClient = FakeReactiveBleClient(holdScanOpen = true),
            importer = Bpx1MeasurementImporter(FakeHealthRepository(), clock),
            clock = clock,
            dispatcher = dispatcher,
        )

        service.setForegroundActive(true)
        runCurrent()
        assertEquals(Bpx1ServiceMode.PASSIVE_LISTENING, service.status.value.mode)

        var modeInsideManual: Bpx1ServiceMode? = null
        service.withManualOperation {
            runCurrent()
            modeInsideManual = service.status.value.mode
        }
        runCurrent()

        assertEquals(Bpx1ServiceMode.ACTIVE_SYNCING, modeInsideManual)
        assertEquals(Bpx1ServiceMode.PASSIVE_LISTENING, service.status.value.mode)

        service.setForegroundActive(false)
        runCurrent()
    }

    @Test
    fun `bluetooth state and permission recovery update service mode reactively`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryBpx1DeviceStore(
            initial = Bpx1DeviceConfiguration(macAddress = MAC, autoImport = true),
            bindKey = ByteArray(16) { it.toByte() },
        )
        val bleClient = FakeReactiveBleClient(
            initialAvailability = Bpx1BluetoothAvailability.DISABLED,
            events = listOf(Bpx1ScanEvent.Failure(Bpx1ScanEvent.Failure.PERMISSION_DENIED)),
        )
        val service = Bpx1SyncService(
            deviceStore = store,
            bleClient = bleClient,
            importer = Bpx1MeasurementImporter(FakeHealthRepository(), clock),
            clock = clock,
            dispatcher = dispatcher,
        )

        service.setForegroundActive(true)
        runCurrent()
        assertEquals(Bpx1ServiceMode.BLUETOOTH_OFF, service.status.value.mode)

        bleClient.setAvailability(Bpx1BluetoothAvailability.READY)
        runCurrent()
        assertEquals(Bpx1ServiceMode.PERMISSION_REQUIRED, service.status.value.mode)

        bleClient.events = emptyList()
        bleClient.holdScanOpen = true
        service.refreshAvailabilityAndPermissions()
        runCurrent()
        assertEquals(Bpx1ServiceMode.PASSIVE_LISTENING, service.status.value.mode)

        service.setForegroundActive(false)
        advanceTimeBy(1_000L)
        runCurrent()
    }

    private fun deviceEvent(measurement: Bpx1Measurement) = Bpx1ScanEvent.Device(
        device = Bpx1DiscoveredDevice(
            macAddress = MAC,
            name = "BPX1",
            rssi = -50,
            registered = true,
            payloadStatus = Bpx1PayloadStatus.DECRYPTED,
            lastSeenMillis = measurement.timestampMillis,
        ),
        measurement = measurement,
    )

    private val sampleMeasurement = Bpx1Measurement(
        timestampMillis = 1_720_000_000_000L,
        systolic = 122,
        diastolic = 78,
        heartRate = 64,
        flags = 0,
        packetCounter = 9,
        objectId = 0x3001,
    )

    private class FakeReactiveBleClient(
        initialAvailability: Bpx1BluetoothAvailability = Bpx1BluetoothAvailability.READY,
        var events: List<Bpx1ScanEvent> = emptyList(),
        var holdScanOpen: Boolean = false,
    ) : Bpx1BleClient {
        private val availabilityFlow = MutableStateFlow(initialAvailability)
        var lastRequestedTargetMac: String? = null
        var lastRequestedScanMode: Bpx1ScanMode? = null

        fun setAvailability(value: Bpx1BluetoothAvailability) {
            availabilityFlow.value = value
        }

        override fun availability(): Bpx1BluetoothAvailability = availabilityFlow.value

        override fun observeAvailability(): Flow<Bpx1BluetoothAvailability> = availabilityFlow

        override fun scan(bindKey: ByteArray?): Flow<Bpx1ScanEvent> =
            scan(bindKey, targetMacAddress = null, scanMode = Bpx1ScanMode.LOW_LATENCY)

        override fun scan(
            bindKey: ByteArray?,
            targetMacAddress: String?,
            scanMode: Bpx1ScanMode,
        ): Flow<Bpx1ScanEvent> = flow {
            lastRequestedTargetMac = targetMacAddress
            lastRequestedScanMode = scanMode
            for (event in events) {
                emit(event)
            }
            if (holdScanOpen) {
                kotlinx.coroutines.awaitCancellation()
            }
        }

        override suspend fun checkConnection(macAddress: String): Bpx1ConnectionResult =
            Bpx1ConnectionResult.Failed(Bpx1ConnectionFailure.CONNECT_FAILED)
    }

    private companion object {
        const val MAC = "AA:BB:CC:DD:EE:FF"
    }
}
