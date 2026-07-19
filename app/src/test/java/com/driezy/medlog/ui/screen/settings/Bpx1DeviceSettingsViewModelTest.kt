package com.driezy.medlog.ui.screen.settings

import com.driezy.medlog.data.model.HealthRecordSource
import com.driezy.medlog.data.model.HealthType
import com.driezy.medlog.data.repository.FakeHealthRepository
import com.driezy.medlog.device.bpx1.Bpx1BleClient
import com.driezy.medlog.device.bpx1.Bpx1BluetoothAvailability
import com.driezy.medlog.device.bpx1.Bpx1ConnectionFailure
import com.driezy.medlog.device.bpx1.Bpx1ConnectionResult
import com.driezy.medlog.device.bpx1.Bpx1DeviceConfiguration
import com.driezy.medlog.device.bpx1.Bpx1DiscoveredDevice
import com.driezy.medlog.device.bpx1.Bpx1Measurement
import com.driezy.medlog.device.bpx1.Bpx1PayloadStatus
import com.driezy.medlog.device.bpx1.Bpx1ScanEvent
import com.driezy.medlog.device.bpx1.InMemoryBpx1DeviceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Bpx1DeviceSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a matching measurement imports blood pressure and heart rate once`() = runTest {
        val key = ByteArray(16) { it.toByte() }
        val store = InMemoryBpx1DeviceStore(
            initial = Bpx1DeviceConfiguration(
                macAddress = "AA:BB:CC:DD:EE:FF",
                autoImport = true,
            ),
            bindKey = key,
        )
        val measurement = Bpx1Measurement(
            timestampMillis = 1_720_000_000_000L,
            systolic = 122,
            diastolic = 78,
            heartRate = 64,
            flags = 0,
            packetCounter = 3,
            objectId = 0x3001,
        )
        val scanEvent = Bpx1ScanEvent.Device(
            device = Bpx1DiscoveredDevice(
                macAddress = "AA:BB:CC:DD:EE:FF",
                name = "BPX1",
                rssi = -52,
                registered = true,
                payloadStatus = Bpx1PayloadStatus.DECRYPTED,
                lastSeenMillis = measurement.timestampMillis,
            ),
            measurement = measurement,
        )
        val repository = FakeHealthRepository()
        val viewModel = Bpx1DeviceSettingsViewModel(store, FakeBleClient(scanEvent), repository)

        viewModel.startScan()
        advanceUntilIdle()
        viewModel.startScan()
        advanceUntilIdle()

        val records = repository.getAllRecords().first()
        assertEquals(2, records.size)
        assertEquals(
            setOf(HealthType.BLOOD_PRESSURE.name, HealthType.HEART_RATE.name),
            records.map { it.type }.toSet(),
        )
        assertTrue(records.all { it.source == HealthRecordSource.IMPORT })
        assertTrue(records.all { it.sourceProvider == "ihealth.bpm.bpx1" })
        assertEquals(1, viewModel.uiState.value.importedMeasurementCount)
    }

    @Test
    fun `empty discovery state is shown only after a scan attempt`() = runTest {
        val viewModel = Bpx1DeviceSettingsViewModel(
            InMemoryBpx1DeviceStore(),
            FakeBleClient(),
            FakeHealthRepository(),
        )

        assertEquals(false, viewModel.uiState.value.hasAttemptedScan)

        viewModel.startScan()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasAttemptedScan)
        assertEquals(false, viewModel.uiState.value.isScanning)
    }

    @Test
    fun `changing device requires a new bind key`() = runTest {
        val store = InMemoryBpx1DeviceStore(
            initial = Bpx1DeviceConfiguration(macAddress = "AA:BB:CC:DD:EE:FF"),
            bindKey = ByteArray(16),
        )
        val viewModel = Bpx1DeviceSettingsViewModel(store, FakeBleClient(), FakeHealthRepository())

        viewModel.updateMacInput("11:22:33:44:55:66")
        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.bindKeyInputInvalid)
        assertEquals("AA:BB:CC:DD:EE:FF", store.configuration.value.macAddress)
    }

    @Test
    fun `saving the same device may retain its stored bind key`() = runTest {
        val key = ByteArray(16) { it.toByte() }
        val store = InMemoryBpx1DeviceStore(
            initial = Bpx1DeviceConfiguration(macAddress = "AA:BB:CC:DD:EE:FF"),
            bindKey = key,
        )
        val viewModel = Bpx1DeviceSettingsViewModel(store, FakeBleClient(), FakeHealthRepository())

        viewModel.updateMacInput("aa-bb-cc-dd-ee-ff")
        viewModel.saveConfiguration()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.bindKeyInputInvalid)
        assertEquals(Bpx1SettingsMessage.CONFIGURATION_SAVED, viewModel.uiState.value.message)
        assertEquals("AA:BB:CC:DD:EE:FF", store.configuration.value.macAddress)
        assertTrue(store.getBindKey()?.contentEquals(key) == true)
    }

    @Test
    fun `scan failure completes the attempt and exposes an actionable message`() = runTest {
        val viewModel = Bpx1DeviceSettingsViewModel(
            InMemoryBpx1DeviceStore(),
            FakeBleClient(Bpx1ScanEvent.Failure(errorCode = 2)),
            FakeHealthRepository(),
        )

        viewModel.startScan()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasAttemptedScan)
        assertEquals(false, viewModel.uiState.value.isScanning)
        assertEquals(Bpx1SettingsMessage.SCAN_FAILED, viewModel.uiState.value.message)
    }

    private class FakeBleClient(vararg events: Bpx1ScanEvent) : Bpx1BleClient {
        private val events = events

        override fun availability() = Bpx1BluetoothAvailability.READY

        override fun scan(bindKey: ByteArray?): Flow<Bpx1ScanEvent> = flowOf(*events)

        override suspend fun checkConnection(macAddress: String): Bpx1ConnectionResult =
            Bpx1ConnectionResult.Failed(Bpx1ConnectionFailure.CONNECT_FAILED)
    }
}
