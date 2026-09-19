package com.driezy.medlog.domain

import com.driezy.medlog.domain.model.MedicationSchedule
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

data class ReminderOccurrence(val slotIndex: Int, val scheduledAt: Instant)

/** Selects future reminders from the same calendar rules used by today's plan and history. */
class ReminderPlanner(private val clock: Clock) {
    fun nextOccurrences(
        schedule: MedicationSchedule,
        endAt: Instant?,
        zoneId: ZoneId,
        lastTakenAt: Instant? = null,
        startAt: Instant = Instant.EPOCH,
        handled: Set<Instant> = emptySet(),
    ): List<ReminderOccurrence> {
        val slots = if (schedule is MedicationSchedule.ExactTimes) schedule.times.indices else 0..0
        return slots.mapNotNull { slot ->
            var after = clock.instant()
            var next = nextOccurrenceForSlot(schedule, slot, after, endAt, zoneId, startAt, lastTakenAt)
            while (next != null && next.scheduledAt in handled) {
                after = next.scheduledAt
                next = nextOccurrenceForSlot(schedule, slot, after, endAt, zoneId, startAt, lastTakenAt)
            }
            next
        }
    }

    fun nextOccurrenceForSlot(
        schedule: MedicationSchedule,
        slotIndex: Int,
        after: Instant,
        endAt: Instant?,
        zoneId: ZoneId,
        startAt: Instant = Instant.EPOCH,
        lastTakenAt: Instant? = null,
    ): ReminderOccurrence? = ScheduleOccurrences.next(
        schedule = schedule,
        slot = slotIndex,
        startAt = startAt,
        endAt = endAt,
        after = after,
        zone = zoneId,
        intervalAnchor = lastTakenAt?.let { taken ->
            if (schedule is MedicationSchedule.Interval) taken.plus(schedule.every) else taken
        } ?: startAt,
    )
}
