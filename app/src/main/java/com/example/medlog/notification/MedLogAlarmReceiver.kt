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
        // goAsync() 防止系统在协程完成前 kill 进程
        val pendingResult = goAsync()

        when (intent.action) {
            "ACTION_TAKEN" -> {
                // 仅取消本时间槽通知，其他时间槽提醒不受影响
                notificationHelper.cancelReminderNotification(medId, timeIndex)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        logRepo.insertLog(
                            MedicationLog(
                                medicationId = medId,
                                scheduledTimeMs = nowMs,
                                actualTakenTimeMs = nowMs,
                                status = LogStatus.TAKEN,
                            )
                        )
                        // 扣减库存
                        val med = medicationRepo.getMedicationById(medId)
                        if (med != null) {
                            val newStock = ((med.stock ?: 0.0) - med.doseQuantity).coerceAtLeast(0.0)
                            medicationRepo.updateStock(medId, newStock)
                            // 重新调度本时间槽的下一轮提醒
                            rescheduleNext(med, timeIndex)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            "ACTION_SKIP" -> {
                // 仅取消本时间槽通知，其他时间槽提醒不受影响
                notificationHelper.cancelReminderNotification(medId, timeIndex)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        logRepo.insertLog(
                            MedicationLog(
                                medicationId = medId,
                                scheduledTimeMs = nowMs,
                                actualTakenTimeMs = null,
                                status = LogStatus.SKIPPED,
                            )
                        )
                        rescheduleNext(medId, timeIndex)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            else -> {
                // 闹钟触发：显示通知，并调度下一次闹钟
                CoroutineScope(Dispatchers.IO).launch {
                    try {
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
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    /** 重新调度指定药品某时间槽的下一次提醒 */
    private suspend fun rescheduleNext(medId: Long, timeIndex: Int) {
        val med = medicationRepo.getMedicationById(medId) ?: return
        rescheduleNext(med, timeIndex)
    }

    /** 直接传入已读取的 Medication 对象，避免重复 DB 查询 */
    private suspend fun rescheduleNext(med: com.example.medlog.data.model.Medication, timeIndex: Int) {
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
