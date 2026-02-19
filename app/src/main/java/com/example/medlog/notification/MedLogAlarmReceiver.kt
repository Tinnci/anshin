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
        val timeIndex = intent.getIntExtra(EXTRA_TIME_INDEX, 0)
        if (medId == -1L) return

        val nowMs = System.currentTimeMillis()

        when (intent.action) {
            "ACTION_TAKEN" -> {
                // 用户点击"已服用"操作按钮
                notificationHelper.cancelAllReminders(medId)
                CoroutineScope(Dispatchers.IO).launch {
                    logRepo.insertLog(
                        MedicationLog(
                            medicationId = medId,
                            scheduledTimeMs = nowMs,
                            actualTakenTimeMs = nowMs,
                            status = LogStatus.TAKEN,
                        )
                    )
                    // 重新调度下一轮提醒
                    rescheduleNext(medId, timeIndex)
                }
            }
            "ACTION_SKIP" -> {
                // 用户点击"跳过"操作按钮
                notificationHelper.cancelAllReminders(medId)
                CoroutineScope(Dispatchers.IO).launch {
                    logRepo.insertLog(
                        MedicationLog(
                            medicationId = medId,
                            scheduledTimeMs = nowMs,
                            actualTakenTimeMs = null,
                            status = LogStatus.SKIPPED,
                        )
                    )
                    rescheduleNext(medId, timeIndex)
                }
            }
            else -> {
                // 闹钟触发：显示通知，并调度下一次闹钟
                CoroutineScope(Dispatchers.IO).launch {
                    val med = medicationRepo.getMedicationById(medId) ?: return@launch
                    notificationHelper.showReminderNotification(
                        medId,
                        medName,
                        "${med.doseQuantity} ${med.doseUnit}",
                        timeIndex,
                    )
                    // 为当前时间槽调度下一次触发
                    val times = med.reminderTimes.split(",").map { it.trim() }
                    val timeStr = times.getOrNull(timeIndex) ?: return@launch
                    val nextMs = notificationHelper.computeNextTrigger(
                        timeStr = timeStr,
                        frequencyType = med.frequencyType,
                        frequencyInterval = med.frequencyInterval,
                        frequencyDays = med.frequencyDays,
                        endDateMs = med.endDate,
                    ) ?: return@launch
                    notificationHelper.scheduleAlarmSlot(med, timeIndex, nextMs)
                }
            }
        }
    }

    /** 重新调度指定药品某时间槽的下一次提醒 */
    private suspend fun rescheduleNext(medId: Long, timeIndex: Int) {
        val med = medicationRepo.getMedicationById(medId) ?: return
        val times = med.reminderTimes.split(",").map { it.trim() }
        val timeStr = times.getOrNull(timeIndex) ?: return
        val nextMs = notificationHelper.computeNextTrigger(
            timeStr = timeStr,
            frequencyType = med.frequencyType,
            frequencyInterval = med.frequencyInterval,
            frequencyDays = med.frequencyDays,
            endDateMs = med.endDate,
        ) ?: return
        notificationHelper.scheduleAlarmSlot(med, timeIndex, nextMs)
    }
}
