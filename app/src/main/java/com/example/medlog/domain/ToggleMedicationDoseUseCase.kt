package com.example.medlog.domain

import android.content.Context
import com.example.medlog.data.model.LogStatus
import com.example.medlog.data.model.Medication
import com.example.medlog.data.model.MedicationLog
import com.example.medlog.data.repository.LogRepository
import com.example.medlog.data.repository.MedicationRepository
import com.example.medlog.notification.AlarmScheduler
import com.example.medlog.notification.NotificationHelper
import com.example.medlog.widget.WidgetRefreshWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 单一职责用例：管理服药操作产生的所有副作用。
 *
 * 副作用包括：日志写入、库存扣除/恢复、闹钟取消/重设、通知取消、Widget 刷新。
 *
 * [HomeViewModel] 和 [MarkTakenAction] 均通过此用例执行服药操作，确保 SSOT。
 *
 * 低库存/补药提醒通知属于"观察型"副作用，仍由 [HomeViewModel] 负责（SRP）。
 */
@Singleton
class ToggleMedicationDoseUseCase @Inject constructor(
    private val logRepo: LogRepository,
    private val medicationRepo: MedicationRepository,
    private val alarmScheduler: AlarmScheduler,
    private val notificationHelper: NotificationHelper,
    @param:ApplicationContext private val context: Context,
) {
    /**
     * 标记为已服 — 写日志、扣库存、取消闹钟/通知、刷新 Widget。
     *
     * 若今日已有日志记录，先删除再重写，保证幂等性。
     */
    suspend fun markTaken(med: Medication, existingLog: MedicationLog?) {
        val (start, end) = todayRange()
        logRepo.deleteLogsForDate(med.id, start, end)
        logRepo.insertLog(
            MedicationLog(
                medicationId = med.id,
                scheduledTimeMs = scheduledMs(med),
                actualTakenTimeMs = System.currentTimeMillis(),
                status = LogStatus.TAKEN,
            ),
        )
        med.stock?.let { stock ->
            medicationRepo.updateStock(med.id, (stock - med.doseQuantity).coerceAtLeast(0.0))
        }
        alarmScheduler.cancelAllAlarms(med.id)
        notificationHelper.cancelAllReminderNotifications(med.id)
        WidgetRefreshWorker.scheduleImmediateRefresh(context)
    }

    /**
     * 从 Widget 直接打卡 — 按 ID 获取药品后调用 [markTaken]。
     * 适用于无法注入 Medication 实体的 Glance ActionCallback。
     */
    suspend fun markTakenById(medId: Long) {
        val med = medicationRepo.getMedicationById(medId) ?: return
        val (start, end) = todayRange()
        val existingLog = logRepo.getLogForMedicationAndDate(medId, start, end)
        markTaken(med, existingLog)
    }

    /** 标记为跳过 — 写日志、取消闹钟/通知、刷新 Widget */
    suspend fun markSkipped(med: Medication) {
        val (start, end) = todayRange()
        logRepo.deleteLogsForDate(med.id, start, end)
        logRepo.insertLog(
            MedicationLog(
                medicationId = med.id,
                scheduledTimeMs = scheduledMs(med),
                actualTakenTimeMs = null,
                status = LogStatus.SKIPPED,
            ),
        )
        alarmScheduler.cancelAllAlarms(med.id)
        notificationHelper.cancelAllReminderNotifications(med.id)
        WidgetRefreshWorker.scheduleImmediateRefresh(context)
    }

    /** 撤销已服 — 删除日志、恢复库存、重设闹钟、刷新 Widget */
    suspend fun undoTaken(med: Medication, log: MedicationLog) {
        logRepo.deleteLog(log)
        med.stock?.let { stock ->
            medicationRepo.updateStock(med.id, stock + med.doseQuantity)
        }
        alarmScheduler.scheduleAllReminders(med)
        WidgetRefreshWorker.scheduleImmediateRefresh(context)
    }

    /** 撤销跳过 — 删除日志、重设闹钟、刷新 Widget */
    suspend fun undoSkipped(med: Medication, log: MedicationLog) {
        logRepo.deleteLog(log)
        alarmScheduler.scheduleAllReminders(med)
        WidgetRefreshWorker.scheduleImmediateRefresh(context)
    }

    private fun scheduledMs(med: Medication): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, med.reminderHour)
        set(Calendar.MINUTE, med.reminderMinute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
