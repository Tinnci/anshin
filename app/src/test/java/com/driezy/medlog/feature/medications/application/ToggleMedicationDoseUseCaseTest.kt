package com.driezy.medlog.feature.medications.application

import com.driezy.medlog.capability.reminders.NotificationHelper
import com.driezy.medlog.capability.reminders.application.ReconcileRemindersUseCase
import com.driezy.medlog.data.local.TransactionRunner
import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.model.MedicationLog
import com.driezy.medlog.data.repository.FakeLogRepository
import com.driezy.medlog.data.repository.FakeMedicationRepository
import com.driezy.medlog.domain.ReminderReconcileReason
import com.driezy.medlog.domain.ReminderReconciler
import com.driezy.medlog.domain.ReminderReconciliationQueue
import com.driezy.medlog.domain.model.MedicationId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * [ToggleMedicationDoseUseCase] 单元测试。
 *
 * - [FakeLogRepository] 验证日志写入 / 删除行为
 * - [FakeMedicationRepository] 验证库存更新
 * - [ReminderReconciler] 验证持久提醒/Widget 投影对账
 * - [NotificationHelper] 用 Mockito 模拟（避免 Android 运行时依赖）
 */
class ToggleMedicationDoseUseCaseTest {

    private lateinit var logRepo: FakeLogRepository
    private lateinit var medicationRepo: FakeMedicationRepository
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var reminderReconciler: ReminderReconciler
    private lateinit var reminderQueue: ReminderReconciliationQueue
    private lateinit var useCase: ToggleMedicationDoseUseCase
    private val clock = Clock.fixed(Instant.parse("2026-08-02T04:00:00Z"), ZoneId.of("Asia/Shanghai"))

    /** 测试用 TransactionRunner：直接执行 block，不启动真实事务 */
    private val fakeTransactionRunner = object : TransactionRunner {
        override suspend fun <R> withTransaction(block: suspend () -> R): R = block()
    }

    // ── 测试辅助 ──────────────────────────────────────────────────────────────

    private fun med(id: Long = 1L, stock: Double? = null) = Medication(
        id = id,
        name = "测试药品",
        dose = 1.0,
        doseUnit = "片",
        doseQuantity = 2.0,
        reminderHour = 8,
        reminderMinute = 0,
        stock = stock,
    )

    private fun log(medId: Long = 1L, status: LogStatus = LogStatus.TAKEN) = MedicationLog(
        id = 0,
        medicationId = medId,
        scheduledTimeMs = System.currentTimeMillis(),
        status = status,
    )

    @Before
    fun setUp() {
        logRepo = FakeLogRepository()
        medicationRepo = FakeMedicationRepository()
        notificationHelper = mock()
        reminderReconciler = mock()
        reminderQueue = mock()
        useCase = ToggleMedicationDoseUseCase(
            transactionRunner = fakeTransactionRunner,
            logRepo = logRepo,
            medicationRepo = medicationRepo,
            notificationHelper = notificationHelper,
            reconcileReminders = ReconcileRemindersUseCase(reminderReconciler, reminderQueue),
            clock = clock,
        )
    }

    // ── markTaken ─────────────────────────────────────────────────────────────

    @Test
    fun `markTaken writes TAKEN log to repository`() = runTest {
        val medication = med()
        medicationRepo.addMedication(medication)

        useCase.markTaken(medication, null)

        val logs = logRepo.getLogsForRangeOnce(0L, Long.MAX_VALUE)
        assertEquals(1, logs.size)
        assertEquals(LogStatus.TAKEN, logs.first().status)
        assertEquals(medication.id, logs.first().medicationId)
    }

    @Test
    fun `markTaken deducts doseQuantity from stock`() = runTest {
        val medication = med(stock = 10.0)
        medicationRepo.addMedication(medication)

        useCase.markTaken(medication, null)

        val updated = medicationRepo.getMedicationById(medication.id)
        assertNotNull(updated)
        assertEquals(8.0, updated!!.stock!!, 0.001)
    }

