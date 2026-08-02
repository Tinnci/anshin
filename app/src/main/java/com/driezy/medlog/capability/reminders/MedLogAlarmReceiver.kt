package com.driezy.medlog.capability.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.data.repository.LogRepository
import com.driezy.medlog.data.repository.MedicationRepository
import com.driezy.medlog.data.repository.UserPreferencesRepository
import com.driezy.medlog.feature.medications.application.ToggleMedicationDoseUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

private const val TAG = "AlarmReceiver"

/**
 * 结构化协程辅助：在 [BroadcastReceiver.goAsync] 返回的 PendingResult 上下文中
 * 运行 suspend 块，保证 [BroadcastReceiver.PendingResult.finish] 始终被调用。
 *
 * 使用 [SupervisorJob] 确保单个子任务失败不影响其他任务。
 */
fun BroadcastReceiver.goAsyncSafe(block: suspend CoroutineScope.() -> Unit) {
    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Error in BroadcastReceiver async work", e)
        } finally {
            pendingResult.finish()
        }
    }
}

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
    lateinit var toggleDoseUseCase: ToggleMedicationDoseUseCase

    @Inject
    lateinit var prefsRepository: UserPreferencesRepository

    @Inject
    lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val medId = intent.getLongExtra(EXTRA_MED_ID, -1L)
        val medName = intent.getStringExtra(EXTRA_MED_NAME) ?: return
        val timeIndex = intent.getIntExtra(EXTRA_TIME_INDEX, 0)
        val isEarly = intent.getBooleanExtra(EXTRA_IS_EARLY, false)
        val isFollowUp = intent.getBooleanExtra(EXTRA_IS_FOLLOW_UP, false)
        if (medId == -1L) return

        val nowMs = clock.millis()
        val scheduledMs = intent.getLongExtra(EXTRA_SCHEDULED_MS, nowMs)

        when (intent.action) {
            "ACTION_TAKEN" -> {
                goAsyncSafe {
                    val med = medicationRepo.getMedicationById(medId) ?: return@goAsyncSafe
                    val existingLog = logRepo.getLogForScheduledTime(medId, scheduledMs)
                    toggleDoseUseCase.markTaken(
                        med = med,
                        existingLog = existingLog,
                        timeSlotIndex = timeIndex,
                        scheduledTimeMs = scheduledMs,
                    )
                }
            }
            "ACTION_SKIP" -> {
                goAsyncSafe {
                    val med = medicationRepo.getMedicationById(medId) ?: return@goAsyncSafe
                    val existingLog = logRepo.getLogForScheduledTime(medId, scheduledMs)
                    toggleDoseUseCase.markSkipped(
                        med = med,
                        existingLog = existingLog,
                        timeSlotIndex = timeIndex,
                        scheduledTimeMs = scheduledMs,
                    )
                }
            }
            else -> {
                // 提前预告闹钟：只显示通知，不记录日志、不重新调度
                if (isEarly) {
                    val earlyMinutes = intent.getIntExtra("early_minutes", 15)
                    goAsyncSafe {
                        val med = medicationRepo.getMedicationById(medId) ?: return@goAsyncSafe
                        notificationHelper.showEarlyReminderNotification(
                            medId,
                            medName,
                            "${med.doseQuantity} ${med.doseUnit}",
                            earlyMinutes,
                            timeIndex,
                        )
                    }
                    return
                } // 漏服再提醒闹钟触发
                if (isFollowUp) {
                    val followUpCount = intent.getIntExtra(EXTRA_FOLLOW_UP_COUNT, 1)
                    val followUpMaxCount = intent.getIntExtra(EXTRA_FOLLOW_UP_MAX_COUNT, 1)
                    val followUpDelayMs = intent.getLongExtra(EXTRA_FOLLOW_UP_DELAY_MS, 15 * 60_000L)
                    goAsyncSafe {
                        val med = medicationRepo.getMedicationById(medId) ?: return@goAsyncSafe
                        // 检查用户是否已在原时间前后窗口内服药或跳过该药
                        val windowMs = 30 * 60_000L
                        val existingLog = logRepo.getLogForMedicationAndDate(
                            medicationId = medId,
                            startMs = scheduledMs - windowMs,
                            endMs = scheduledMs + followUpDelayMs * followUpCount + windowMs,
                        )
                        if (existingLog != null &&
                            existingLog.status != LogStatus.MISSED
                        ) {
                            return@goAsyncSafe // 已服药或跳过，不显示再提醒
                        }
                        // 显示漏服再提醒通知
                        notificationHelper.showFollowUpNotification(
                            medId,
                            medName,
                            "${med.doseQuantity} ${med.doseUnit}",
                            timeIndex,
                            followUpCount,
                            scheduledMs,
                        )
                        // 若还没到最大次数，继续调度下一次
                        if (followUpCount < followUpMaxCount) {
                            alarmScheduler.scheduleFollowUpAlarm(
                                medication = med,
                                timeIndex = timeIndex,
                                scheduledMs = scheduledMs,
                                followUpCount = followUpCount + 1,
                                followUpMaxCount = followUpMaxCount,
                                delayMs = followUpDelayMs,
                                triggerAtMs = nowMs + followUpDelayMs,
                            )
                        }
                    }
                    return
                } // 正式服药时间到：显示通知，并调度下一次闹钟
                goAsyncSafe {
                    val med = medicationRepo.getMedicationById(medId) ?: return@goAsyncSafe
                    notificationHelper.showReminderNotification(
                        medId,
                        medName,
                        "${med.doseQuantity} ${med.doseUnit}",
                        timeIndex,
                        scheduledMs,
                    )
                    // 间隔给药：等用户服药后再调度（onReceive ACTION_TAKEN 时处理）
                    // 时钟模式：立即调度下一次固定时间触发
                    if (med.intervalHours <= 0) {
                        alarmScheduler.scheduleNextReminderAfterDose(
                            medication = med,
                            timeIndex = timeIndex,
                            scheduledTimeMs = scheduledMs,
                        )
                    }
                    // 若已开启漏服再提醒，调度第一次再提醒闹钟
                    val prefs = prefsRepository.settingsFlow.first()
                    if (prefs.followUpReminderEnabled && prefs.followUpMaxCount > 0) {
                        val delayMs = prefs.followUpDelayMinutes * 60_000L
                        alarmScheduler.scheduleFollowUpAlarm(
                            medication = med,
                            timeIndex = timeIndex,
                            scheduledMs = scheduledMs,
                            followUpCount = 1,
                            followUpMaxCount = prefs.followUpMaxCount,
                            delayMs = delayMs,
                            triggerAtMs = nowMs + delayMs,
                        )
                    }
                }
            }
        }
    }
}
