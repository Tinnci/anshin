package com.driezy.medlog.domain

import com.driezy.medlog.domain.model.MedicationId

enum class ReminderReconcileReason {
    MEDICATION_CHANGED,
    ROUTINE_CHANGED,
    DOSE_RECORDED,
    SYSTEM_EVENT,
    DATA_RESTORED,
}

interface ReminderReconciler {
    suspend fun reconcileMedication(id: MedicationId, reason: ReminderReconcileReason)
    suspend fun reconcileAll(reason: ReminderReconcileReason)
}

interface ReminderReconciliationQueue {
    fun enqueue(reason: ReminderReconcileReason)
}
