package com.driezy.medlog.domain

import com.driezy.medlog.domain.model.MedicationSchedule
import com.driezy.medlog.domain.model.ScheduleRecurrence
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ScheduleOccurrencesTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val start = LocalDate.of(2026, 9, 19).atStartOfDay(zone).toInstant()
    private val schedule = MedicationSchedule.ExactTimes(listOf(LocalTime.of(8, 0)), ScheduleRecurrence.EveryDays(3))

    @Test fun `rebuilding reminders preserves recurrence phase and matches calendar`() {
        val first = ReminderPlanner(
            Clock.fixed(start.plusSeconds(7 * 3600), zone),
        ).nextOccurrences(schedule, null, zone, startAt = start).single()
        assertEquals(start.plusSeconds(8 * 3600), first.scheduledAt)
        val tomorrow = start.plusSeconds(86400)
        val next = ReminderPlanner(
            Clock.fixed(tomorrow, zone),
        ).nextOccurrences(schedule, null, zone, startAt = start).single()
        val plans = ScheduleOccurrences.between(schedule, start, null, tomorrow, start.plusSeconds(7 * 86400), zone)
        assertEquals(plans.first(), next)
        assertEquals(start.plusSeconds(3 * 86400 + 8 * 3600), next.scheduledAt)
    }

    @Test fun `future start end date and handled occurrence apply identically`() {
        val end = start.plusSeconds(3 * 86400)
        val plans = ScheduleOccurrences.between(
            schedule,
            start,
            end,
            start.minusSeconds(86400),
            start.plusSeconds(
                10 * 86400,
            ),
            zone,
        )
        assertEquals(2, plans.size)
        val next = ReminderPlanner(
            Clock.fixed(start.minusSeconds(86400), zone),
        ).nextOccurrences(schedule, end, zone, startAt = start, handled = setOf(plans.first().scheduledAt))
        assertEquals(listOf(plans.last()), next)
        assertTrue(
            ReminderPlanner(
                Clock.fixed(plans.last().scheduledAt, zone),
            ).nextOccurrences(schedule, end, zone, startAt = start).isEmpty(),
        )
    }

    @Test fun `calendar recurrence survives daylight saving change`() {
        val ny = ZoneId.of("America/New_York")
        val beginning = LocalDate.of(2026, 3, 7).atStartOfDay(ny).toInstant()
        val daily = MedicationSchedule.ExactTimes(listOf(LocalTime.of(8, 0)), ScheduleRecurrence.Daily)
        val occurrences = ScheduleOccurrences.between(
            daily,
            beginning,
            null,
            beginning,
            LocalDate.of(2026, 3, 10).atStartOfDay(ny).toInstant(),
            ny,
        )
        assertEquals(listOf(7, 8, 9), occurrences.map { it.scheduledAt.atZone(ny).dayOfMonth })
        assertTrue(occurrences.all { it.scheduledAt.atZone(ny).hour == 8 })
        assertEquals(23, Duration.between(occurrences[0].scheduledAt, occurrences[1].scheduledAt).toHours())
    }
}
