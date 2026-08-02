package com.driezy.medlog.feature.medications.application

import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.model.toDomainSchedule
import com.driezy.medlog.domain.model.MedicationSchedule
import com.driezy.medlog.domain.model.ScheduleRecurrence
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A typed occurrence used by history and export projections. */
data class FuturePlanItem(
    val medication: Medication,
    val day: LocalDate,
    val scheduledAt: Instant,
    val timeSlotIndex: Int,
    val timeLabel: String,
)

/**
 * Expands persisted medication plans into occurrences using typed schedules and java.time.
 * Legacy string decoding is confined to [toDomainSchedule].
 */
@Singleton
class FuturePlanCalculator @Inject constructor(private val clock: Clock) {
    fun calculate(
        medications: List<Medication>,
        days: Int = 7,
        from: Instant = clock.instant(),
        zoneId: ZoneId = clock.zone,
        includeArchived: Boolean = false,
    ): List<FuturePlanItem> {
        if (days <= 0) return emptyList()
        val rangeStartDate = from.atZone(zoneId).toLocalDate()
        val rangeStart = rangeStartDate.atStartOfDay(zoneId).toInstant()
        val rangeEnd = rangeStartDate.plusDays(days.toLong()).atStartOfDay(zoneId).toInstant()

        return medications.flatMap { medication ->
            if (medication.isArchived && !includeArchived) return@flatMap emptyList()
            when (val schedule = medication.toDomainSchedule()) {
                MedicationSchedule.AsNeeded -> emptyList()
                is MedicationSchedule.Interval -> expandInterval(
                    medication = medication,
                    interval = schedule.every,
                    rangeStart = rangeStart,
                    rangeEnd = rangeEnd,
                    zoneId = zoneId,
                )
                is MedicationSchedule.ExactTimes -> expandClockSchedule(
                    medication = medication,
                    times = schedule.times,
                    recurrence = schedule.recurrence,
                    rangeStartDate = rangeStartDate,
                    days = days,
                    zoneId = zoneId,
                )
                is MedicationSchedule.RoutineAnchored -> expandClockSchedule(
                    medication = medication,
                    times = listOf(schedule.resolvedTime),
                    recurrence = schedule.recurrence,
                    rangeStartDate = rangeStartDate,
                    days = days,
                    zoneId = zoneId,
                )
            }
        }.sortedBy(FuturePlanItem::scheduledAt)
    }

    private fun expandClockSchedule(
        medication: Medication,
        times: List<LocalTime>,
        recurrence: ScheduleRecurrence,
        rangeStartDate: LocalDate,
        days: Int,
        zoneId: ZoneId,
    ): List<FuturePlanItem> {
        val medicationStartDate = Instant.ofEpochMilli(medication.startDate).atZone(zoneId).toLocalDate()
        val medicationEndDate = medication.endDate
            ?.let(Instant::ofEpochMilli)
            ?.atZone(zoneId)
            ?.toLocalDate()

        return buildList {
            repeat(days) { offset ->
                val date = rangeStartDate.plusDays(offset.toLong())
                if (date < medicationStartDate) return@repeat
                if (medicationEndDate != null && date > medicationEndDate) return@repeat
                if (!recurrence.matches(medicationStartDate, date)) return@repeat

                times.forEachIndexed { slotIndex, time ->
                    add(
                        FuturePlanItem(
                            medication = medication,
                            day = date,
                            scheduledAt = date.atTime(time).atZone(zoneId).toInstant(),
                            timeSlotIndex = slotIndex,
                            timeLabel = time.format(TIME_FORMATTER),
                        ),
                    )
                }
            }
        }
    }

    private fun expandInterval(
        medication: Medication,
        interval: Duration,
        rangeStart: Instant,
        rangeEnd: Instant,
        zoneId: ZoneId,
    ): List<FuturePlanItem> {
        val intervalMillis = interval.toMillis()
        if (intervalMillis <= 0L) return emptyList()
        val medicationEnd = medication.endDate?.let(Instant::ofEpochMilli)
        var cursor = Instant.ofEpochMilli(medication.startDate)
        if (cursor < rangeStart) {
            val jumps = Duration.between(cursor, rangeStart).toMillis() / intervalMillis
            cursor = cursor.plus(interval.multipliedBy(jumps))
            if (cursor < rangeStart) cursor = cursor.plus(interval)
        }

        return buildList {
            while (cursor < rangeEnd && (medicationEnd == null || cursor <= medicationEnd)) {
                val zoned = cursor.atZone(zoneId)
                add(
                    FuturePlanItem(
                        medication = medication,
                        day = zoned.toLocalDate(),
                        scheduledAt = cursor,
                        timeSlotIndex = 0,
                        timeLabel = zoned.toLocalTime().format(TIME_FORMATTER),
                    ),
                )
                cursor = cursor.plus(interval)
            }
        }
    }

    private companion object {
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

private fun ScheduleRecurrence.matches(startDate: LocalDate, date: LocalDate): Boolean = when (this) {
    ScheduleRecurrence.Daily -> true
    is ScheduleRecurrence.EveryDays -> {
        val elapsedDays = ChronoUnit.DAYS.between(startDate, date)
        elapsedDays >= 0 && elapsedDays % days == 0L
    }
    is ScheduleRecurrence.Weekdays -> date.dayOfWeek in days
}
