package com.driezy.medlog.domain.model

import com.driezy.medlog.data.model.BloodPressureClassification
import com.driezy.medlog.data.model.BmiClassification
import com.driezy.medlog.data.model.HealthType
import com.driezy.medlog.data.model.RoutineSchedule
import com.driezy.medlog.data.model.RoutineTime
import com.driezy.medlog.data.model.resolve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Duration
import java.time.LocalTime

class MedicationScheduleTest {
    @Test
    fun `health classifications are typed and independent from Android resources`() {
        assertEquals(
            BloodPressureClassification.NORMAL,
            HealthType.classifyBloodPressure(118.0, 78.0),
        )
        assertEquals(BmiClassification.OVERWEIGHT, HealthType.classifyBmi(26.0))
    }

    @Test
    fun `routine anchors resolve through typed routine times including day rollover`() {
        val schedule = RoutineSchedule(
            breakfast = RoutineTime(0, 10),
            bed = RoutineTime(0, 30),
        )

        assertEquals(LocalTime.of(23, 55), schedule.resolve(RoutineAnchor.BEFORE_BREAKFAST))
        assertEquals(LocalTime.of(23, 30), schedule.resolve(RoutineAnchor.EVENING))
    }

    @Test
    fun `identifiers and schedules reject invalid values`() {
        assertThrows(IllegalArgumentException::class.java) { MedicationId(0) }
        assertThrows(IllegalArgumentException::class.java) { MedicationId(-1) }
        assertThrows(IllegalArgumentException::class.java) {
            MedicationSchedule.ExactTimes(emptyList(), ScheduleRecurrence.Daily)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MedicationSchedule.Interval(Duration.ZERO)
        }
    }

    @Test
    fun `exact schedules retain typed time and recurrence`() {
        val schedule = MedicationSchedule.ExactTimes(
            times = listOf(LocalTime.of(8, 30)),
            recurrence = ScheduleRecurrence.EveryDays(2),
        )

        assertEquals(LocalTime.of(8, 30), schedule.times.single())
        assertEquals(ScheduleRecurrence.EveryDays(2), schedule.recurrence)
    }
}
