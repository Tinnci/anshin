package com.driezy.medlog.capability.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.driezy.medlog.domain.ReminderReconcileReason
import com.driezy.medlog.domain.ReminderReconciler
import com.driezy.medlog.domain.ReminderReconciliationQueue
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class ReminderReconciliationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reconciler: ReminderReconciler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val reason = inputData.getString(KEY_REASON)
            ?.let { runCatching { ReminderReconcileReason.valueOf(it) }.getOrNull() }
            ?: ReminderReconcileReason.SYSTEM_EVENT
        reconciler.reconcileAll(reason)
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )

    companion object {
        internal const val KEY_REASON = "reason"
        internal const val UNIQUE_WORK_NAME = "reminder-reconciliation"
    }
}

@Singleton
class WorkManagerReminderReconciliationQueue @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReminderReconciliationQueue {
    override fun enqueue(reason: ReminderReconcileReason) {
        val request = OneTimeWorkRequestBuilder<ReminderReconciliationWorker>()
            .setInputData(workDataOf(ReminderReconciliationWorker.KEY_REASON to reason.name))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ReminderReconciliationWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