    @Test
    fun `markTaken stock never goes below zero`() = runTest {
        val medication = med(stock = 1.0)
        medicationRepo.addMedication(medication)

        // doseQuantity = 2.0, stock = 1.0 → should clamp to 0
        useCase.markTaken(medication, null)

        val updated = medicationRepo.getMedicationById(medication.id)
        assertEquals(0.0, updated!!.stock!!, 0.001)
    }

    @Test
    fun `low stock notification failure does not turn a committed dose into an error`() = runTest {
        val medication = med(stock = 3.0).copy(refillThreshold = 2.0)
        medicationRepo.addMedication(medication)
        whenever(
            notificationHelper.showLowStockNotification(
                medicationId = medication.id,
                medicationName = medication.name,
                stock = 1.0,
                unit = medication.doseUnit,
            ),
        ).thenThrow(IllegalStateException("notification permission unavailable"))

        val outcome = runCatching { useCase.markTaken(medication, null) }

        assertTrue(outcome.isSuccess)
        assertEquals(1.0, medicationRepo.getMedicationById(medication.id)!!.stock!!, 0.001)
        assertEquals(1, logRepo.currentLogs().size)
    }

    @Test
    fun `markTaken does not change stock when stock is null`() = runTest {
        val medication = med(stock = null)
        medicationRepo.addMedication(medication)

        useCase.markTaken(medication, null)

        val updated = medicationRepo.getMedicationById(medication.id)
        assertNull(updated!!.stock)
    }

    @Test
    fun `markTaken reconciles durable projections`() = runTest {
        val medication = med()
        medicationRepo.addMedication(medication)

        useCase.markTaken(medication, null)

        verify(reminderReconciler).reconcileMedication(MedicationId(1L), ReminderReconcileReason.DOSE_RECORDED)
    }

    @Test
    fun `markTaken replaces existing log for same day`() = runTest {
        val medication = med()
        medicationRepo.addMedication(medication)

        // 先标记一次（模拟今日已有日志）
        useCase.markTaken(medication, null)
        // 再标记一次（幂等：不应有两条同日日志）
        useCase.markTaken(medication, null)

        val logs = logRepo.getLogsForRangeOnce(0L, Long.MAX_VALUE)
        // markTaken 先删除同一计划时间的日志再写入，最终应只有 1 条
        val takenLogs = logs.filter { it.status == LogStatus.TAKEN }
        assertEquals(1, takenLogs.size)
    }

    @Test
    fun `repeating markTaken for the same occurrence deducts stock only once`() = runTest {
        val medication = med(stock = 10.0)
        medicationRepo.addMedication(medication)
        val scheduledTime = todayAt(8, 0)

        useCase.markTaken(medication, null, scheduledTimeMs = scheduledTime)
        useCase.markTaken(medication, null, scheduledTimeMs = scheduledTime)

        val updated = medicationRepo.getMedicationById(medication.id)
        assertEquals(8.0, updated!!.stock!!, 0.001)
        assertEquals(
            1,
            logRepo.currentLogs().count {
                it.medicationId == medication.id && it.scheduledTimeMs == scheduledTime
            },
        )
    }

    @Test
    fun `repeating an idempotent command retries its recoverable projections`() = runTest {
        val medication = med(stock = 10.0)
        medicationRepo.addMedication(medication)
        val scheduledTime = todayAt(8, 0)

        useCase.markTaken(medication, null, scheduledTimeMs = scheduledTime)
        useCase.markTaken(medication, null, scheduledTimeMs = scheduledTime)

        verify(reminderReconciler, times(2)).reconcileMedication(
            MedicationId(medication.id),
            ReminderReconcileReason.DOSE_RECORDED,
        )
        verify(reminderQueue, times(2)).enqueue(ReminderReconcileReason.DOSE_RECORDED)
    }

    @Test
    fun `repeating markTakenById from external entry points deducts stock only once`() = runTest {
        val medication = med(stock = 10.0)
        medicationRepo.addMedication(medication)

        useCase.markTakenById(medication.id)
        useCase.markTakenById(medication.id)

        val updated = medicationRepo.getMedicationById(medication.id)
        assertEquals(8.0, updated!!.stock!!, 0.001)
        assertEquals(1, logRepo.currentLogs().count { it.medicationId == medication.id })
    }

