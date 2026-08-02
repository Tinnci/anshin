package com.driezy.medlog.capability.reminders.application

import com.driezy.medlog.domain.ReminderReconcileReason
import com.driezy.medlog.domain.ReminderReconciler
import com.driezy.medlog.domain.ReminderReconciliationQueue
import com.driezy.medlog.domain.model.MedicationId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcileRemindersUseCaseTest {
    @Test
    fun `durable retry is enqueued after successful immediate reconciliation`() = runTest {
        val reconciler = FakeReminderReconciler()
        val queue = FakeReminderQueue()

        ReconcileRemindersUseCase(reconciler, queue).medication(
            MedicationId(7L),
            ReminderReconcileReason.MEDICATION_CHANGED,
        )

        assertEquals(listOf(MedicationId(7L)), reconciler.medicationIds)
        assertEquals(listOf(ReminderReconcileReason.MEDICATION_CHANGED), queue.reasons)
    }

    @Test
    fun `durable retry is still enqueued when immediate reconciliation fails`() = runTest {
        val reconciler = FakeReminderReconciler(fail = true)
        val queue = FakeReminderQueue()

        val outcome = runCatching {
            ReconcileRemindersUseCase(reconciler, queue).all(ReminderReconcileReason.ROUTINE_CHANGED)
        }

        assertTrue("a committed command must not be reported as failed by a projection", outcome.isSuccess)
        assertEquals(listOf(ReminderReconcileReason.ROUTINE_CHANGED), queue.reasons)
    }

    @Test
    fun `queue failure cannot turn a completed business command into a half-success error`() = runTest {
        val outcome = runCatching {
            ReconcileRemindersUseCase(FakeReminderReconciler(), FakeReminderQueue(fail = true)).medication(
                MedicationId(7L),
                ReminderReconcileReason.DOSE_RECORDED,
            )
        }

        assertTrue(outcome.isSuccess)
    }
}

private class FakeReminderReconciler(private val fail: Boolean = false) : ReminderReconciler {
    val medicationIds = mutableListOf<MedicationId>()

    override suspend fun reconcileMedication(id: MedicationId, reason: ReminderReconcileReason) {
        medicationIds += id
        if (fail) error("projection unavailable")
    }

    override suspend fun reconcileAll(reason: ReminderReconcileReason) {
        if (fail) error("projection unavailable")
    }
}

private class FakeReminderQueue(private val fail: Boolean = false) : ReminderReconciliationQueue {
    val reasons = mutableListOf<ReminderReconcileReason>()

    override fun enqueue(reason: ReminderReconcileReason) {
        if (fail) error("queue unavailable")
        reasons += reason
    }
}
