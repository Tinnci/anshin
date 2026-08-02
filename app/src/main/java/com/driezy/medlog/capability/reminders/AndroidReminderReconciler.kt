package com.driezy.medlog.capability.reminders

import com.driezy.medlog.capability.widgets.WidgetRefresher
import com.driezy.medlog.data.repository.LogRepository
import com.driezy.medlog.data.repository.MedicationRepository
import com.driezy.medlog.domain.ReminderReconcileReason
import com.driezy.medlog.domain.ReminderReconciler
import com.driezy.medlog.domain.model.MedicationId
import kotlinx.coroutines.flow.first
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
            val lastTakenMs = logs.getLogsForMedication(id.value, limit = 1).first()
                .firstOrNull()
                ?.actualTakenTimeMs
            alarmScheduler.scheduleAllReminders(medication, lastTakenMs)
        }
        widgetRefresher.refreshAll()
    }

    override suspend fun reconcileAll(reason: ReminderReconcileReason) {
        alarmScheduler.cancelAllKnownAlarms().forEach(notificationHelper::cancelAllReminderNotifications)
        medications.getAllMedications().first().forEach { medication ->
            alarmScheduler.cancelAllAlarms(medication.id)
            notificationHelper.cancelAllReminderNotifications(medication.id)
            if (!medication.isArchived && !medication.isPRN) {
                val lastTakenMs = logs.getLogsForMedication(medication.id, limit = 1).first()
                    .firstOrNull()
                    ?.actualTakenTimeMs
                alarmScheduler.scheduleAllReminders(medication, lastTakenMs)
            }
        }
        widgetRefresher.refreshAll()
    }
}
