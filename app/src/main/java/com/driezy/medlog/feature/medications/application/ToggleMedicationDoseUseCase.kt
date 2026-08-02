package com.driezy.medlog.feature.medications.application

import com.driezy.medlog.capability.reminders.NotificationHelper
import com.driezy.medlog.capability.reminders.application.ReconcileRemindersUseCase
import com.driezy.medlog.data.local.TransactionRunner
import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.model.MedicationLog
import com.driezy.medlog.data.model.scheduledLocalTimeForSlot
import com.driezy.medlog.data.repository.LogRepository
import com.driezy.medlog.data.repository.MedicationRepository
import com.driezy.medlog.domain.ReminderReconcileReason
import com.driezy.medlog.domain.model.DoseOccurrenceId
import com.driezy.medlog.domain.model.MedicationId
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
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
    private val notificationHelper: NotificationHelper,
    private val reconcileReminders: ReconcileRemindersUseCase,
    private val clock: Clock,
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
        val occurrenceId = DoseOccurrenceId(MedicationId(med.id), Instant.ofEpochMilli(slotMs))
        val actualTakenTimeMs = clock.millis()
        val mutation = recordOccurrence(
            medication = med,
            existingLog = existingLog,
            occurrenceId = occurrenceId,
            status = LogStatus.TAKEN,
            actualTakenTimeMs = actualTakenTimeMs,
            consumedDose = med.doseQuantity,
        )
        completeDoseProjection(mutation.medication.id)
        if (mutation.changed) checkAndNotifyLowStock(mutation.medication)
    }

    /**
     * 从 Widget 直接打卡 — 按 ID 获取药品后调用 [markTaken]。
     * 适用于无法注入 Medication 实体的 Glance ActionCallback。
     */
    suspend fun markTakenById(medId: Long) {
        val med = medicationRepo.getMedicationById(medId) ?: return
        val scheduledTimeMs = scheduledMsForSlot(med, 0)
        val existingLog = logRepo.getLogForScheduledTime(medId, scheduledTimeMs)
        markTaken(med, existingLog, scheduledTimeMs = scheduledTimeMs)
    }

    /** 标记为跳过 — 写日志、取消闹钟/通知、刷新 Widget */
    suspend fun markSkipped(
        med: Medication,
        existingLog: MedicationLog? = null,
        timeSlotIndex: Int = 0,
        scheduledTimeMs: Long? = null,
    ) {
        val slotMs = scheduledTimeMs ?: scheduledMsForSlot(med, timeSlotIndex)
        val occurrenceId = DoseOccurrenceId(MedicationId(med.id), Instant.ofEpochMilli(slotMs))
        val mutation = recordOccurrence(
            medication = med,
            existingLog = existingLog,
            occurrenceId = occurrenceId,
            status = LogStatus.SKIPPED,
            actualTakenTimeMs = null,
            consumedDose = 0.0,
        )
        completeDoseProjection(mutation.medication.id)
    }

    /** 撤销已服 — 删除日志、恢复库存、重设闹钟、刷新 Widget */
    suspend fun undoTaken(med: Medication, log: MedicationLog, timeSlotIndex: Int = 0) {
        val mutation = undoOccurrence(med, log, LogStatus.TAKEN)
        reconcileReminders.medication(MedicationId(mutation.medication.id), ReminderReconcileReason.DOSE_RECORDED)
    }

    /** 撤销跳过 — 删除日志、重设闹钟、刷新 Widget */
    suspend fun undoSkipped(med: Medication, log: MedicationLog, timeSlotIndex: Int = 0) {
        val mutation = undoOccurrence(med, log, LogStatus.SKIPPED)
        reconcileReminders.medication(MedicationId(mutation.medication.id), ReminderReconcileReason.DOSE_RECORDED)
    }

    /**
     * 标记为部分服用 — 写日志（含实际剂量）、按实际剂量扣库存、取消闹钟/通知、刷新 Widget。
     *
     * @param actualQty 本次实际服用的剂量（< [Medication.doseQuantity]）
     * @param timeSlotIndex 提醒时间槽索引（0 为默认/单时间槽）
     */
    suspend fun markPartial(
        med: Medication,
        existingLog: MedicationLog?,
        actualQty: Double,
        timeSlotIndex: Int = 0,
        scheduledTimeMs: Long? = null,
    ) {
        val slotMs = scheduledTimeMs ?: scheduledMsForSlot(med, timeSlotIndex)
        val occurrenceId = DoseOccurrenceId(MedicationId(med.id), Instant.ofEpochMilli(slotMs))
        val actualTakenTimeMs = clock.millis()
        val mutation = recordOccurrence(
            medication = med,
            existingLog = existingLog,
            occurrenceId = occurrenceId,
            status = LogStatus.PARTIAL,
            actualTakenTimeMs = actualTakenTimeMs,
            consumedDose = actualQty,
        )
        completeDoseProjection(mutation.medication.id)
        if (mutation.changed) checkAndNotifyLowStock(mutation.medication)
    }

    /** 撤销部分服用 — 删除日志、按记录的实际剂量恢复库存、重设闹钟、刷新 Widget */
    suspend fun undoPartial(med: Medication, log: MedicationLog, timeSlotIndex: Int = 0) {
        val mutation = undoOccurrence(med, log, LogStatus.PARTIAL)
        reconcileReminders.medication(MedicationId(mutation.medication.id), ReminderReconcileReason.DOSE_RECORDED)
    }

    private suspend fun recordOccurrence(
        medication: Medication,
        existingLog: MedicationLog?,
        occurrenceId: DoseOccurrenceId,
        status: LogStatus,
        actualTakenTimeMs: Long?,
        consumedDose: Double,
    ): DoseMutationResult {
        val medicationId = occurrenceId.medicationId.value
        val scheduledTimeMs = occurrenceId.scheduledAt.toEpochMilli()
        var result = DoseMutationResult(changed = false, medication = medication)
        transactionRunner.withTransaction {
            val exactLog = logRepo.getLogForScheduledTime(medicationId, scheduledTimeMs)
            val previousLog = exactLog ?: existingLog
            val normalizedDose = consumedDose.coerceIn(0.0, medication.doseQuantity)
            if (previousLog.matches(status, normalizedDose)) {
                val latestMedication = medicationRepo.getMedicationById(medicationId) ?: medication
                result = DoseMutationResult(changed = false, medication = latestMedication)
                return@withTransaction
            }

            if (exactLog == null) {
                existingLog
                    ?.takeIf { it.scheduledTimeMs != scheduledTimeMs }
                    ?.let { logRepo.deleteLog(it) }
            }
            logRepo.deleteLogForScheduledTime(medicationId, scheduledTimeMs)
            logRepo.insertLog(
                MedicationLog(
                    medicationId = medicationId,
                    scheduledTimeMs = scheduledTimeMs,
                    actualTakenTimeMs = actualTakenTimeMs,
                    status = status,
                    actualDoseQuantity = normalizedDose.takeIf { status == LogStatus.PARTIAL },
                ),
            )

            val latestMedication = medicationRepo.getMedicationById(medicationId) ?: medication
            val stockDelta = normalizedDose - previousLog.consumedDose(medication.doseQuantity)
            val updatedMedication = latestMedication.stock?.let { stock ->
                latestMedication.copy(stock = (stock - stockDelta).coerceAtLeast(0.0))
            } ?: latestMedication
            if (updatedMedication.stock != latestMedication.stock) {
                medicationRepo.updateStock(updatedMedication.id, updatedMedication.stock!!)
            }
            result = DoseMutationResult(changed = true, medication = updatedMedication)
        }
        return result
    }

    private suspend fun undoOccurrence(
        medication: Medication,
        log: MedicationLog,
        expectedStatus: LogStatus,
    ): DoseMutationResult {
        var result = DoseMutationResult(changed = false, medication = medication)
        transactionRunner.withTransaction {
            val occurrenceId = DoseOccurrenceId(
                medicationId = MedicationId(medication.id),
                scheduledAt = Instant.ofEpochMilli(log.scheduledTimeMs),
            )
            val persistedLog = logRepo.getLogForScheduledTime(
                occurrenceId.medicationId.value,
                occurrenceId.scheduledAt.toEpochMilli(),
            )
            val latestMedication = medicationRepo.getMedicationById(occurrenceId.medicationId.value) ?: medication
            if (persistedLog?.status != expectedStatus) {
                result = DoseMutationResult(changed = false, medication = latestMedication)
                return@withTransaction
            }
            logRepo.deleteLog(persistedLog)
            val restoredMedication = latestMedication.stock?.let { stock ->
                latestMedication.copy(stock = stock + persistedLog.consumedDose(medication.doseQuantity))
            } ?: latestMedication
            if (restoredMedication.stock != latestMedication.stock) {
                medicationRepo.updateStock(restoredMedication.id, restoredMedication.stock!!)
            }
            result = DoseMutationResult(changed = true, medication = restoredMedication)
        }
        return result
    }

    private suspend fun completeDoseProjection(medicationId: Long) {
        reconcileReminders.medication(MedicationId(medicationId), ReminderReconcileReason.DOSE_RECORDED)
    }

    /**
     * 计算指定时间槽的今日计划服药时间戳（毫秒）。
     *
     * - [timeSlotIndex] is resolved through the typed schedule adapter.
     * - An invalid slot falls back inside the persistence mapper for compatibility.
     */
    private fun scheduledMsForSlot(med: Medication, timeSlotIndex: Int): Long {
        val time = med.scheduledLocalTimeForSlot(timeSlotIndex)
        return LocalDate.now(clock)
            .atTime(time)
            .atZone(clock.zone)
            .toInstant()
            .toEpochMilli()
    }

    /**
     * 服药后检查库存是否低于阈值，低于则推送低库存通知。
     * 统一在 UseCase 内处理，避免 ViewModel 重复计算。
     */
    private fun checkAndNotifyLowStock(med: Medication) {
        val stock = med.stock ?: return
        val threshold = med.refillThreshold ?: return
        if (stock <= threshold) {
            runCatching {
                notificationHelper.showLowStockNotification(
                    medicationId = med.id,
                    medicationName = med.name,
                    stock = stock,
                    unit = med.doseUnit,
                )
            }
        }
    }
}

private data class DoseMutationResult(val changed: Boolean, val medication: Medication)

private fun MedicationLog?.consumedDose(plannedDose: Double): Double = when (this?.status) {
    LogStatus.TAKEN -> plannedDose
    LogStatus.PARTIAL -> actualDoseQuantity ?: 0.0
    else -> 0.0
}

private fun MedicationLog?.matches(status: LogStatus, consumedDose: Double): Boolean = when {
    this == null || this.status != status -> false
    status == LogStatus.PARTIAL -> actualDoseQuantity == consumedDose
    else -> true
}