    @Test
    fun `widget command does not delete another occurrence from the same day`() = runTest {
        val medication = med(stock = 10.0).copy(reminderTimes = "08:00,12:00")
        medicationRepo.addMedication(medication)
        val noonMs = todayAt(12, 0)
        logRepo.setLogs(
            listOf(
                MedicationLog(
                    id = 22L,
                    medicationId = medication.id,
                    scheduledTimeMs = noonMs,
                    status = LogStatus.SKIPPED,
                ),
            ),
        )

        useCase.markTakenById(medication.id)

        val logs = logRepo.currentLogs()
        assertEquals(2, logs.size)
        assertEquals(LogStatus.TAKEN, logs.single { it.scheduledTimeMs == todayAt(8, 0) }.status)
        assertEquals(LogStatus.SKIPPED, logs.single { it.scheduledTimeMs == noonMs }.status)
    }

    @Test
    fun `markTaken only replaces the selected slot for multi-slot medication`() = runTest {
        val medication = med().copy(reminderTimes = "08:00,12:00")
        medicationRepo.addMedication(medication)
        val morningMs = todayAt(8, 0)
        val noonMs = todayAt(12, 0)
        logRepo.setLogs(
            listOf(
                MedicationLog(
                    id = 11L,
                    medicationId = medication.id,
                    scheduledTimeMs = morningMs,
                    status = LogStatus.SKIPPED,
                ),
                MedicationLog(
                    id = 12L,
                    medicationId = medication.id,
                    scheduledTimeMs = noonMs,
                    status = LogStatus.TAKEN,
                ),
            ),
        )

        useCase.markTaken(medication, existingLog = null, timeSlotIndex = 0)

        val logs = logRepo.currentLogs()
        assertEquals(2, logs.size)
        assertEquals(LogStatus.TAKEN, logs.single { it.scheduledTimeMs == morningMs }.status)
        assertEquals(LogStatus.TAKEN, logs.single { it.scheduledTimeMs == noonMs }.status)
    }

    @Test
    fun `markTaken delegates all recoverable notification and alarm effects to reconciliation`() = runTest {
        val medication = med(id = 9L).copy(reminderTimes = "08:00,12:00")
        medicationRepo.addMedication(medication)

        useCase.markTaken(medication, existingLog = null, timeSlotIndex = 1)

        verify(notificationHelper, never()).cancelReminderNotification(9L, 1)
        verify(notificationHelper, never()).cancelEarlyReminderNotification(9L, 1)
        verify(notificationHelper, never()).cancelFollowUpNotification(9L, 1)
        verify(notificationHelper, never()).cancelAllReminderNotifications(9L)
        verify(reminderReconciler).reconcileMedication(MedicationId(9L), ReminderReconcileReason.DOSE_RECORDED)
    }

    @Test
    fun `markTaken replaces a legacy nearest-slot log with the exact planned timestamp`() = runTest {
        val medication = med().copy(reminderTimes = "08:00,12:00")
        medicationRepo.addMedication(medication)
        val morningMs = todayAt(8, 0)
        val legacyLog = MedicationLog(
            id = 21L,
            medicationId = medication.id,
            scheduledTimeMs = morningMs + 10 * 60_000L,
            status = LogStatus.SKIPPED,
        )
        logRepo.setLogs(listOf(legacyLog))

        useCase.markTaken(medication, existingLog = legacyLog, timeSlotIndex = 0)

        val logs = logRepo.currentLogs()
        assertEquals(1, logs.size)
        assertEquals(morningMs, logs.single().scheduledTimeMs)
        assertEquals(LogStatus.TAKEN, logs.single().status)
    }

    // ── markSkipped ───────────────────────────────────────────────────────────

    @Test
    fun `markSkipped writes SKIPPED log to repository`() = runTest {
        val medication = med()
        medicationRepo.addMedication(medication)

        useCase.markSkipped(medication)

        val logs = logRepo.getLogsForRangeOnce(0L, Long.MAX_VALUE)
        assertEquals(1, logs.size)
        assertEquals(LogStatus.SKIPPED, logs.first().status)
    }

    @Test
    fun `markSkipped reconciles durable projections`() = runTest {
        val medication = med()
        medicationRepo.addMedication(medication)

        useCase.markSkipped(medication)

        verify(reminderReconciler).reconcileMedication(MedicationId(1L), ReminderReconcileReason.DOSE_RECORDED)
    }

