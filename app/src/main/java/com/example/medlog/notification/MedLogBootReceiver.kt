package com.example.medlog.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.medlog.data.repository.MedicationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class MedLogBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: MedicationRepository

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        CoroutineScope(Dispatchers.IO).launch {
            val medications = repository.getActiveMedications().first()
            medications.forEach { med ->
                val triggerMs = nextTriggerMs(med.reminderHour, med.reminderMinute)
                notificationHelper.scheduleAlarm(med, triggerMs)
            }
        }
    }

    private fun nextTriggerMs(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
