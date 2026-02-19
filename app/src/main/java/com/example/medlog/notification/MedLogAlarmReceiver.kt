package com.example.medlog.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.medlog.data.model.LogStatus
import com.example.medlog.data.model.MedicationLog
import com.example.medlog.data.repository.LogRepository
import com.example.medlog.data.repository.MedicationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class MedLogAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var medicationRepo: MedicationRepository

    @Inject
    lateinit var logRepo: LogRepository

    override fun onReceive(context: Context, intent: Intent) {
        val medId = intent.getLongExtra(EXTRA_MED_ID, -1L)
        val medName = intent.getStringExtra(EXTRA_MED_NAME) ?: return
        if (medId == -1L) return

        when (intent.action) {
            "ACTION_TAKEN" -> {
                notificationHelper.cancelReminder(medId)
                CoroutineScope(Dispatchers.IO).launch {
                    logRepo.insertLog(
                        MedicationLog(
                            medicationId = medId,
                            scheduledTimeMs = dayStartMs(),
                            actualTakenTimeMs = System.currentTimeMillis(),
                            status = LogStatus.TAKEN,
                        )
                    )
                }
            }
            "ACTION_SKIP" -> {
                notificationHelper.cancelReminder(medId)
                CoroutineScope(Dispatchers.IO).launch {
                    logRepo.insertLog(
                        MedicationLog(
                            medicationId = medId,
                            scheduledTimeMs = dayStartMs(),
                            actualTakenTimeMs = null,
                            status = LogStatus.SKIPPED,
                        )
                    )
                }
            }
            else -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val med = medicationRepo.getMedicationById(medId) ?: return@launch
                    notificationHelper.showReminderNotification(
                        medId,
                        medName,
                        "${med.doseQuantity} ${med.doseUnit}",
                    )
                }
            }
        }
    }

    private fun dayStartMs(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

}
