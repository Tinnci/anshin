package com.driezy.medlog.capability.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.model.toDomainSchedule
import com.driezy.medlog.data.repository.UserPreferencesRepository
import com.driezy.medlog.data.repository.reminderZone
import com.driezy.medlog.di.ApplicationScope
import com.driezy.medlog.domain.ReminderPlanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_REMINDER_SLOTS = 20
private const val ALARM_PROJECTION_PREFERENCES = "alarm_projection_registry"
private const val REGISTERED_MEDICATION_IDS = "registered_medication_ids"

/** PendingIntent requestCode 偏移：提前预告闹钟用，避免与正式提醒冲突 */
const val EARLY_REMINDER_CODE_OFFSET = 50_000

/** PendingIntent requestCode 偏移：漏服再提醒闹钟 */
const val FOLLOW_UP_CODE_OFFSET = 100_000

/**
 * 闹钟调度器。
 *
 * **单一职责**：管理所有服药提醒闹钟的调度与取消。
 * 不涉及任何通知 UI 内容 —— 见 [NotificationHelper]。
 *
 * 依赖注入（Hilt）：[Singleton]，整个 App 生命周期内唯一实例。
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefsRepository: UserPreferencesRepository,
    private val reminderPlanner: ReminderPlanner,
    private val clock: Clock,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val projectionRegistry = context.getSharedPreferences(ALARM_PROJECTION_PREFERENCES, Context.MODE_PRIVATE)

    /** 旅行模式：家乡时区，由后台协程实时同步 */
    @Volatile private var reminderZoneId: ZoneId = clock.zone

    /** 提前预告提醒分钟数（0 = 关闭），由 DataStore 实时同步 */
    @Volatile private var earlyReminderMinutes: Int = 0

    init {
        // 监听旅行模式 / 家乡时区变化
        scope.launch {
            prefsRepository.settingsFlow.collect { prefs ->
                reminderZoneId = prefs.reminderZone(clock.zone)
                earlyReminderMinutes = prefs.earlyReminderMinutes
            }
        }
    }

    // ─── 调度 ──────────────────────────────────────────────────────────────

    /**
     * 根据药品配置为每个时间槽调度下一次提醒闹钟。
     * PRN（按需服用）药品直接跳过。
     *
     * 间隔给药（[Medication.intervalHours] > 0）：
     *   triggerMs = [lastTakenMs] ?: now + intervalHours * 3 600 000
     */
    fun scheduleAllReminders(medication: Medication, lastTakenMs: Long? = null) {
        reminderPlanner.nextOccurrences(
            schedule = medication.toDomainSchedule(),
            endAt = medication.endDate?.let(Instant::ofEpochMilli),
            zoneId = reminderZoneId,
            lastTakenAt = lastTakenMs?.let(Instant::ofEpochMilli),
        ).forEach { occurrence ->
            val triggerMs = occurrence.scheduledAt.toEpochMilli()
            scheduleAlarmSlot(medication, occurrence.slotIndex, triggerMs)
            scheduleEarlyReminderIfNeeded(medication, occurrence.slotIndex, triggerMs)
        }
    }

    /**
     * 调度指定时间槽的单个闹钟。
     * 供 [scheduleAllReminders] 内部调用，以及 [com.driezy.medlog.capability.reminders.MedLogAlarmReceiver]
     * 在每次触发后调度下一次时使用。
     */
    fun scheduleAlarmSlot(medication: Medication, timeIndex: Int, triggerAtMs: Long) {
        registerProjection(medication.id)
        val requestCode = (medication.id * 100 + timeIndex).toInt()
        val intent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, MedLogAlarmReceiver::class.java).apply {
                putExtra(EXTRA_MED_ID, medication.id)
                putExtra(EXTRA_MED_NAME, medication.name)
                putExtra(EXTRA_TIME_INDEX, timeIndex)
                putExtra(EXTRA_SCHEDULED_MS, triggerAtMs)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        scheduleExact(intent, triggerAtMs)
    }

    /**
     * 完成一个剂量后，只推进该时间槽的下一轮提醒。
     *
     * 固定时钟计划以本次计划时间为下界，避免用户提前服药后又收到同一剂提醒；
     * 间隔给药则从实际服药时间（若有）重新计算间隔。
     */
    fun scheduleNextReminderAfterDose(
        medication: Medication,
        timeIndex: Int,
        scheduledTimeMs: Long,
        actualTakenTimeMs: Long? = null,
    ) {
        val afterMs = actualTakenTimeMs ?: maxOf(scheduledTimeMs, clock.millis())
        val triggerMs = reminderPlanner.nextOccurrenceForSlot(
            schedule = medication.toDomainSchedule(),
            slotIndex = timeIndex,
            after = Instant.ofEpochMilli(afterMs),
            endAt = medication.endDate?.let(Instant::ofEpochMilli),
            zoneId = reminderZoneId,
        )?.scheduledAt?.toEpochMilli() ?: return
        scheduleAlarmSlot(medication, timeIndex, triggerMs)
        scheduleEarlyReminderIfNeeded(medication, timeIndex, triggerMs)
    }

    /** 撤销一个剂量后，只恢复该时间槽当前仍可触发的下一次提醒。 */
    fun restoreReminderForDose(medication: Medication, timeIndex: Int) {
        val triggerMs = reminderPlanner.nextOccurrenceForSlot(
            schedule = medication.toDomainSchedule(),
            slotIndex = timeIndex,
            after = clock.instant(),
            endAt = medication.endDate?.let(Instant::ofEpochMilli),
            zoneId = reminderZoneId,
        )?.scheduledAt?.toEpochMilli() ?: return
        scheduleAlarmSlot(medication, timeIndex, triggerMs)
        scheduleEarlyReminderIfNeeded(medication, timeIndex, triggerMs)
    }

    // ─── 取消 ──────────────────────────────────────────────────────────────

    /** 取消指定药品的单个时间槽，以及该槽的提前预告和漏服再提醒。 */
    fun cancelAlarmSlot(medicationId: Long, timeIndex: Int) {
        cancelPendingAlarm((medicationId * 100 + timeIndex).toInt())
        cancelPendingAlarm(
            (medicationId * 100 + timeIndex).toInt() + EARLY_REMINDER_CODE_OFFSET,
        )
        cancelPendingAlarm(
            (medicationId * 100 + timeIndex).toInt() + FOLLOW_UP_CODE_OFFSET,
        )
    }

    /** 只取消指定时间槽的漏服再提醒闹钟。 */
    fun cancelFollowUpAlarm(medicationId: Long, timeIndex: Int) {
        cancelPendingAlarm(
            (medicationId * 100 + timeIndex).toInt() + FOLLOW_UP_CODE_OFFSET,
        )
    }

    /**
     * 取消某药品的所有时间槽闹钟（不影响通知 UI）。
     * 通知的取消由 [NotificationHelper.cancelAllReminderNotifications] 负责。
     */
    fun cancelAllAlarms(medicationId: Long) {
        for (i in 0 until MAX_REMINDER_SLOTS) {
            val requestCode = (medicationId * 100 + i).toInt()
            val intent = PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, MedLogAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: continue
            alarmManager.cancel(intent)
            intent.cancel()
        }
        // 一并取消所有提前预告闹钟
        cancelEarlyReminderAlarms(medicationId)
        // 一并取消漏服再提醒闹钟
        cancelFollowUpAlarms(medicationId)
        unregisterProjection(medicationId)
    }

    /** Clears alarms for IDs absent from current Room truth after delete, import, or restore. */
    fun cancelAllKnownAlarms(): Set<Long> {
        val ids = projectionRegistry.getStringSet(REGISTERED_MEDICATION_IDS, emptySet())
            .orEmpty()
            .mapNotNull(String::toLongOrNull)
            .toSet()
        ids.forEach(::cancelAllAlarms)
        return ids
    }

    /**
     * 取消某药品的所有提前预告闹钟。
     * 内部用于 [cancelAllAlarms]，也可独立调用。
     */
    fun cancelEarlyReminderAlarms(medicationId: Long) {
        for (i in 0 until MAX_REMINDER_SLOTS) {
            val requestCode = (medicationId * 100 + i).toInt() + EARLY_REMINDER_CODE_OFFSET
            val intent = PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, MedLogAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: continue
            alarmManager.cancel(intent)
            intent.cancel()
        }
    }

    /**
     * 取消某药品的所有漏服再提醒闹钟。
     */
    fun cancelFollowUpAlarms(medicationId: Long) {
        for (i in 0 until MAX_REMINDER_SLOTS) {
            val requestCode = (medicationId * 100 + i).toInt() + FOLLOW_UP_CODE_OFFSET
            val intent = PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, MedLogAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: continue
            alarmManager.cancel(intent)
            intent.cancel()
        }
    }

    private fun cancelPendingAlarm(requestCode: Int) {
        val intent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, MedLogAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(intent)
        intent.cancel()
    }

    @Synchronized
    private fun registerProjection(medicationId: Long) {
        val ids = projectionRegistry.getStringSet(REGISTERED_MEDICATION_IDS, emptySet()).orEmpty().toMutableSet()
        if (ids.add(medicationId.toString())) {
            projectionRegistry.edit().putStringSet(REGISTERED_MEDICATION_IDS, ids).apply()
        }
    }

    @Synchronized
    private fun unregisterProjection(medicationId: Long) {
        val ids = projectionRegistry.getStringSet(REGISTERED_MEDICATION_IDS, emptySet()).orEmpty().toMutableSet()
        if (ids.remove(medicationId.toString())) {
            projectionRegistry.edit().putStringSet(REGISTERED_MEDICATION_IDS, ids).apply()
        }
    }

    /**
     * 调度漏服再提醒闹钟。
     *
     * @param medication 药品对象
     * @param timeIndex  提醒时间槽索引
     * @param scheduledMs 原始闹钟触发时间（用于对照日志查询）
     * @param followUpCount 当前是第几次再提醒（从 1 开始）
     * @param followUpMaxCount 最大再提醒次数
     * @param delayMs 再提醒间隔毫秒时
     * @param triggerAtMs 本次闹钟触发时间
     */
    fun scheduleFollowUpAlarm(
        medication: Medication,
        timeIndex: Int,
        scheduledMs: Long,
        followUpCount: Int,
        followUpMaxCount: Int,
        delayMs: Long,
        triggerAtMs: Long,
    ) {
        registerProjection(medication.id)
        val requestCode = (medication.id * 100 + timeIndex).toInt() + FOLLOW_UP_CODE_OFFSET
        val intent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, MedLogAlarmReceiver::class.java).apply {
                putExtra(EXTRA_MED_ID, medication.id)
                putExtra(EXTRA_MED_NAME, medication.name)
                putExtra(EXTRA_TIME_INDEX, timeIndex)
                putExtra(EXTRA_IS_FOLLOW_UP, true)
                putExtra(EXTRA_FOLLOW_UP_COUNT, followUpCount)
                putExtra(EXTRA_FOLLOW_UP_MAX_COUNT, followUpMaxCount)
                putExtra(EXTRA_FOLLOW_UP_DELAY_MS, delayMs)
                putExtra(EXTRA_SCHEDULED_MS, scheduledMs)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        scheduleExact(intent, triggerAtMs)
    }

    // ─── 私有辅助 ─────────────────────────────────────────────────────────

    /**
     * 若用户开启了「提前 N 分钟预告」，则为指定时间槽调度一个提前预告闹钟。
     * 如果 [mainTriggerMs] 减去偏移后已过去或不足 1 分钟，则跳过。
     */
    private fun scheduleEarlyReminderIfNeeded(medication: Medication, timeIndex: Int, mainTriggerMs: Long) {
        val mins = earlyReminderMinutes
        if (mins <= 0) return
        val earlyTriggerMs = mainTriggerMs - mins * 60_000L
        if (earlyTriggerMs <= clock.millis() + 60_000L) return // 时机已过
        val requestCode = (medication.id * 100 + timeIndex).toInt() + EARLY_REMINDER_CODE_OFFSET
        val intent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, MedLogAlarmReceiver::class.java).apply {
                putExtra(EXTRA_MED_ID, medication.id)
                putExtra(EXTRA_MED_NAME, medication.name)
                putExtra(EXTRA_TIME_INDEX, timeIndex)
                putExtra(EXTRA_IS_EARLY, true)
                putExtra("early_minutes", mins)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        scheduleExact(intent, earlyTriggerMs)
    }

    /** 调度一次性精确闹钟（compat：Android 12+需要精确闹钟权限）*/
    private fun scheduleExact(intent: PendingIntent, triggerAtMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            alarmManager.canScheduleExactAlarms()
        ) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, intent)
        }
    }
}