    @Test
    fun `markSkipped does not change stock`() = runTest {
        val medication = med(stock = 10.0)
        medicationRepo.addMedication(medication)

        useCase.markSkipped(medication)

        val updated = medicationRepo.getMedicationById(medication.id)
        assertEquals(10.0, updated!!.stock!!, 0.001)
    }

    @Test
    fun `changing a taken occurrence to skipped restores its consumed stock`() = runTest {
        val medication = med(stock = 10.0)
        medicationRepo.addMedication(medication)
        val scheduledTime = todayAt(8, 0)
        useCase.markTaken(medication, null, scheduledTimeMs = scheduledTime)
        val takenLog = logRepo.getLogForScheduledTime(medication.id, scheduledTime)

        useCase.markSkipped(
            medicationRepo.getMedicationById(medication.id)!!,
            existingLog = takenLog,
            scheduledTimeMs = scheduledTime,
        )

        assertEquals(10.0, medicationRepo.getMedicationById(medication.id)!!.stock!!, 0.001)
        assertEquals(LogStatus.SKIPPED, logRepo.getLogForScheduledTime(medication.id, scheduledTime)!!.status)
    }

    // ── undoTaken ─────────────────────────────────────────────────────────────

    @Test
    fun `undoTaken deletes the log`() = runTest {
        val medication = med(stock = 8.0)
        medicationRepo.addMedication(medication)
        val takenLog = log(status = LogStatus.TAKEN)
        logRepo.insertLog(takenLog)

        val insertedLog = logRepo.getLogsForRangeOnce(0L, Long.MAX_VALUE).first()
        useCase.undoTaken(medication, insertedLog)

        val remaining = logRepo.getLogsForRangeOnce(0L, Long.MAX_VALUE)
        assertTrue("撤销后日志应被删除", remaining.isEmpty())
    }

    @Test
    fun `undoTaken restores stock`() = runTest {
        val medication = med(stock = 8.0) // was 10.0, took 2.0
        medicationRepo.addMedication(medication)
        val takenLog = log(status = LogStatus.TAKEN)
        logRepo.insertLog(takenLog)

        val insertedLog = logRepo.getLogsForRangeOnce(0L, Long.MAX_VALUE).first()
        useCase.undoTaken(medication, insertedLog)

        val updated = medicationRepo.getMedicationById(medication.id)
        assertEquals(10.0, updated!!.stock!!, 0.001) // 8.0 + 2.0 = 10.0
    }

    @Test
    fun `repeating undoTaken restores stock only once`() = runTest {
        val medication = med(stock = 8.0)
        medicationRepo.addMedication(medication)
        val takenLog = log(status = LogStatus.TAKEN)
        logRepo.insertLog(takenLog)
        val insertedLog = logRepo.getLogsForRangeOnce(0L, Long.MAX_VALUE).first()

        useCase.undoTaken(medication, insertedLog)
        useCase.undoTaken(medication, insertedLog)

        val updated = medicationRepo.getMedicationById(medication.id)
        assertEquals(10.0, updated!!.stock!!, 0.001)
    }

    @Test
    fun `repeating undoTaken with freshly loaded medication restores stock only once`() = runTest {
        val medication = med(stock = 10.0)
        medicationRepo.addMedication(medication)
        val scheduledTime = todayAt(8, 0)
        useCase.markTaken(medication, null, scheduledTimeMs = scheduledTime)
        val takenLog = logRepo.getLogForScheduledTime(medication.id, scheduledTime)!!

        useCase.undoTaken(medicationRepo.getMedicationById(medication.id)!!, takenLog)
        useCase.undoTaken(medicationRepo.getMedicationById(medication.id)!!, takenLog)

        assertEquals(10.0, medicationRepo.getMedicationById(medication.id)!!.stock!!, 0.001)
    }

