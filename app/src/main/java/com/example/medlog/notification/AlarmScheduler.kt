package com.example.medlog.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.medlog.data.model.Medication
import com.example.medlog.data.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_REMINDER_SLOTS = 20
/** PendingIntent requestCode 偏移：提前预告闹钟用，避免与正式提醒冲突 */
const val EARLY_REMINDER_CODE_OFFSET = 50_000

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
    @ApplicationContext private val context: Context,
    private val prefsRepository: UserPreferencesRepository,
) {
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** 旅行模式：家乡时区，由后台协程实时同步 */
    @Volatile private var homeTimeZone: TimeZone = TimeZone.getDefault()

    /** 提前预告提醒分钟数（0 = 关闭），由 DataStore 实时同步 */
    @Volatile private var earlyReminderMinutes: Int = 0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // 监听旅行模式 / 家乡时区变化
        scope.launch {
            prefsRepository.settingsFlow.collect { prefs ->
                homeTimeZone = if (prefs.travelMode && prefs.homeTimeZoneId.isNotBlank()) {
                    try { TimeZone.getTimeZone(prefs.homeTimeZoneId) }
                    catch (_: Exception) { TimeZone.getDefault() }
                } else {
                    TimeZone.getDefault()
                }
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
        if (medication.isPRN) return  // 按需服用不设置闹钟
        if (medication.intervalHours > 0) {
            val triggerMs = (lastTakenMs ?: System.currentTimeMillis()) +
                    medication.intervalHours * 3_600_000L
            scheduleAlarmSlot(medication, 0, triggerMs)
            scheduleEarlyReminderIfNeeded(medication, 0, triggerMs)
            return
        }
        val times = medication.reminderTimes.split(",").map { it.trim() }
        times.forEachIndexed { index, timeStr ->
            val triggerMs = computeNextTrigger(
                timeStr          = timeStr,
                frequencyType    = medication.frequencyType,
                frequencyInterval = medication.frequencyInterval,
                frequencyDays    = medication.frequencyDays,
                endDateMs        = medication.endDate,
            ) ?: return@forEachIndexed
            scheduleAlarmSlot(medication, index, triggerMs)
            scheduleEarlyReminderIfNeeded(medication, index, triggerMs)
        }
    }

    /**
     * 调度指定时间槽的单个闹钟。
     * 供 [scheduleAllReminders] 内部调用，以及 [com.example.medlog.notification.MedLogAlarmReceiver]
     * 在每次触发后调度下一次时使用。
     */
    fun scheduleAlarmSlot(medication: Medication, timeIndex: Int, triggerAtMs: Long) {
        val requestCode = (medication.id * 100 + timeIndex).toInt()
        val intent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, MedLogAlarmReceiver::class.java).apply {
                putExtra(EXTRA_MED_ID,    medication.id)
                putExtra(EXTRA_MED_NAME,  medication.name)
                putExtra(EXTRA_TIME_INDEX, timeIndex)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        scheduleExact(intent, triggerAtMs)
    }

    // ─── 取消 ──────────────────────────────────────────────────────────────

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

    // ─── 计算下次触发时间 ────────────────────────────────────────────────

    /**
     * 计算指定时间槽的下一次触发时间（毫秒戳）；若已超过结束日期则返回 null。
     *
     * 当旅行模式开启时，使用 [homeTimeZone] 计算，确保提醒始终对应家乡时钟。
     */
    fun computeNextTrigger(
        timeStr: String,
        frequencyType: String,
        frequencyInterval: Int,
        frequencyDays: String,
        endDateMs: Long?,
    ): Long? {
        val parts = timeStr.split(":").mapNotNull { it.trim().toIntOrNull() }
        if (parts.size < 2) return null
        val (hour, minute) = parts

        val now = System.currentTimeMillis()
        val tz  = homeTimeZone
        val cal = Calendar.getInstance(tz).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE,      minute)
            set(Calendar.SECOND,      0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)

        val triggerMs: Long = when (frequencyType) {
            "specific_days" -> {
                val days = frequencyDays.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .map { if (it == 7) Calendar.SUNDAY else it + 1 }
                var found = false
                for (offset in 0..7) {
                    val candidate = Calendar.getInstance(tz).apply {
                        timeInMillis = cal.timeInMillis
                        add(Calendar.DAY_OF_YEAR, offset)
                    }
                    if (candidate.get(Calendar.DAY_OF_WEEK) in days && candidate.timeInMillis > now) {
                        cal.timeInMillis = candidate.timeInMillis
                        found = true
                        break
                    }
                }
                if (!found) return null
                cal.timeInMillis
            }
            "interval" -> {
                if (frequencyInterval > 1) cal.add(Calendar.DAY_OF_YEAR, frequencyInterval - 1)
                cal.timeInMillis
            }
            else -> cal.timeInMillis  // "daily"
        }

        if (endDateMs != null && triggerMs > endDateMs) return null
        return triggerMs
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
        if (earlyTriggerMs <= System.currentTimeMillis() + 60_000L) return  // 时机已过
        val requestCode = (medication.id * 100 + timeIndex).toInt() + EARLY_REMINDER_CODE_OFFSET
        val intent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, MedLogAlarmReceiver::class.java).apply {
                putExtra(EXTRA_MED_ID,    medication.id)
                putExtra(EXTRA_MED_NAME,  medication.name)
                putExtra(EXTRA_TIME_INDEX, timeIndex)
                putExtra(EXTRA_IS_EARLY,  true)
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

    // ─── 废弃兼容层（保持二进制兼容；不在新代码中使用）─────────────────

    /** @deprecated 请使用 [cancelAllAlarms] */
    @Deprecated("Use cancelAllAlarms instead", ReplaceWith("cancelAllAlarms(medicationId)"))
    fun cancelAlarm(medicationId: Long) = cancelAllAlarms(medicationId)

    /** @deprecated 请使用 [scheduleAllReminders]；保留以兼容旧调用 */
    @Deprecated("Use scheduleAllReminders instead",
        ReplaceWith("scheduleAlarmSlot(medication, 0, triggerAtMs)"))
    fun scheduleAlarm(medication: Medication, triggerAtMs: Long) =
        scheduleAlarmSlot(medication, 0, triggerAtMs)
}
