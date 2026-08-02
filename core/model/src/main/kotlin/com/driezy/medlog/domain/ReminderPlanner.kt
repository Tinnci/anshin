package com.driezy.medlog.domain

import com.driezy.medlog.domain.model.MedicationSchedule
import com.driezy.medlog.domain.model.ScheduleRecurrence
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class ReminderOccurrence(val slotIndex: Int, val scheduledAt: Instant)

/** Pure domain planner: persistence encodings and Android alarm details stay outside this module. */
class ReminderPlanner(private val clock: Clock) {
    fun nextOccurrences(
        schedule: MedicationSchedule,
        endAt: Instant?,
        zoneId: ZoneId,
        lastTakenAt: Instant? = null,
    ): List<ReminderOccurrence> = when (schedule) {
        MedicationSchedule.AsNeeded -> emptyList()
        is MedicationSchedule.Interval -> listOfNotNull(
            intervalOccurrence(schedule.every, lastTakenAt ?: clock.instant(), endAt),
        )
        is MedicationSchedule.ExactTimes -> schedule.times.mapIndexedNotNull { index, time ->
            clockOccurrence(index, time, schedule.recurrence, endAt, zoneId, clock.instant())
        }
        is MedicationSchedule.RoutineAnchored -> listOfNotNull(
            clockOccurrence(0, schedule.resolvedTime, schedule.recurrence, endAt, zoneId, clock.instant()),
        )
    }

    fun nextOccurrenceForSlot(
        schedule: MedicationSchedule,
        slotIndex: Int,
        after: Instant,
        endAt: Instant?,
        zoneId: ZoneId,
    ): ReminderOccurrence? = when (schedule) {
        MedicationSchedule.AsNeeded -> null
        is MedicationSchedule.Interval -> intervalOccurrence(schedule.every, after, endAt)
        is MedicationSchedule.ExactTimes -> schedule.times.getOrNull(slotIndex)?.let { time ->
            clockOccurrence(slotIndex, time, schedule.recurrence, endAt, zoneId, after)
        }
        is MedicationSchedule.RoutineAnchored -> if (slotIndex == 0) {
            clockOccurrence(0, schedule.resolvedTime, schedule.recurrence, endAt, zoneId, after)
        } else {
            null
        }
    }

    private fun intervalOccurrence(every: Duration, base: Instant, endAt: Instant?): ReminderOccurrence? {
        val occurrence = ReminderOccurrence(0, base.plus(every))
        return occurrence.takeUnless { endAt != null && it.scheduledAt > endAt }
    }

    private fun clockOccurrence(
        slotIndex: Int,
        time: LocalTime,
        recurrence: ScheduleRecurrence,
        endAt: Instant?,
        zoneId: ZoneId,
        after: Instant,
    ): ReminderOccurrence? {
        val afterAtZone = after.atZone(zoneId)
        val date = nextDate(afterAtZone, time, recurrence)
        val instant = ZonedDateTime.of(date, time, zoneId).toInstant()
        return ReminderOccurrence(slotIndex, instant).takeUnless { endAt != null && instant > endAt }
    }

    private fun nextDate(after: ZonedDateTime, time: LocalTime, recurrence: ScheduleRecurrence): LocalDate {
        val todayCandidate = ZonedDateTime.of(after.toLocalDate(), time, after.zone)
        val firstDate = if (todayCandidate.toInstant() > after.toInstant()) {
            after.toLocalDate()
        } else {
            after.toLocalDate().plusDays(1)
        }
        return when (recurrence) {
            ScheduleRecurrence.Daily -> firstDate
            is ScheduleRecurrence.EveryDays -> firstDate.plusDays((recurrence.days - 1).toLong())
            is ScheduleRecurrence.Weekdays -> firstDate.nextMatching(recurrence.days)
        }
    }
}

private fun LocalDate.nextMatching(days: Set<DayOfWeek>): LocalDate =
    generateSequence(this) { it.plusDays(1) }.first { it.dayOfWeek in days }
