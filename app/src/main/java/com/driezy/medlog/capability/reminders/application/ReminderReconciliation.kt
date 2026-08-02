package com.driezy.medlog.capability.reminders.application

import com.driezy.medlog.domain.ReminderReconcileReason
import com.driezy.medlog.domain.ReminderReconciler
import com.driezy.medlog.domain.ReminderReconciliationQueue
import com.driezy.medlog.domain.model.MedicationId
import javax.inject.Inject

/** Runs an immediate projection update and always leaves a durable retry behind. */
class ReconcileRemindersUseCase @Inject constructor(
    private val reconciler: ReminderReconciler,
    private val retryQueue: ReminderReconciliationQueue,
) {
    suspend fun medication(id: MedicationId, reason: ReminderReconcileReason) {
        runCatching {
            reconciler.reconcileMedication(id, reason)
        }
        runCatching {
            retryQueue.enqueue(reason)
        }
    }

    suspend fun all(reason: ReminderReconcileReason) {
        runCatching {
            reconciler.reconcileAll(reason)
        }
        runCatching {
            retryQueue.enqueue(reason)
        }
    }
}
