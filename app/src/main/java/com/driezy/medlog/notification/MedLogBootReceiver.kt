package com.driezy.medlog.notification

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.driezy.medlog.data.repository.MedicationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class MedLogBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: MedicationRepository

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            )
        ) {
            return
        }
        goAsyncSafe {
            // 设备重启或精确闹钟权限恢复后，重新为所有活跃药品调度多时间段提醒。
            val medications = repository.getActiveMedications().first()
            medications.forEach { med ->
                alarmScheduler.scheduleAllReminders(med)
            }
        }
    }
}