    @Test
    fun `changing partial dose updates stock by delta and repeating it is idempotent`() = runTest {
        val medication = med(stock = 10.0)
        medicationRepo.addMedication(medication)
        val scheduledTime = todayAt(8, 0)

        useCase.markPartial(
            medication,
            existingLog = null,
            actualQty = 1.0,
            scheduledTimeMs = scheduledTime,
        )
        var current = medicationRepo.getMedicationById(medication.id)!!
        var currentLog = logRepo.getLogForScheduledTime(medication.id, scheduledTime)
        useCase.markPartial(
            current,
            existingLog = currentLog,
            actualQty = 1.5,
            scheduledTimeMs = scheduledTime,
        )
        current = medicationRepo.getMedicationById(medication.id)!!
        currentLog = logRepo.getLogForScheduledTime(medication.id, scheduledTime)
        useCase.markPartial(
            current,
            existingLog = currentLog,
            actualQty = 1.5,
            scheduledTimeMs = scheduledTime,
        )

        assertEquals(8.5, medicationRepo.getMedicationById(medication.id)!!.stock!!, 0.001)
        assertEquals(1.5, logRepo.getLogForScheduledTime(medication.id, scheduledTime)!!.actualDoseQuantity!!, 0.001)
    }

    @Test
    fun `undoTaken does not change null stock`() = runTest {
        val medication = med(stock = null)
        medicationRepo.addMedication(medication)
        val takenLog = log(status = LogStatus.TAKEN)
        logRepo.insertLog(takenLog)

        val insertedLog = logRepo.getLogsForRangeOnce(0L, Long.MAX_VALUE).first()
        useCase.undoTaken(medication, insertedLog)

        val updated = medicationRepo.getMedicationById(medication.id)
        assertNull(updated!!.stock)
    }

    @Test
    fun `undoTaken reconciles durable projections`() = runTest {
        val medication = med()
        medicationRepo.addMedication(medication)
        val takenLog = log(status = LogStatus.TAKEN)
        logRepo.insertLog(takenLog)

        val insertedLog = logRepo.getLogsForRangeOnce(0L, Long.MAX_VALUE).first()
        useCase.undoTaken(medication, insertedLog)

        verify(reminderReconciler).reconcileMedication(MedicationId(1L), ReminderReconcileReason.DOSE_RECORDED)
    }

    @Test
    fun `undoTaken rebuilds the medication projection`() = runTest {
        val medication = med(id = 9L).copy(reminderTimes = "08:00,12:00")
        medicationRepo.addMedication(medication)
        val takenLog = log(medId = 9L, status = LogStatus.TAKEN)
        logRepo.insertLog(takenLog)

        val insertedLog = logRepo.getLogsForRangeOnce(0L, Long.MAX_VALUE).first()
        useCase.undoTaken(medication, insertedLog, timeSlotIndex = 1)

        verify(reminderReconciler).reconcileMedication(MedicationId(9L), ReminderReconcileReason.DOSE_RECORDED)
    }

    // ── undoSkipped ───────────────────────────────────────────────────────────

    @Test
    fun `undoSkipped deletes the log`() = runTest {
        val medication = med()
        medicationRepo.addMedication(medication)
        val skippedLog = log(status = LogStatus.SKIPPED)
        logRepo.insertLog(skippedLog)

        val insertedLog = logRepo.getLogsForRangeOnce(0L, Long.MAX_VALUE).first()
        useCase.undoSkipped(medication, insertedLog)

        val remaining = logRepo.getLogsForRangeOnce(0L, Long.MAX_VALUE)
        assertTrue("撤销后日志应被删除", remaining.isEmpty())
    }

    @Test
    fun `undoSkipped reconciles durable projections`() = runTest {
        val medication = med()
        medicationRepo.addMedication(medication)
        val skippedLog = log(status = LogStatus.SKIPPED)
        logRepo.insertLog(skippedLog)

        val insertedLog = logRepo.getLogsForRangeOnce(0L, Long.MAX_VALUE).first()
        useCase.undoSkipped(medication, insertedLog)

        verify(reminderReconciler).reconcileMedication(MedicationId(1L), ReminderReconcileReason.DOSE_RECORDED)
    }

    private fun todayAt(hour: Int, minute: Int): Long = LocalDate.now(clock)
        .atTime(LocalTime.of(hour, minute))
        .atZone(clock.zone)
        .toInstant()
        .toEpochMilli()
}
