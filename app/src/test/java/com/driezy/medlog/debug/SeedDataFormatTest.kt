package com.driezy.medlog.debug

import com.driezy.medlog.data.model.HealthType
import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.data.model.TimePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class SeedDataFormatTest {

    private val clock = Clock.fixed(
        Instant.parse("2026-05-22T08:30:00Z"),
        ZoneId.of("Asia/Shanghai"),
    )
    private val calendar = SeedDemoCalendar(clock)

    @Test
    fun `seed dataset uses deterministic valid domain formats`() {
        val dataset = SeedDemoDataUseCase.buildDataset(
            profile = SeedDemoProfile.STANDARD,
            calendar = calendar,
        )
        val medicationKeys = dataset.medications.map { it.key }.toSet()
        val healthTypes = HealthType.entries.map { it.name }.toSet()
        val validTimePeriods = TimePeriod.entries.map { it.key }.toSet()
        val validStatuses = LogStatus.entries.toSet()
        val todayStart = calendar.startOfTodayMs()
        val tomorrowStart = calendar.tomorrowStartMs()

        assertTrue(dataset.medications.size >= 5)
        assertTrue(dataset.logs.size >= 8)
        assertEquals(HealthType.entries.size, dataset.healthRecords.map { it.record.type }.toSet().size)

        dataset.medications.forEach { seeded ->
            val med = seeded.medication
            assertTrue(seeded.key.isNotBlank())
            assertTrue(med.name.isNotBlank())
            assertTrue(med.dose > 0.0)
            assertTrue(med.doseQuantity > 0.0)
            assertTrue(med.doseUnit.isNotBlank())
            assertTrue(med.reminderHour in 0..23)
            assertTrue(med.reminderMinute in 0..59)
            assertTrue(med.timePeriod in validTimePeriods)
            assertTrue(med.startDate <= todayStart)
            med.stock?.let { stock ->
                assertTrue(stock >= 0.0)
                assertNotNull(med.refillThreshold)
                assertTrue(med.refillThreshold!! >= 0.0)
            }
        }

        dataset.logs.forEach { seeded ->
            assertTrue(seeded.medicationKey in medicationKeys)
            assertTrue(seeded.log.scheduledTimeMs in todayStart until tomorrowStart)
            assertTrue(seeded.log.status in validStatuses)
            seeded.log.actualTakenTimeMs?.let { actual ->
                assertTrue(actual >= seeded.log.scheduledTimeMs)
            }
            seeded.log.actualDoseQuantity?.let { actualDose ->
                assertTrue(actualDose > 0.0)
            }
        }

        dataset.healthRecords.forEach { seeded ->
            assertTrue(seeded.record.type in healthTypes)
            assertTrue(seeded.record.value > 0.0)
            assertTrue(seeded.record.timestamp <= clock.millis())
            if (seeded.record.type == HealthType.BLOOD_PRESSURE.name) {
                assertNotNull(seeded.record.secondaryValue)
                assertTrue(seeded.record.secondaryValue!! > 0.0)
            }
        }
    }
}
