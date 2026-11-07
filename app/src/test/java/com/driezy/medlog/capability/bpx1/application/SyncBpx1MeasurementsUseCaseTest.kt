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
import com.driezy.medlog.capability.bpx1.InMemoryBpx1DeviceStore
import com.driezy.medlog.data.repository.FakeHealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SyncBpx1MeasurementsUseCaseTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000L), ZoneOffset.UTC)

    @Test
    fun `bluetooth off is reported as disabled`() = runTest {
        val events = sync(FakeBleClient(availability = Bpx1BluetoothAvailability.DISABLED))

        assertEquals(listOf(Bpx1SyncState.Failed(Bpx1SyncFailure.BLUETOOTH_DISABLED)), events)
    }

    @Test
    fun `missing bluetooth hardware is reported as unsupported`() = runTest {
        val events = sync(FakeBleClient(availability = Bpx1BluetoothAvailability.UNSUPPORTED))

        assertEquals(listOf(Bpx1SyncState.Failed(Bpx1SyncFailure.BLUETOOTH_UNSUPPORTED)), events)
    }

    @Test
    fun `a scan rejected for permission is reported as permission denied`() = runTest {
        val events = sync(FakeBleClient(Bpx1ScanEvent.Failure(Bpx1ScanEvent.Failure.PERMISSION_DENIED)))

        assertEquals(Bpx1SyncState.Failed(Bpx1SyncFailure.PERMISSION_DENIED), events.last())
    }

    @Test
    fun `a platform scan error is reported as scan failed`() = runTest {
        val events = sync(FakeBleClient(Bpx1ScanEvent.Failure(errorCode = 2)))

        assertEquals(Bpx1SyncState.Failed(Bpx1SyncFailure.SCAN_FAILED), events.last())
    }

    @Test
    fun `finishing without hearing the monitor reports it as not detected`() = runTest {
        val events = sync(FakeBleClient(deviceEvent(mac = "11:22:33:44:55:66", measurement = null)))

        assertEquals(Bpx1SyncState.Finished(deviceDetected = false), events.last())
        assertEquals(false, events.contains(Bpx1SyncState.DeviceDetected))
    }

    @Test
    fun `hearing the monitor without a new reading reports detected with nothing imported`() = runTest {
        val events = sync(FakeBleClient(deviceEvent(measurement = null), deviceEvent(measurement = null)))

        assertEquals(1, events.count { it == Bpx1SyncState.DeviceDetected })
        assertEquals(0, events.count { it is Bpx1SyncState.MeasurementImported })
        assertEquals(Bpx1SyncState.Finished(deviceDetected = true), events.last())
    }

    @Test
    fun `a new reading is imported once and announces the scan window`() = runTest {
        val events = sync(FakeBleClient(deviceEvent(measurement), deviceEvent(measurement)))

        assertEquals(Bpx1SyncState.Scanning(SCAN_MILLIS), events.first())
        val imported = events.filterIsInstance<Bpx1SyncState.MeasurementImported>()
        assertEquals(1, imported.size)
        assertEquals(1, imported.single().importedCount)
        assertEquals(Bpx1SyncState.Finished(deviceDetected = true), events.last())
    }

    private suspend fun sync(client: Bpx1BleClient): List<Bpx1SyncState> {
        val store = InMemoryBpx1DeviceStore(
            initial = Bpx1DeviceConfiguration(macAddress = MAC),
            bindKey = ByteArray(16) { it.toByte() },
        )
        val useCase = SyncBpx1MeasurementsUseCase(
            deviceStore = store,
            bleClient = client,
            importer = Bpx1MeasurementImporter(FakeHealthRepository(), clock),
        )
        val events = mutableListOf<Bpx1SyncState>()
        useCase.sync(scanDurationMillis = SCAN_MILLIS) { events += it }
        return events
    }

    private fun deviceEvent(measurement: Bpx1Measurement?, mac: String = MAC) = Bpx1ScanEvent.Device(
        device = Bpx1DiscoveredDevice(
            macAddress = mac,
            name = "BPX1",
            rssi = -52,
            registered = true,
            payloadStatus = Bpx1PayloadStatus.DECRYPTED,
            lastSeenMillis = 1_720_000_000_000L,
        ),
        measurement = measurement,
    )

    private val measurement = Bpx1Measurement(
        timestampMillis = 1_720_000_000_000L,
        systolic = 122,
        diastolic = 78,
        heartRate = 64,
        flags = 0,
        packetCounter = 3,
        objectId = 0x3001,
    )

    private class FakeBleClient(
        vararg events: Bpx1ScanEvent,
        private val availability: Bpx1BluetoothAvailability = Bpx1BluetoothAvailability.READY,
    ) : Bpx1BleClient {
        private val events = events

        override fun availability() = availability

        override fun scan(bindKey: ByteArray?): Flow<Bpx1ScanEvent> = flowOf(*events)

        override suspend fun checkConnection(macAddress: String): Bpx1ConnectionResult =
            Bpx1ConnectionResult.Failed(Bpx1ConnectionFailure.CONNECT_FAILED)
    }

    private companion object {
        const val MAC = "AA:BB:CC:DD:EE:FF"
        const val SCAN_MILLIS = 5_000L
    }
}
