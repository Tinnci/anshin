package com.driezy.medlog.domain

import com.driezy.medlog.data.local.TransactionRunner
import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.model.MedicationLog
import com.driezy.medlog.data.repository.FakeLogRepository
import com.driezy.medlog.data.repository.FakeMedicationRepository
import com.driezy.medlog.notification.AlarmScheduler
import com.driezy.medlog.notification.NotificationHelper
import com.driezy.medlog.widget.FakeWidgetRefresher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.util.Calendar

/**
 * [ToggleMedicationDoseUseCase] 单元测试。
 *
 * - [FakeLogRepository] 验证日志写入 / 删除行为
 * - [FakeMedicationRepository] 验证库存更新
 * - [FakeWidgetRefresher] 验证 Widget 刷新触发次数
 * - [AlarmScheduler] / [NotificationHelper] 用 Mockito 模拟（避免 Android 运行时依赖）
 */
class ToggleMedicationDoseUseCaseTest {

    private lateinit var logRepo: FakeLogRepository
    private lateinit var medicationRepo: FakeMedicationRepository
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var widgetRefresher: FakeWidgetRefresher
    private lateinit var useCase: ToggleMedicationDoseUseCase

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
        alarmScheduler = mock()
        notificationHelper = mock()
        widgetRefresher = FakeWidgetRefresher()
        useCase = ToggleMedicationDoseUseCase(
            transactionRunner = fakeTransactionRunner,
            logRepo = logRepo,
            medicationRepo = medicationRepo,
            alarmScheduler = alarmScheduler,
            notificationHelper = notificationHelper,
            widgetRefresher = widgetRefresher,
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
    fun `markTaken does not change stock when stock is null`() = runTest {
        val medication = med(stock = null)
        medicationRepo.addMedication(medication)

        useCase.markTaken(medication, null)

        val updated = medicationRepo.getMedicationById(medication.id)
        assertNull(updated!!.stock)
    }

    @Test
    fun `markTaken triggers widget refresh`() = runTest {
        val medication = med()
        medicationRepo.addMedication(medication)

        useCase.markTaken(medication, null)

        assertEquals(1, widgetRefresher.refreshCallCount)
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
    fun `markTaken only cancels reminders for the selected slot`() = runTest {
        val medication = med(id = 9L).copy(reminderTimes = "08:00,12:00")
        medicationRepo.addMedication(medication)

        useCase.markTaken(medication, existingLog = null, timeSlotIndex = 1)

        verify(alarmScheduler).cancelAlarmSlot(9L, 1)
        verify(alarmScheduler, never()).cancelAllAlarms(9L)
        verify(notificationHelper).cancelReminderNotification(9L, 1)
        verify(notificationHelper).cancelEarlyReminderNotification(9L, 1)
        verify(notificationHelper).cancelFollowUpNotification(9L, 1)
        verify(notificationHelper, never()).cancelAllReminderNotifications(9L)
        verify(alarmScheduler).scheduleNextReminderAfterDose(
            eq(medication),
            eq(1),
            any(),
            any(),
        )
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
    fun `markSkipped triggers widget refresh`() = runTest {
        val medication = med()
        medicationRepo.addMedication(medication)

        useCase.markSkipped(medication)

        assertEquals(1, widgetRefresher.refreshCallCount)
    }

    @Test
    fun `markSkipped does not change stock`() = runTest {
        val medication = med(stock = 10.0)
        medicationRepo.addMedication(medication)

        useCase.markSkipped(medication)

        val updated = medicationRepo.getMedicationById(medication.id)
        assertEquals(10.0, updated!!.stock!!, 0.001)
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
    fun `undoTaken triggers widget refresh`() = runTest {
        val medication = med()
        medicationRepo.addMedication(medication)
        val takenLog = log(status = LogStatus.TAKEN)
        logRepo.insertLog(takenLog)

        val insertedLog = logRepo.getLogsForRangeOnce(0L, Long.MAX_VALUE).first()
        useCase.undoTaken(medication, insertedLog)

        assertEquals(1, widgetRefresher.refreshCallCount)
    }

    @Test
    fun `undoTaken restores only the selected reminder slot`() = runTest {
        val medication = med(id = 9L).copy(reminderTimes = "08:00,12:00")
        medicationRepo.addMedication(medication)
        val takenLog = log(medId = 9L, status = LogStatus.TAKEN)
        logRepo.insertLog(takenLog)

        val insertedLog = logRepo.getLogsForRangeOnce(0L, Long.MAX_VALUE).first()
        useCase.undoTaken(medication, insertedLog, timeSlotIndex = 1)

        verify(alarmScheduler).restoreReminderForDose(medication, 1)
        verify(alarmScheduler, never()).scheduleAllReminders(medication)
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
    fun `undoSkipped triggers widget refresh`() = runTest {
        val medication = med()
        medicationRepo.addMedication(medication)
        val skippedLog = log(status = LogStatus.SKIPPED)
        logRepo.insertLog(skippedLog)

        val insertedLog = logRepo.getLogsForRangeOnce(0L, Long.MAX_VALUE).first()
        useCase.undoSkipped(medication, insertedLog)

        assertEquals(1, widgetRefresher.refreshCallCount)
    }

    private fun todayAt(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
