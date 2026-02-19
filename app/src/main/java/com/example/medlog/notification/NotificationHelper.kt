package com.example.medlog.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.medlog.R
import com.example.medlog.data.model.Medication
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

const val CHANNEL_REMINDER = "med_reminder"
const val CHANNEL_LOW_STOCK = "low_stock"
const val EXTRA_MED_ID = "med_id"
const val EXTRA_MED_NAME = "med_name"
const val EXTRA_TIME_INDEX = "time_index"   // 提醒时间在列表中的索引

/** 最大支持的提醒时间数量，也用于 cancel 所有时间槽 */
private const val MAX_REMINDER_SLOTS = 20

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    init {
        createChannels()
    }

    private fun createChannels() {
        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER,
            context.getString(R.string.reminder_notification_channel),
            NotificationManager.IMPORTANCE_HIGH,
        )
        val stockChannel = NotificationChannel(
            CHANNEL_LOW_STOCK,
            context.getString(R.string.low_stock_notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        notificationManager.createNotificationChannels(listOf(reminderChannel, stockChannel))
    }

    // ─── 通知显示 ────────────────────────────────────────────

    fun showReminderNotification(
        medicationId: Long,
        medicationName: String,
        dose: String,
        timeIndex: Int = 0,
    ) {
        val baseIntent = Intent(context, MedLogAlarmReceiver::class.java).apply {
            putExtra(EXTRA_MED_ID, medicationId)
            putExtra(EXTRA_MED_NAME, medicationName)
            putExtra(EXTRA_TIME_INDEX, timeIndex)
        }
        val notificationId = (medicationId * 100 + timeIndex).toInt()
        val takenPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            baseIntent.apply { action = "ACTION_TAKEN" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val skipPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            baseIntent.apply { action = "ACTION_SKIP" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_pill_splash)
            .setContentTitle("该服药了：$medicationName")
            .setContentText("剂量：$dose")
            .addAction(0, "✅ 已服用", takenPendingIntent)
            .addAction(0, "⏭ 跳过", skipPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    // ─── 调度多时间段提醒 ─────────────────────────────────────

    /**
     * 根据药品的 reminderTimes、frequencyType、frequencyDays 为每个时间槽
     * 调度下一次提醒闹钟。
     */
    fun scheduleAllReminders(medication: Medication) {
        if (medication.isPRN) return  // 按需服用不设置闹钟
        val times = medication.reminderTimes.split(",").map { it.trim() }
        times.forEachIndexed { index, timeStr ->
            val triggerMs = computeNextTrigger(
                timeStr = timeStr,
                frequencyType = medication.frequencyType,
                frequencyInterval = medication.frequencyInterval,
                frequencyDays = medication.frequencyDays,
                endDateMs = medication.endDate,
            ) ?: return@forEachIndexed
            scheduleAlarmSlot(medication, index, triggerMs)
        }
    }

    /**
     * 调度单个时间槽的闹钟（供 AlarmReceiver 在触发后重新调度下一次使用）。
     */
    fun scheduleAlarmSlot(medication: Medication, timeIndex: Int, triggerAtMs: Long) {
        val requestCode = (medication.id * 100 + timeIndex).toInt()
        val intent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, MedLogAlarmReceiver::class.java).apply {
                putExtra(EXTRA_MED_ID, medication.id)
                putExtra(EXTRA_MED_NAME, medication.name)
                putExtra(EXTRA_TIME_INDEX, timeIndex)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        scheduleExact(intent, triggerAtMs)
    }

    /** compat：调度一次性精确闹钟 */
    private fun scheduleExact(intent: PendingIntent, triggerAtMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, intent)
        }
    }

    /** 计算指定时间槽的下一次触发时间（毫秒）；若已过期则返回 null */
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
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)

        // 根据频率类型确定触发日期
        val triggerMs: Long = when (frequencyType) {
            "specific_days" -> {
                // frequencyDays 格式: "1,3,5"  1=周一..7=周日
                val days = frequencyDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                    .map { if (it == 7) Calendar.SUNDAY else it + 1 }  // 转成 Calendar 常量
                // 从 cal 开始往后找最近的匹配 day
                var found = false
                for (offset in 0..7) {
                    val candidate = Calendar.getInstance().apply {
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
                // 每隔 N 天触发一次，直接取 cal（已加1天）
                // 注：精确的间隔调度依赖 lastTakenDate，这里简化为"明天起每N天"
                if (frequencyInterval > 1) {
                    cal.add(Calendar.DAY_OF_YEAR, frequencyInterval - 1)
                }
                cal.timeInMillis
            }
            else -> cal.timeInMillis  // "daily" - 直接用 cal
        }

        // 检查是否超过结束日期
        if (endDateMs != null && triggerMs > endDateMs) return null
        return triggerMs
    }

    // ─── 取消提醒 ─────────────────────────────────────────────

    /** 取消某药品的所有时间槽闹钟 */
    fun cancelAllReminders(medicationId: Long) {
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
        notificationManager.cancel(medicationId.toInt())
    }

    /** @deprecated 请使用 cancelAllReminders */
    fun cancelAlarm(medicationId: Long) = cancelAllReminders(medicationId)

    /** @deprecated 请使用 scheduleAllReminders；保留以兼容旧调用 */
    fun scheduleAlarm(medication: Medication, triggerAtMs: Long) {
        scheduleAlarmSlot(medication, 0, triggerAtMs)
    }

    // ─── 低库存通知 ──────────────────────────────────────────

    fun showLowStockNotification(medicationId: Long, medicationName: String, stock: Double, unit: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_LOW_STOCK)
            .setSmallIcon(R.drawable.ic_pill_splash)
            .setContentTitle("$medicationName 库存不足")
            .setContentText("当前库存：$stock $unit，请及时补充")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notificationManager.notify((medicationId + 10000L).toInt(), notification)
    }
}
