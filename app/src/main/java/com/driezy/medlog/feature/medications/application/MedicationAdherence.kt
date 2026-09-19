package com.driezy.medlog.feature.medications.application

import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.model.MedicationLog
import com.driezy.medlog.data.model.MedicationPlanRevision
import com.driezy.medlog.data.repository.LogRepository
import com.driezy.medlog.data.repository.MedicationRepository
import com.driezy.medlog.data.repository.UserPreferencesRepository
import com.driezy.medlog.data.repository.reminderZone
import com.driezy.medlog.di.ComputationDispatcher
import com.driezy.medlog.domain.StreakCalculator
import com.driezy.medlog.feature.medications.application.matchDoseLogsToSlots
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

const val UNKNOWN_MEDICATION_NAME = "\u0000__unknown__"

data class AdherenceDay(
    val date: LocalDate,
    val taken: Int,
    val partial: Int,
    val total: Int,
    val logs: List<Pair<MedicationLog, String>>,
    val expectedLogs: List<MedicationLog> = logs.map { it.first },
) {
    val pending: Int get() = expectedLogs.count { it.status == LogStatus.PENDING }
    val resolved: Int get() = expectedLogs.size - pending

    /** Full doses / due scheduled doses. Partial doses are reported separately, without an invented weight. */
    val rate: Float get() = if (resolved == 0) 0f else taken.toFloat() / resolved
}

data class MedicationAdherence(
    val today: LocalDate,
    val zone: ZoneId,
    val medications: List<Medication>,
    val logs: List<MedicationLog>,
    val days: Map<LocalDate, AdherenceDay>,
) {
    private val recentDays by lazy { days.filterKeys { it >= today.minusDays(29) }.values }
    val taken30d by lazy { recentDays.sumOf { it.taken } }
    val partial30d by lazy { recentDays.sumOf { it.partial } }
    val total30d by lazy { recentDays.sumOf { it.resolved } }
    val rate30d get() = if (total30d == 0) 0f else taken30d.toFloat() / total30d
    private val recordedDates by lazy {
        logs.filter { it.status == LogStatus.TAKEN || it.status == LogStatus.PARTIAL }
            .map { Instant.ofEpochMilli(it.scheduledTimeMs).atZone(zone).toLocalDate() }.toSet()
    }
    val currentStreak get() = StreakCalculator.currentStreak(recordedDates, today)
    val longestStreak get() = StreakCalculator.longestStreak(recordedDates)
}

class MedicationAdherenceCalculator @Inject constructor(private val plans: FuturePlanCalculator) {
    fun calculate(
        medications: List<Medication>,
        logs: List<MedicationLog>,
        revisions: List<MedicationPlanRevision>,
        now: Instant,
        zone: ZoneId,
    ): MedicationAdherence {
        val today = now.atZone(zone).toLocalDate()
        val firstDate = today.minusDays(89)
        val planned = plans.calculate(
            medications.filter {
                !it.isArchived || it.endDate != null || revisions.any { r -> r.medicationId == it.id }
            },
            days = 90,
            from = firstDate.atStartOfDay(zone).toInstant(),
            zoneId = zone,
            includeArchived = true,
            revisions = revisions,
            logs = logs,
        ).groupBy { it.day }
        val byId = medications.associateBy { it.id }
        val byDay = logs.groupBy { Instant.ofEpochMilli(it.scheduledTimeMs).atZone(zone).toLocalDate() }
        val days = (planned.keys + byDay.keys + today).filter { it in firstDate..today }.associateWith { date ->
            val actual = byDay[date].orEmpty()
            val matched = mutableSetOf<MedicationLog>()
            val expected = planned[date].orEmpty().groupBy { it.medication.id }.flatMap { (id, slots) ->
                val matches =
                    matchDoseLogsToSlots(
                        slots.map { it.scheduledAt.toEpochMilli() },
                        actual.filter {
                            it.medicationId ==
                                id
                        },
                    )
                slots.mapIndexed { i, slot ->
                    matches[i]?.also { matched += it } ?: MedicationLog(
                        id = -slot.scheduledAt.toEpochMilli(),
                        medicationId = id,
                        scheduledTimeMs = slot.scheduledAt.toEpochMilli(),
                        status = if (slot.scheduledAt > now) LogStatus.PENDING else LogStatus.MISSED,
                        createdAtMs = 0L,
                    )
                }
            }
            val extra = actual.filterNot { it in matched }
            val counted = expected + extra.filter { byId[it.medicationId]?.isPRN != true }
            val all = (expected + extra).sortedBy { it.scheduledTimeMs }
            AdherenceDay(
                date,
                counted.count { it.status == LogStatus.TAKEN },
                counted.count { it.status == LogStatus.PARTIAL },
                counted.size,
                all.map { it to (byId[it.medicationId]?.name ?: UNKNOWN_MEDICATION_NAME) },
                counted,
            )
        }
        return MedicationAdherence(today, zone, medications, logs, days)
    }
}

/** One reactive, timezone-aware query shared by history and medication details. */
class ObserveMedicationAdherence @Inject constructor(
    private val medications: MedicationRepository,
    private val logs: LogRepository,
    private val preferences: UserPreferencesRepository,
    private val calculator: MedicationAdherenceCalculator,
    private val clock: Clock,
    @param:ComputationDispatcher private val dispatcher: CoroutineDispatcher,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(time: Flow<Instant>, medicationId: Long? = null): Flow<MedicationAdherence> = combine(
        time,
        preferences.settingsFlow.map {
            it.reminderZone(clock.zone)
        }.distinctUntilChanged(),
    ) { now, zone ->
        now.atZone(zone).toLocalDate() to zone
    }.distinctUntilChanged().flatMapLatest { (today, zone) ->
        combine(
            medications.getAllMedications(),
            medications.observePlanRevisions(),
            logs.getLogsForDateRange(0L, today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1),
            time,
        ) { meds, revisions, records, now ->
            calculator.calculate(
                meds.filter { medicationId == null || it.id == medicationId },
                records.filter { medicationId == null || it.medicationId == medicationId },
                revisions,
                now,
                zone,
            )
        }
    }.flowOn(dispatcher)
}
