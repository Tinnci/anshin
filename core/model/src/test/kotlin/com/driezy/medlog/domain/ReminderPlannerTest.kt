package com.driezy.medlog.domain

import com.driezy.medlog.domain.model.MedicationSchedule
import com.driezy.medlog.domain.model.RoutineAnchor
import com.driezy.medlog.domain.model.ScheduleRecurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class ReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private val now = Instant.parse("2025-06-15T01:30:00Z") // Sunday 10:30 JST
    private val planner = ReminderPlanner(Clock.fixed(now, zone))

    @Test
    fun `daily schedule returns each typed slot without persisted strings`() {
        val schedule = MedicationSchedule.ExactTimes(
            listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            ScheduleRecurrence.Daily,
        )

        val results = planner.nextOccurrences(schedule, null, zone)

        assertEquals(2, results.size)
        assertEquals(Instant.parse("2025-06-15T11:00:00Z"), results[1].scheduledAt)
        assertEquals(Instant.parse("2025-06-15T23:00:00Z"), results[0].scheduledAt)
    }

    @Test
    fun `every three days preserves legacy next-candidate recurrence semantics`() {
        val occurrence = planner.nextOccurrenceForSlot(
            MedicationSchedule.ExactTimes(
                listOf(LocalTime.of(8, 0)),
                ScheduleRecurrence.EveryDays(3),
            ),
            slotIndex = 0,
            after = now,
            endAt = null,
            zoneId = zone,
        )

        assertEquals(Instant.parse("2025-06-17T23:00:00Z"), occurrence?.scheduledAt)
    }

    @Test
    fun `weekday recurrence selects the next matching local date`() {
        val result = planner.nextOccurrences(
            MedicationSchedule.ExactTimes(
                listOf(LocalTime.of(20, 0)),
                ScheduleRecurrence.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
            ),
            endAt = null,
            zoneId = zone,
        ).single()

        assertEquals(Instant.parse("2025-06-16T11:00:00Z"), result.scheduledAt)
    }

    @Test
    fun `interval starts from last taken and respects end date`() {
        val schedule = MedicationSchedule.Interval(Duration.ofHours(6))
        val lastTaken = Instant.parse("2025-06-15T00:00:00Z")

        assertEquals(
            Instant.parse("2025-06-15T06:00:00Z"),
            planner.nextOccurrences(schedule, null, zone, lastTaken).single().scheduledAt,
        )
        assertEquals(
            emptyList<ReminderOccurrence>(),
            planner.nextOccurrences(schedule, Instant.parse("2025-06-15T05:59:59Z"), zone, lastTaken),
        )
    }

    @Test
    fun `routine anchor retains meaning while scheduling its resolved time`() {
        val result = planner.nextOccurrences(
            MedicationSchedule.RoutineAnchored(
                anchor = RoutineAnchor.AFTER_BREAKFAST,
                resolvedTime = LocalTime.of(11, 0),
                recurrence = ScheduleRecurrence.Daily,
            ),
            endAt = null,
            zoneId = zone,
        ).single()

        assertEquals(Instant.parse("2025-06-15T02:00:00Z"), result.scheduledAt)
    }

    @Test
    fun `spring DST gap is resolved by zone rules`() {
        val newYork = ZoneId.of("America/New_York")
        val beforeGap = Instant.parse("2025-03-09T06:00:00Z")
        val dstPlanner = ReminderPlanner(Clock.fixed(beforeGap, newYork))

        val result = dstPlanner.nextOccurrences(
            MedicationSchedule.ExactTimes(listOf(LocalTime.of(2, 30)), ScheduleRecurrence.Daily),
            endAt = null,
            zoneId = newYork,
        ).single()

        assertEquals(Instant.parse("2025-03-09T07:30:00Z"), result.scheduledAt)
    }

    @Test
    fun `as needed schedule never creates an occurrence`() {
        assertEquals(emptyList<ReminderOccurrence>(), planner.nextOccurrences(MedicationSchedule.AsNeeded, null, zone))
        assertNull(planner.nextOccurrenceForSlot(MedicationSchedule.AsNeeded, 0, now, null, zone))
    }
}
