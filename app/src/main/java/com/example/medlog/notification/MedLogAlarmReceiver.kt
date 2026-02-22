package com.example.medlog.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.medlog.data.model.LogStatus
import com.example.medlog.data.model.MedicationLog
import com.example.medlog.data.repository.LogRepository
import com.example.medlog.data.repository.MedicationRepository
import com.example.medlog.data.repository.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MedLogAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    @Inject
    lateinit var medicationRepo: MedicationRepository

    @Inject
    lateinit var logRepo: LogRepository

    @Inject
    lateinit var prefsRepository: UserPreferencesRepository

    override fun onReceive(context: Context, intent: Intent) {
        val medId = intent.getLongExtra(EXTRA_MED_ID, -1L)
        val medName = intent.getStringExtra(EXTRA_MED_NAME) ?: return
        val timeIndex = intent.getIntExtra(EXTRA_TIME_INDEX, 0)
        val isEarly = intent.getBooleanExtra(EXTRA_IS_EARLY, false)
        val isFollowUp = intent.getBooleanExtra(EXTRA_IS_FOLLOW_UP, false)
        if (medId == -1L) return

        val nowMs = System.currentTimeMillis()
        // goAsync() 防止系统在协程完成前 kill 进程
        val pendingResult = goAsync()

        when (intent.action) {
            "ACTION_TAKEN" -> {
                // 仅取消本时间槽通知，其他时间槽提醒不受影响
                notificationHelper.cancelReminderNotification(medId, timeIndex)
                notificationHelper.cancelFollowUpNotification(medId, timeIndex)
                alarmScheduler.cancelFollowUpAlarms(medId)
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
                            // 重新调度本时间槽的下一轮提醒（间隔模式传入实际服药时间）
                            rescheduleNext(med, timeIndex, lastTakenMs = nowMs)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            "ACTION_SKIP" -> {
                // 仅取消本时间槽通知，其他时间槽提醒不受影响
                notificationHelper.cancelReminderNotification(medId, timeIndex)
                notificationHelper.cancelFollowUpNotification(medId, timeIndex)
                alarmScheduler.cancelFollowUpAlarms(medId)
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
                // 提前预告闹钟：只显示通知，不记录日志、不重新调度
                if (isEarly) {
                    val earlyMinutes = intent.getIntExtra("early_minutes", 15)
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val med = medicationRepo.getMedicationById(medId) ?: return@launch
                            notificationHelper.showEarlyReminderNotification(
                                medId,
                                medName,
                                "${med.doseQuantity} ${med.doseUnit}",
                                earlyMinutes,
                                timeIndex,
                            )
                        } finally {
                            pendingResult.finish()
                        }
                    }
                    return
                }                // 漏服再提醒闳钟触发
                if (isFollowUp) {
                    val followUpCount    = intent.getIntExtra(EXTRA_FOLLOW_UP_COUNT, 1)
                    val followUpMaxCount = intent.getIntExtra(EXTRA_FOLLOW_UP_MAX_COUNT, 1)
                    val followUpDelayMs  = intent.getLongExtra(EXTRA_FOLLOW_UP_DELAY_MS, 15 * 60_000L)
                    val scheduledMs      = intent.getLongExtra(EXTRA_SCHEDULED_MS, nowMs)
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val med = medicationRepo.getMedicationById(medId) ?: return@launch
                            // 检查用户是否已在原时间前后窗口内服辩或跳过该药
                            val windowMs   = 30 * 60_000L
                            val existingLog = logRepo.getLogForMedicationAndDate(
                                medicationId = medId,
                                startMs = scheduledMs - windowMs,
                                endMs   = scheduledMs + followUpDelayMs * followUpCount + windowMs,
                            )
                            if (existingLog != null &&
                                existingLog.status != com.example.medlog.data.model.LogStatus.MISSED
                            ) return@launch  // 已服辩或跳过，不显示再提醒
                            // 显示漏服再提醒通知
                            notificationHelper.showFollowUpNotification(
                                medId, medName,
                                "${med.doseQuantity} ${med.doseUnit}",
                                timeIndex, followUpCount,
                            )
                            // 若还没到最大次数，继续调度下一次
                            if (followUpCount < followUpMaxCount) {
                                alarmScheduler.scheduleFollowUpAlarm(
                                    medication       = med,
                                    timeIndex        = timeIndex,
                                    scheduledMs      = scheduledMs,
                                    followUpCount    = followUpCount + 1,
                                    followUpMaxCount = followUpMaxCount,
                                    delayMs          = followUpDelayMs,
                                    triggerAtMs      = nowMs + followUpDelayMs,
                                )
                            }
                        } finally {
                            pendingResult.finish()
                        }
                    }
                    return
                }                // 正式服药时间到：显示通知，并调度下一次闹钟
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val med = medicationRepo.getMedicationById(medId) ?: return@launch
                        notificationHelper.showReminderNotification(
                            medId,
                            medName,
                            "${med.doseQuantity} ${med.doseUnit}",
                            timeIndex,
                        )
                        // 间隔给药：等用户服药后再调度（onReceive ACTION_TAKEN 时处理）
                        // 时钟模式：立即调度下一次固定时间触发
                        if (med.intervalHours <= 0) {
                            val times = med.reminderTimes.split(",").map { it.trim() }
                            val timeStr = times.getOrNull(timeIndex) ?: return@launch
                            val nextMs = alarmScheduler.computeNextTrigger(
                                timeStr = timeStr,
                                frequencyType = med.frequencyType,
                                frequencyInterval = med.frequencyInterval,
                                frequencyDays = med.frequencyDays,
                                endDateMs = med.endDate,
                            ) ?: return@launch
                            alarmScheduler.scheduleAlarmSlot(med, timeIndex, nextMs)
                            // 同时为下次主提醒调度配套的提前预告
                            alarmScheduler.scheduleAllReminders(med)
                        }
                        // 若已开启漏服再提醒，调度第一次再提醒闳钟
                        val prefs = prefsRepository.settingsFlow.first()
                        if (prefs.followUpReminderEnabled && prefs.followUpMaxCount > 0) {
                            val delayMs = prefs.followUpDelayMinutes * 60_000L
                            alarmScheduler.scheduleFollowUpAlarm(
                                medication       = med,
                                timeIndex        = timeIndex,
                                scheduledMs      = nowMs,
                                followUpCount    = 1,
                                followUpMaxCount = prefs.followUpMaxCount,
                                delayMs          = delayMs,
                                triggerAtMs      = nowMs + delayMs,
                            )
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    /** 重新调度指定药品某时间槽的下一次提醒 */
    private suspend fun rescheduleNext(medId: Long, timeIndex: Int, lastTakenMs: Long? = null) {
        val med = medicationRepo.getMedicationById(medId) ?: return
        rescheduleNext(med, timeIndex, lastTakenMs)
    }

    /** 直接传入已读取的 Medication 对象，避免重复 DB 查询 */
    private suspend fun rescheduleNext(
        med: com.example.medlog.data.model.Medication,
        timeIndex: Int,
        lastTakenMs: Long? = null,
    ) {
        // 间隔给药模式：下一次触发 = 上次服药时间 + interval
        if (med.intervalHours > 0) {
            val base = lastTakenMs ?: System.currentTimeMillis()
            val nextMs = base + med.intervalHours * 3_600_000L
            alarmScheduler.scheduleAlarmSlot(med, 0, nextMs)
            return
        }
        val times = med.reminderTimes.split(",").map { it.trim() }
        val timeStr = times.getOrNull(timeIndex) ?: return
        val nextMs = alarmScheduler.computeNextTrigger(
            timeStr = timeStr,
            frequencyType = med.frequencyType,
            frequencyInterval = med.frequencyInterval,
            frequencyDays = med.frequencyDays,
            endDateMs = med.endDate,
        ) ?: return
        alarmScheduler.scheduleAlarmSlot(med, timeIndex, nextMs)
    }
}
