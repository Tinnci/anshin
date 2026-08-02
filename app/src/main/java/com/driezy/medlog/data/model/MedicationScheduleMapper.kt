package com.driezy.medlog.data.model

import com.driezy.medlog.domain.model.MedicationSchedule
import com.driezy.medlog.domain.model.RoutineAnchor
import com.driezy.medlog.domain.model.ScheduleRecurrence
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Keeps legacy Room string encodings at the persistence boundary. */
fun Medication.toDomainSchedule(): MedicationSchedule {
    if (isPRN) return MedicationSchedule.AsNeeded
    if (intervalHours > 0) return MedicationSchedule.Interval(Duration.ofHours(intervalHours.toLong()))

    val recurrence = toDomainRecurrence()
    val period = TimePeriod.fromKey(timePeriod)
    val fallback = LocalTime.of(reminderHour.coerceIn(0, 23), reminderMinute.coerceIn(0, 59))
    val persistedTimes = reminderTimes
        .split(',')
        .mapNotNull { raw -> runCatching { LocalTime.parse(raw.trim()) }.getOrNull() }
    if (period != TimePeriod.EXACT) {
        return MedicationSchedule.RoutineAnchored(
            anchor = RoutineAnchor.valueOf(period.name),
            resolvedTime = persistedTimes.firstOrNull() ?: fallback,
            recurrence = recurrence,
        )
    }

    val times = persistedTimes.ifEmpty { listOf(fallback) }
    return MedicationSchedule.ExactTimes(times = times.distinct(), recurrence = recurrence)
}

/** Converts a typed routine result back to the legacy Room columns in one persistence adapter. */
fun Medication.withResolvedRoutineTime(time: LocalTime): Medication {
    val encoded = time.format(STORED_TIME_FORMATTER)
    return copy(
        reminderTimes = encoded,
        reminderHour = time.hour,
        reminderMinute = time.minute,
    )
}

/** Resolves a stable wall-clock slot without leaking legacy comma-separated fields to commands. */
fun Medication.scheduledLocalTimeForSlot(index: Int): LocalTime {
    val fallback = LocalTime.of(reminderHour.coerceIn(0, 23), reminderMinute.coerceIn(0, 59))
    return when (val schedule = toDomainSchedule()) {
        is MedicationSchedule.ExactTimes -> schedule.times.getOrNull(index) ?: fallback
        is MedicationSchedule.RoutineAnchored -> schedule.resolvedTime.takeIf { index == 0 } ?: fallback
        is MedicationSchedule.Interval, MedicationSchedule.AsNeeded -> fallback
    }
}

private fun Medication.toDomainRecurrence(): ScheduleRecurrence = when (frequencyType) {
    "interval" -> ScheduleRecurrence.EveryDays(frequencyInterval.coerceAtLeast(1))
    "specific_days" -> {
        val days = frequencyDays
            .split(',')
            .mapNotNull(String::trim)
            .mapNotNull(String::toIntOrNull)
            .filter { it in 1..7 }
            .map(DayOfWeek::of)
            .toSet()
        if (days.isEmpty()) ScheduleRecurrence.Daily else ScheduleRecurrence.Weekdays(days)
    }
    else -> ScheduleRecurrence.Daily
}

private val STORED_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
