package com.driezy.medlog.feature.medications.application

import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.model.MedicationLog
import com.driezy.medlog.data.model.MedicationPlanRevision
import com.driezy.medlog.data.model.applyTo
import com.driezy.medlog.data.model.toDomainSchedule
import com.driezy.medlog.domain.ReminderOccurrence
import com.driezy.medlog.domain.ScheduleOccurrences
import com.driezy.medlog.domain.model.MedicationSchedule
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class FuturePlanItem(
    val medication: Medication,
    val day: LocalDate,
    val scheduledAt: Instant,
    val timeSlotIndex: Int,
    val timeLabel: String,
)

/** Persistence adapter for the shared, pure occurrence calculator. */
@Singleton
class FuturePlanCalculator @Inject constructor(private val clock: Clock) {
    fun calculate(
        medications: List<Medication>,
        days: Int = 7,
        from: Instant = clock.instant(),
        zoneId: ZoneId = clock.zone,
        includeArchived: Boolean = false,
        revisions: List<MedicationPlanRevision> = emptyList(),
        logs: List<MedicationLog> = emptyList(),
    ): List<FuturePlanItem> {
        if (days <= 0) return emptyList()
        val firstDate = from.atZone(zoneId).toLocalDate()
        val start = firstDate.atStartOfDay(zoneId).toInstant()
        val end = firstDate.plusDays(days.toLong()).atStartOfDay(zoneId).toInstant()
        val revisionsByMedication = revisions.groupBy { it.medicationId }
        val logsByMedication = logs.groupBy { it.medicationId }
        return medications.flatMap { current ->
            val history = revisionsByMedication[current.id].orEmpty()
            val plans = history.map { it.applyTo(current) to it.effectiveUntilMs } + (current to Long.MAX_VALUE)
            plans.flatMap planLoop@{ (medication, effectiveUntil) ->
                if (medication.isArchived && (!includeArchived || history.isNotEmpty())) return@planLoop emptyList()
                val lower = maxOf(start, Instant.ofEpochMilli(medication.planEffectiveFromMs))
                val upper = minOf(end, Instant.ofEpochMilli(effectiveUntil))
                val schedule = medication.toDomainSchedule()
                val occurrences = if (schedule is MedicationSchedule.Interval) {
                    intervalOccurrences(
                        medication,
                        schedule,
                        lower,
                        upper,
                        zoneId,
                        logsByMedication[current.id].orEmpty(),
                    )
                } else {
                    ScheduleOccurrences.between(
                        schedule,
                        Instant.ofEpochMilli(medication.startDate),
                        medication.endDate?.let(Instant::ofEpochMilli),
                        lower,
                        upper,
                        zoneId,
                    )
                }
                occurrences.map { occurrence ->
                    val local = occurrence.scheduledAt.atZone(zoneId)
                    FuturePlanItem(
                        medication = medication,
                        day = local.toLocalDate(),
                        scheduledAt = occurrence.scheduledAt,
                        timeSlotIndex = occurrence.slotIndex,
                        timeLabel = local.toLocalTime().format(TIME_FORMATTER),
                    )
                }
            }
        }.sortedBy(FuturePlanItem::scheduledAt)
    }

    /** Each actual interval dose starts the next interval; replay the same anchors for all projections. */
    private fun intervalOccurrences(
        medication: Medication,
        schedule: MedicationSchedule.Interval,
        from: Instant,
        until: Instant,
        zone: ZoneId,
        logs: List<MedicationLog>,
    ): List<ReminderOccurrence> {
        if (from >= until) return emptyList()
        var anchor = Instant.ofEpochMilli(medication.startDate)
        var lower = from
        val result = mutableListOf<ReminderOccurrence>()
        val actual = logs.filter {
            it.medicationId == medication.id &&
                it.scheduledTimeMs >= medication.planEffectiveFromMs &&
                it.scheduledTimeMs < until.toEpochMilli() &&
                it.actualTakenTimeMs != null &&
                (it.status == LogStatus.TAKEN || it.status == LogStatus.PARTIAL)
        }.sortedBy { it.scheduledTimeMs }
        for (log in actual) {
            val boundary = Instant.ofEpochMilli(log.scheduledTimeMs).plusMillis(1)
            if (boundary >
                lower
            ) {
                result +=
                    ScheduleOccurrences.between(
                        schedule,
                        anchor,
                        medication.endDate?.let(Instant::ofEpochMilli),
                        lower,
                        minOf(boundary, until),
                        zone,
                    )
            }
            lower = maxOf(lower, boundary)
            anchor = Instant.ofEpochMilli(requireNotNull(log.actualTakenTimeMs)).plus(schedule.every)
        }
        result +=
            ScheduleOccurrences.between(
                schedule,
                anchor,
                medication.endDate?.let(Instant::ofEpochMilli),
                lower,
                until,
                zone,
            )
        return result.distinctBy { it.scheduledAt }
    }

    private companion object {
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
