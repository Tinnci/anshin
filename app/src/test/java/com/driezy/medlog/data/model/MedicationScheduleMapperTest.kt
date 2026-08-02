package com.driezy.medlog.data.model

import com.driezy.medlog.domain.model.MedicationSchedule
import com.driezy.medlog.domain.model.RoutineAnchor
import com.driezy.medlog.domain.model.ScheduleRecurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

class MedicationScheduleMapperTest {
    private fun medication() = Medication(name = "Test", dose = 1.0, doseUnit = "tablet")

    @Test
    fun `maps persisted exact times and weekdays to typed schedule`() {
        val schedule = medication().copy(
            reminderTimes = "08:30,20:15",
            frequencyType = "specific_days",
            frequencyDays = "1,3,7",
        ).toDomainSchedule() as MedicationSchedule.ExactTimes

        assertEquals(listOf(LocalTime.of(8, 30), LocalTime.of(20, 15)), schedule.times)
        assertEquals(
            ScheduleRecurrence.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)),
            schedule.recurrence,
        )
    }

    @Test
    fun `maps routine interval and as-needed schedules without leaking persisted strings`() {
        val routine = medication().copy(timePeriod = TimePeriod.AFTER_BREAKFAST.key).toDomainSchedule()
        val interval = medication().copy(intervalHours = 6).toDomainSchedule()
        val asNeeded = medication().copy(isPRN = true, intervalHours = 6).toDomainSchedule()

        assertEquals(
            MedicationSchedule.RoutineAnchored(
                RoutineAnchor.AFTER_BREAKFAST,
                LocalTime.of(8, 0),
                ScheduleRecurrence.Daily,
            ),
            routine,
        )
        assertEquals(MedicationSchedule.Interval(Duration.ofHours(6)), interval)
        assertSame(MedicationSchedule.AsNeeded, asNeeded)
    }

    @Test
    fun `invalid persisted exact times fall back to compatible hour and minute columns`() {
        val schedule = medication().copy(
            reminderTimes = "invalid",
            reminderHour = 9,
            reminderMinute = 45,
        ).toDomainSchedule() as MedicationSchedule.ExactTimes

        assertEquals(listOf(LocalTime.of(9, 45)), schedule.times)
    }

    @Test
    fun `typed routine result is encoded only by the persistence adapter`() {
        val updated = medication().withResolvedRoutineTime(LocalTime.of(21, 5))

        assertEquals("21:05", updated.reminderTimes)
        assertEquals(21, updated.reminderHour)
        assertEquals(5, updated.reminderMinute)
    }

    @Test
    fun `slot lookup returns typed local time and keeps malformed fallback compatible`() {
        val medication = medication().copy(
            reminderTimes = "08:30,invalid,20:15",
            reminderHour = 9,
            reminderMinute = 45,
        )

        assertEquals(LocalTime.of(8, 30), medication.scheduledLocalTimeForSlot(0))
        assertEquals(LocalTime.of(20, 15), medication.scheduledLocalTimeForSlot(1))
        assertEquals(LocalTime.of(9, 45), medication.scheduledLocalTimeForSlot(10))
    }
}
