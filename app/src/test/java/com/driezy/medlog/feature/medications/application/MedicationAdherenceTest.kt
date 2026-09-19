package com.driezy.medlog.feature.medications.application

import com.driezy.medlog.data.model.*
import com.driezy.medlog.domain.ReminderPlanner
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class MedicationAdherenceTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-09-19T02:00:00Z"), zone)
    private val today = LocalDate.now(clock)
    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val plans = FuturePlanCalculator(clock)
    private val calculator = MedicationAdherenceCalculator(plans)
    private fun medication() = Medication(
        id = 1,
        name = "药",
        dose = 1.0,
        doseUnit = "片",
        startDate = at(today.minusDays(29), 0),
        reminderTimes = "08:00,20:00",
    )

    @Test fun `statistics include unrecorded scheduled doses without a sixty-log cap`() {
        val med = medication().copy(reminderTimes = "07:00,08:00,09:00")
        val summary = calculator.calculate(
            listOf(med),
            listOf(MedicationLog(medicationId = 1, scheduledTimeMs = at(today, 8), status = LogStatus.TAKEN)),
            emptyList(),
            clock.instant(),
            zone,
        )
        assertEquals(90, summary.total30d)
        assertEquals(1, summary.taken30d)
        assertEquals(1f / 90, summary.rate30d, 0.0001f)
    }

    @Test fun `exact slots win over nearby legacy matches and partial has no arbitrary weight`() {
        val med = medication().copy(startDate = at(today, 0), reminderTimes = "08:00,09:00,20:00")
        val logs = listOf(
            MedicationLog(id = 1, medicationId = 1, scheduledTimeMs = at(today, 9), status = LogStatus.TAKEN),
            MedicationLog(
                id = 2,
                medicationId = 1,
                scheduledTimeMs = at(today, 8),
                status = LogStatus.PARTIAL,
                actualDoseQuantity = 0.25,
            ),
        )
        val day = calculator.calculate(listOf(med), logs, emptyList(), clock.instant(), zone).days.getValue(today)
        assertEquals(listOf(LogStatus.PARTIAL, LogStatus.TAKEN, LogStatus.PENDING), day.logs.map { it.first.status })
        assertEquals(2, day.resolved)
        assertEquals(1, day.partial)
        assertEquals(0.5f, day.rate, 0.0f)
    }

    @Test fun `editing and stopping a schedule preserves prior days and prevents future obligations`() {
        val old = medication()
        val changedAt = at(today.minusDays(1), 12)
        val newer = old.copy(reminderTimes = "09:00", planEffectiveFromMs = changedAt)
        val stoppedAt = at(today, 9)
        val stopped = newer.copy(isArchived = true, planEffectiveFromMs = stoppedAt)
        val revisions = listOf(old.planRevision(changedAt), newer.planRevision(stoppedAt))
        val summary = calculator.calculate(listOf(stopped), emptyList(), revisions, clock.instant(), zone)
        assertEquals(2, summary.days.getValue(today.minusDays(2)).total)
        assertEquals(
            listOf(at(today.minusDays(1), 8)),
            summary.days.getValue(today.minusDays(1)).logs.map {
                it.first.scheduledTimeMs
            },
        )
        assertEquals(0, summary.days.getValue(today).total)
        assertTrue(plans.calculate(listOf(stopped), 7, clock.instant(), zone, revisions = revisions).isEmpty())
    }

    @Test fun `as needed doses remain visible but do not affect planned adherence`() {
        val med = medication().copy(isPRN = true)
        val log = MedicationLog(medicationId = 1, scheduledTimeMs = at(today, 8), status = LogStatus.TAKEN)
        val summary = calculator.calculate(listOf(med), listOf(log), emptyList(), clock.instant(), zone)
        assertEquals(0, summary.total30d)
        assertEquals(1, summary.days.getValue(today).logs.size)
    }

    @Test fun `interval projection and reminder share actual dose anchor`() {
        val med = medication().copy(startDate = at(today, 0), intervalHours = 8)
        val log =
            MedicationLog(
                medicationId = 1,
                scheduledTimeMs = at(today, 8),
                actualTakenTimeMs = at(today, 9),
                status = LogStatus.TAKEN,
            )
        val future = plans.calculate(listOf(med), 1, clock.instant(), zone, logs = listOf(log)).filter {
            it.scheduledAt >
                clock.instant()
        }
        val nextReminder = ReminderPlanner(
            clock,
        ).nextOccurrences(
            med.toDomainSchedule(),
            null,
            zone,
            lastTakenAt = Instant.ofEpochMilli(log.actualTakenTimeMs!!),
            startAt = Instant.ofEpochMilli(med.startDate),
        ).single()
        assertEquals(Instant.ofEpochMilli(at(today, 17)), future.first().scheduledAt)
        assertEquals(nextReminder.scheduledAt, future.first().scheduledAt)
    }

    @Test fun `late evening record is grouped in the reminder zone instead of UTC`() {
        val med = medication().copy(startDate = at(today, 0), reminderTimes = "23:00")
        val log = MedicationLog(medicationId = 1, scheduledTimeMs = at(today, 23), status = LogStatus.TAKEN)
        val summary = calculator.calculate(listOf(med), listOf(log), emptyList(), clock.instant(), zone)
        assertEquals(LogStatus.TAKEN, summary.days.getValue(today).logs.single().first.status)
        assertEquals(1, summary.total30d)
    }
}
