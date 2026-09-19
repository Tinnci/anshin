package com.driezy.medlog.capability.reminders

import com.driezy.medlog.capability.widgets.WidgetRefresher
import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.data.repository.LogRepository
import com.driezy.medlog.data.repository.MedicationRepository
import com.driezy.medlog.domain.ReminderReconcileReason
import com.driezy.medlog.domain.ReminderReconciler
import com.driezy.medlog.domain.model.MedicationId
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidReminderReconciler @Inject constructor(
    private val medications: MedicationRepository,
    private val logs: LogRepository,
    private val alarmScheduler: AlarmScheduler,
    private val notificationHelper: NotificationHelper,
    private val widgetRefresher: WidgetRefresher,
) : ReminderReconciler {
    override suspend fun reconcileMedication(id: MedicationId, reason: ReminderReconcileReason) {
        val medication = medications.getMedicationById(id.value)
        alarmScheduler.cancelAllAlarms(id.value)
        notificationHelper.cancelAllReminderNotifications(id.value)
        if (medication != null && !medication.isArchived && !medication.isPRN) {
            schedule(medication)
        }
        widgetRefresher.refreshAll()
    }

    override suspend fun reconcileAll(reason: ReminderReconcileReason) {
        alarmScheduler.cancelAllKnownAlarms().forEach(notificationHelper::cancelAllReminderNotifications)
        medications.getAllMedications().first().forEach { medication ->
            alarmScheduler.cancelAllAlarms(medication.id)
            notificationHelper.cancelAllReminderNotifications(medication.id)
            if (!medication.isArchived && !medication.isPRN) {
                schedule(medication)
            }
        }
        widgetRefresher.refreshAll()
    }

    private suspend fun schedule(medication: com.driezy.medlog.data.model.Medication) {
        val recorded = logs.getLogsForMedication(medication.id, limit = Int.MAX_VALUE).first()
        val handled = recorded.filter { it.status in setOf(LogStatus.TAKEN, LogStatus.PARTIAL, LogStatus.SKIPPED) }
        val lastTaken = handled.filter {
            it.scheduledTimeMs >= medication.planEffectiveFromMs &&
                it.actualTakenTimeMs != null
        }.maxByOrNull { it.scheduledTimeMs }?.actualTakenTimeMs
        alarmScheduler.scheduleAllReminders(
            medication,
            lastTaken,
            handled.map { Instant.ofEpochMilli(it.scheduledTimeMs) }.toSet(),
        )
    }
}
