package com.driezy.medlog.domain

import com.driezy.medlog.data.local.TransactionRunner
import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.model.MedicationLog
import com.driezy.medlog.data.repository.LogRepository
import com.driezy.medlog.data.repository.MedicationRepository
import com.driezy.medlog.notification.AlarmScheduler
import com.driezy.medlog.notification.NotificationHelper
import com.driezy.medlog.widget.WidgetRefresher
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
 * 低库存/补药提醒通知也由此用例统一触发，而非分散在 ViewModel 中（SRP）。
 */
@Singleton
class ToggleMedicationDoseUseCase @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val logRepo: LogRepository,
    private val medicationRepo: MedicationRepository,
    private val alarmScheduler: AlarmScheduler,
    private val notificationHelper: NotificationHelper,
    private val widgetRefresher: WidgetRefresher,
) {
    /**
     * 标记为已服 — 写日志、扣库存、取消闹钟/通知、刷新 Widget。
     *
     * 若该时间槽今日已有日志记录，先删除再重写，保证幂等性。
     *
     * @param timeSlotIndex 提醒时间槽索引（0 为默认/单时间槽）
     */
    suspend fun markTaken(
        med: Medication,
        existingLog: MedicationLog?,
        timeSlotIndex: Int = 0,
        scheduledTimeMs: Long? = null,
    ) {
        val slotMs = scheduledTimeMs ?: scheduledMsForSlot(med, timeSlotIndex)
        val actualTakenTimeMs = System.currentTimeMillis()
        transactionRunner.withTransaction {
            existingLog
                ?.takeIf { it.scheduledTimeMs != slotMs }
                ?.let { logRepo.deleteLog(it) }
            logRepo.deleteLogForScheduledTime(med.id, slotMs)
            logRepo.insertLog(
                MedicationLog(
                    medicationId = med.id,
                    scheduledTimeMs = slotMs,
                    actualTakenTimeMs = actualTakenTimeMs,
                    status = LogStatus.TAKEN,
                ),
            )
            med.stock?.let { stock ->
                medicationRepo.updateStock(med.id, (stock - med.doseQuantity).coerceAtLeast(0.0))
            }
        }
        completeDoseReminders(
            med = med,
            timeSlotIndex = timeSlotIndex,
            scheduledTimeMs = slotMs,
            actualTakenTimeMs = actualTakenTimeMs,
        )
        widgetRefresher.refreshAll()
        checkAndNotifyLowStock(med)
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
    suspend fun markSkipped(
        med: Medication,
        existingLog: MedicationLog? = null,
        timeSlotIndex: Int = 0,
        scheduledTimeMs: Long? = null,
    ) {
        val slotMs = scheduledTimeMs ?: scheduledMsForSlot(med, timeSlotIndex)
        transactionRunner.withTransaction {
            existingLog
                ?.takeIf { it.scheduledTimeMs != slotMs }
                ?.let { logRepo.deleteLog(it) }
            logRepo.deleteLogForScheduledTime(med.id, slotMs)
            logRepo.insertLog(
                MedicationLog(
                    medicationId = med.id,
                    scheduledTimeMs = slotMs,
                    actualTakenTimeMs = null,
                    status = LogStatus.SKIPPED,
                ),
            )
        }
        completeDoseReminders(med, timeSlotIndex, slotMs)
        widgetRefresher.refreshAll()
    }

    /** 撤销已服 — 删除日志、恢复库存、重设闹钟、刷新 Widget */
    suspend fun undoTaken(med: Medication, log: MedicationLog, timeSlotIndex: Int = 0) {
        logRepo.deleteLog(log)
        med.stock?.let { stock ->
            medicationRepo.updateStock(med.id, stock + med.doseQuantity)
        }
        alarmScheduler.restoreReminderForDose(med, timeSlotIndex)
        widgetRefresher.refreshAll()
    }

    /** 撤销跳过 — 删除日志、重设闹钟、刷新 Widget */
    suspend fun undoSkipped(med: Medication, log: MedicationLog, timeSlotIndex: Int = 0) {
        logRepo.deleteLog(log)
        alarmScheduler.restoreReminderForDose(med, timeSlotIndex)
        widgetRefresher.refreshAll()
    }

    /**
     * 标记为部分服用 — 写日志（含实际剂量）、按实际剂量扣库存、取消闹钟/通知、刷新 Widget。
     *
     * @param actualQty 本次实际服用的剂量（< [Medication.doseQuantity]）
     * @param timeSlotIndex 提醒时间槽索引（0 为默认/单时间槽）
     */
    suspend fun markPartial(med: Medication, existingLog: MedicationLog?, actualQty: Double, timeSlotIndex: Int = 0) {
        val slotMs = scheduledMsForSlot(med, timeSlotIndex)
        val actualTakenTimeMs = System.currentTimeMillis()
        transactionRunner.withTransaction {
            existingLog
                ?.takeIf { it.scheduledTimeMs != slotMs }
                ?.let { logRepo.deleteLog(it) }
            logRepo.deleteLogForScheduledTime(med.id, slotMs)
            logRepo.insertLog(
                MedicationLog(
                    medicationId = med.id,
                    scheduledTimeMs = slotMs,
                    actualTakenTimeMs = actualTakenTimeMs,
                    status = LogStatus.PARTIAL,
                    actualDoseQuantity = actualQty,
                ),
            )
            med.stock?.let { stock ->
                medicationRepo.updateStock(med.id, (stock - actualQty).coerceAtLeast(0.0))
            }
        }
        completeDoseReminders(
            med = med,
            timeSlotIndex = timeSlotIndex,
            scheduledTimeMs = slotMs,
            actualTakenTimeMs = actualTakenTimeMs,
        )
        widgetRefresher.refreshAll()
        checkAndNotifyLowStock(med, actualQty)
    }

    /** 撤销部分服用 — 删除日志、按记录的实际剂量恢复库存、重设闹钟、刷新 Widget */
    suspend fun undoPartial(med: Medication, log: MedicationLog, timeSlotIndex: Int = 0) {
        logRepo.deleteLog(log)
        med.stock?.let { stock ->
            medicationRepo.updateStock(med.id, stock + (log.actualDoseQuantity ?: 0.0))
        }
        alarmScheduler.restoreReminderForDose(med, timeSlotIndex)
        widgetRefresher.refreshAll()
    }

    /**
     * 取消某药品的所有闹钟及提醒通知。
     * 供 [MedicationDetailViewModel] 在归档/删除药品时调用，保持 SRP。
     */
    suspend fun cancelAllReminders(medId: Long) {
        alarmScheduler.cancelAllAlarms(medId)
        notificationHelper.cancelAllReminderNotifications(medId)
    }

    private fun completeDoseReminders(
        med: Medication,
        timeSlotIndex: Int,
        scheduledTimeMs: Long,
        actualTakenTimeMs: Long? = null,
    ) {
        alarmScheduler.cancelAlarmSlot(med.id, timeSlotIndex)
        notificationHelper.cancelReminderNotification(med.id, timeSlotIndex)
        notificationHelper.cancelEarlyReminderNotification(med.id, timeSlotIndex)
        notificationHelper.cancelFollowUpNotification(med.id, timeSlotIndex)
        alarmScheduler.scheduleNextReminderAfterDose(
            medication = med,
            timeIndex = timeSlotIndex,
            scheduledTimeMs = scheduledTimeMs,
            actualTakenTimeMs = actualTakenTimeMs,
        )
    }

    private fun scheduledMs(med: Medication): Long = scheduledMsForSlot(med, 0)

    /**
     * 计算指定时间槽的今日计划服药时间戳（毫秒）。
     *
     * - [timeSlotIndex] = 0 对应 [Medication.reminderTimes] 中的第一个时间
     * - 若索引越界则回退到 reminderHour/reminderMinute（向后兼容）
     */
    private fun scheduledMsForSlot(med: Medication, timeSlotIndex: Int): Long {
        val times = med.reminderTimes.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val timeStr = times.getOrNull(timeSlotIndex)
        val (hour, minute) = if (timeStr != null) {
            val parts = timeStr.split(":").mapNotNull { it.toIntOrNull() }
            (parts.getOrElse(0) { med.reminderHour }) to (parts.getOrElse(1) { med.reminderMinute })
        } else {
            med.reminderHour to med.reminderMinute
        }
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * 服药后检查库存是否低于阈值，低于则推送低库存通知。
     * 统一在 UseCase 内处理，避免 ViewModel 重复计算。
     */
    private fun checkAndNotifyLowStock(med: Medication, consumedQty: Double = med.doseQuantity) {
        val stock = med.stock ?: return
        val newStock = (stock - consumedQty).coerceAtLeast(0.0)
        val threshold = med.refillThreshold ?: return
        if (newStock <= threshold) {
            notificationHelper.showLowStockNotification(
                medicationId = med.id,
                medicationName = med.name,
                stock = newStock,
                unit = med.doseUnit,
            )
        }
    }
}
