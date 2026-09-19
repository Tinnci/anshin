package com.driezy.medlog.feature.medications.application

import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.repository.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMedicationAdherenceTest {
    @Test fun `minute refresh reuses data subscriptions and midnight changes query range`() = runTest {
        val zone = ZoneId.of("Asia/Shanghai")
        val clock = Clock.fixed(Instant.parse("2026-09-19T00:00:00Z"), zone)
        val time = MutableStateFlow(clock.instant().minusSeconds(60))
        val meds = FakeMedicationRepository()
        meds.addMedication(Medication(name = "Medication", dose = 1.0, doseUnit = "tablet", startDate = clock.millis()))
        var queries = 0
        val delegate = FakeLogRepository()
        val logs = object : LogRepository by delegate {
            override fun getLogsForDateRange(startMs: Long, endMs: Long) =
                delegate.getLogsForDateRange(startMs, endMs).also { queries++ }
        }
        val prefs = mock<UserPreferencesRepository> { on { settingsFlow } doReturn flowOf(SettingsPreferences()) }
        val observe =
            ObserveMedicationAdherence(
                meds,
                logs,
                prefs,
                MedicationAdherenceCalculator(FuturePlanCalculator(clock)),
                clock,
                StandardTestDispatcher(testScheduler),
            )
        var latest: MedicationAdherence? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observe(time).collect { latest = it } }
        runCurrent()
        assertEquals(1, queries)
        assertEquals(0, latest!!.total30d)
        repeat(20) {
            time.value = time.value.plusSeconds(60)
            runCurrent()
        }
        assertEquals(1, queries)
        assertEquals(1, latest!!.total30d)
        time.value = time.value.plus(Duration.ofDays(1))
        runCurrent()
        assertEquals(2, queries)
        assertEquals(LocalDate.of(2026, 9, 20), latest!!.today)
    }
}
