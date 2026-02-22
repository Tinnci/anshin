package com.example.medlog.domain

import com.example.medlog.data.model.LogStatus
import com.example.medlog.data.model.Medication
import com.example.medlog.data.model.MedicationLog
import com.example.medlog.data.repository.FakeLogRepository
import com.example.medlog.data.repository.FakeMedicationRepository
import com.example.medlog.notification.AlarmScheduler
import com.example.medlog.notification.NotificationHelper
import com.example.medlog.widget.FakeWidgetRefresher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

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
        // 因 markTaken 先 deleteLogsForDate 再 insertLog，最终应只有 1 条
        val takenLogs = logs.filter { it.status == LogStatus.TAKEN }
        assertEquals(1, takenLogs.size)
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
}
