package com.driezy.medlog.domain

import com.driezy.medlog.domain.model.MedicationSchedule
import com.driezy.medlog.domain.model.ScheduleRecurrence
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Calendar dates, recurrence anchors and slot identities shared by every plan projection. */
object ScheduleOccurrences {
    fun between(
        schedule: MedicationSchedule,
        startAt: Instant,
        endAt: Instant?,
        from: Instant,
        until: Instant,
        zone: ZoneId,
    ): List<ReminderOccurrence> {
        if (from >= until || schedule == MedicationSchedule.AsNeeded) return emptyList()
        if (schedule is MedicationSchedule.Interval) {
            val end = minOf(until, endAt.endExclusive(zone) ?: until)
            var cursor = startAt
            if (cursor < from) {
                val steps = Duration.between(cursor, from).toMillis() / schedule.every.toMillis()
                cursor = cursor.plus(schedule.every.multipliedBy(steps))
                if (cursor < from) cursor = cursor.plus(schedule.every)
            }
            return buildList {
                while (cursor < end) {
                    add(ReminderOccurrence(0, cursor))
                    cursor = cursor.plus(schedule.every)
                }
            }
        }
        val (times, recurrence) = schedule.clockTimes()
        val startDate = startAt.atZone(zone).toLocalDate()
        val endDate = endAt?.atZone(zone)?.toLocalDate()
        var date = maxOf(startDate, from.atZone(zone).toLocalDate())
        val lastDate = until.atZone(zone).toLocalDate()
        return buildList {
            while (date <= lastDate && (endDate == null || date <= endDate)) {
                if (recurrence.matches(startDate, date)) {
                    times.forEachIndexed { index, time ->
                        val instant = date.atTime(time).atZone(zone).toInstant()
                        if (instant >= from && instant < until) add(ReminderOccurrence(index, instant))
                    }
                }
                date = date.plusDays(1)
            }
        }.sortedBy(ReminderOccurrence::scheduledAt)
    }

    fun next(
        schedule: MedicationSchedule,
        slot: Int,
        startAt: Instant,
        endAt: Instant?,
        after: Instant,
        zone: ZoneId,
        intervalAnchor: Instant = startAt,
    ): ReminderOccurrence? {
        if (schedule == MedicationSchedule.AsNeeded) return null
        if (schedule is MedicationSchedule.Interval) {
            if (slot != 0) return null
            val lowerBound = maxOf(after, startAt.minusNanos(1))
            var next = intervalAnchor
            if (next <= lowerBound) {
                val steps = Duration.between(next, lowerBound).toMillis() / schedule.every.toMillis() + 1
                next = next.plus(schedule.every.multipliedBy(steps))
            }
            return ReminderOccurrence(0, next).takeIf {
                endAt.endExclusive(zone)?.let { end -> next < end } != false
            }
        }
        val (times, recurrence) = schedule.clockTimes()
        val time = times.getOrNull(slot) ?: return null
        val startDate = startAt.atZone(zone).toLocalDate()
        var date = maxOf(startDate, after.atZone(zone).toLocalDate())
        if (date.atTime(time).atZone(zone).toInstant() <= after) date = date.plusDays(1)
        date = recurrence.nextDate(startDate, date)
        if (endAt != null && date > endAt.atZone(zone).toLocalDate()) return null
        return ReminderOccurrence(slot, date.atTime(time).atZone(zone).toInstant())
    }
}

private fun Instant?.endExclusive(zone: ZoneId): Instant? =
    this?.atZone(zone)?.toLocalDate()?.plusDays(1)?.atStartOfDay(zone)?.toInstant()

private fun MedicationSchedule.clockTimes(): Pair<List<LocalTime>, ScheduleRecurrence> = when (this) {
    is MedicationSchedule.ExactTimes -> times to recurrence
    is MedicationSchedule.RoutineAnchored -> listOf(resolvedTime) to recurrence
    else -> error("Not a clock schedule")
}

private fun ScheduleRecurrence.matches(start: LocalDate, date: LocalDate): Boolean = when (this) {
    ScheduleRecurrence.Daily -> true
    is ScheduleRecurrence.EveryDays -> ChronoUnit.DAYS.between(start, date) % days == 0L
    is ScheduleRecurrence.Weekdays -> date.dayOfWeek in days
}

private fun ScheduleRecurrence.nextDate(start: LocalDate, date: LocalDate): LocalDate = when (this) {
    ScheduleRecurrence.Daily -> date
    is ScheduleRecurrence.EveryDays -> {
        val remainder = ChronoUnit.DAYS.between(start, date) % days
        if (remainder == 0L) date else date.plusDays(days - remainder)
    }
    is ScheduleRecurrence.Weekdays -> generateSequence(date) { it.plusDays(1) }.first { it.dayOfWeek in days }
}
