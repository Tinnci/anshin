package com.example.medlog.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.medlog.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

const val CHANNEL_REMINDER       = "med_reminder"
const val CHANNEL_LOW_STOCK      = "low_stock"
const val CHANNEL_PROGRESS       = "med_progress"    // 持久性今日进度通知
const val CHANNEL_EARLY_REMINDER = "early_reminder"  // 提前预告提醒
private const val NOTIF_ID_PROGRESS = 9999
const val EXTRA_MED_ID    = "med_id"
const val EXTRA_MED_NAME  = "med_name"
const val EXTRA_TIME_INDEX = "time_index"  // 提醒时间在列表中的索引
const val EXTRA_IS_EARLY  = "is_early"    // 是否为提前预告通知

/** 提醒通知分组键 */
private const val GROUP_REMINDERS = "com.example.medlog.REMINDERS"
/** 打开主界面的 PendingIntent requestCode */
private const val REQUEST_OPEN_APP = 10001

/** 最大支持的提醒时间数量，也用于 cancel 所有时间槽 */
private const val MAX_REMINDER_SLOTS = 20

/**
 * 通知显示器。
 *
 * **单一职责**：只负责创建、显示、取消通知消息。
 * 闹钟调度逻辑全部迁移到 [AlarmScheduler]。
 */
@Singleton
class NotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** 品牌蓝（用于通知小图标著色） */
    private val brandColor: Int by lazy {
        ContextCompat.getColor(context, R.color.ic_launcher_background)
    }

    /** 打开主界面的 PendingIntent */
    private val openAppPendingIntent: PendingIntent
        get() = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    init {
        createChannels()
    }

    private fun createChannels() {
        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER,
            context.getString(R.string.reminder_notification_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_ch_reminder_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 100, 250)   // 双击模式
            enableLights(true)
            lightColor = ContextCompat.getColor(context, R.color.ic_launcher_background)
        }
        val stockChannel = NotificationChannel(
            CHANNEL_LOW_STOCK,
            context.getString(R.string.low_stock_notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_ch_stock_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 150, 300)   // 库存警告震动
        }
        val progressChannel = NotificationChannel(
            CHANNEL_PROGRESS,
            context.getString(R.string.notif_ch_progress_name),
            NotificationManager.IMPORTANCE_LOW,    // 不打断用户操作
        ).apply {
            description = context.getString(R.string.notif_ch_progress_desc)
            setShowBadge(false)
        }
        val earlyChannel = NotificationChannel(
            CHANNEL_EARLY_REMINDER,
            context.getString(R.string.notif_ch_early_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_ch_early_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300)             // 单次轻震
        }
        notificationManager.createNotificationChannels(listOf(reminderChannel, stockChannel, progressChannel, earlyChannel))
    }
    // ─── 今日进度持久性通知（Live Activity 风格）─────────────────────────────

    /**
     * 显示/更新今日用药整体进度通知。
     * - taken == total 时自动取消固定状态，用户可手动关闭。
     * - total == 0 时移除通知。
     */
    fun showOrUpdateProgressNotification(
        taken: Int,
        total: Int,
        pendingNames: List<String>,
    ) {
        if (!notificationManager.areNotificationsEnabled()) return
        if (total == 0) { dismissProgressNotification(); return }

        val allDone = taken == total
        val percent = (taken * 100) / total
        val title = if (allDone)
            context.getString(R.string.notif_progress_done_title)
        else
            context.getString(R.string.notif_progress_title, taken, total)
        val bigText = when {
            allDone -> context.getString(R.string.notif_progress_done_body)
            pendingNames.isNotEmpty() -> pendingNames.joinToString("、")
            else -> ""
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(brandColor)
            .setContentTitle(title)
            .setSubText(context.getString(R.string.notif_progress_today))
            .apply {
                if (bigText.isNotEmpty()) setContentText(bigText)
                if (!allDone) setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.notif_progress_pending_prefix, bigText))
                        .setSummaryText(context.getString(R.string.notif_progress_percent_summary, percent))
                )
            }
            .setProgress(total, taken, false)
            .setContentIntent(openAppPendingIntent)
            .setOnlyAlertOnce(true)         // 更新进度时不再发出声音
            .setOngoing(!allDone)           // 未完成时固定在通知栏
            .setAutoCancel(allDone)
            .setLocalOnly(true)             // 进度通知仅显示在手机，不同步到可穿戴设备
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setTicker(if (allDone) context.getString(R.string.notif_progress_ticker_done) else context.getString(R.string.notif_progress_ticker_update))
            .build()

        notificationManager.notify(NOTIF_ID_PROGRESS, notification)
    }

    /** 删除今日进度通知 */
    fun dismissProgressNotification() {
        notificationManager.cancel(NOTIF_ID_PROGRESS)
    }
    // ─── 通知显示 ────────────────────────────────────────────

    fun showReminderNotification(
        medicationId: Long,
        medicationName: String,
        dose: String,
        timeIndex: Int = 0,
    ) {
        if (!notificationManager.areNotificationsEnabled()) return
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

        // 锁屏公开版本：隐藏药品名称保护隐私
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(brandColor)
            .setContentTitle(context.getString(R.string.notif_reminder_public_title))
            .setContentText(context.getString(R.string.notif_reminder_public_body))
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(brandColor)
            .setContentTitle(context.getString(R.string.notif_reminder_title, medicationName))
            .setContentText(context.getString(R.string.notif_reminder_dose_label, dose))
            .setSubText(context.getString(R.string.notif_reminder_subtext))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(context.getString(R.string.notif_reminder_big_text, dose, medicationName))
            )
            .addAction(0, context.getString(R.string.medication_taken), takenPendingIntent)
            .addAction(0, context.getString(R.string.notif_action_skip), skipPendingIntent)
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setGroup(GROUP_REMINDERS)
            .setAutoCancel(true)
            .setTimeoutAfter(2 * 60 * 60 * 1000L)   // 2小时后自动清除
            .setTicker(context.getString(R.string.notif_reminder_ticker, medicationName))
            .build()

        notificationManager.notify(notificationId, notification)
    }

    // ─── 取消通知 ─────────────────────────────────────────────

    /** 取消指定药品某时间槽的通知（不影响闹钟，闹钟由 [AlarmScheduler] 管理）*/
    fun cancelReminderNotification(medicationId: Long, timeIndex: Int) {
        val notificationId = (medicationId * 100 + timeIndex).toInt()
        notificationManager.cancel(notificationId)
    }

    /**
     * 取消某药品的所有时间槽通知 UI。
     * 不负责取消闹钟 —— 使用 [AlarmScheduler.cancelAllAlarms]。
     */
    fun cancelAllReminderNotifications(medicationId: Long) {
        for (i in 0 until MAX_REMINDER_SLOTS) {
            notificationManager.cancel((medicationId * 100 + i).toInt())
        }
    }

    // ─── 低库存通知 ──────────────────────────────────────────

    fun showLowStockNotification(medicationId: Long, medicationName: String, stock: Double, unit: String) {
        if (!notificationManager.areNotificationsEnabled()) return
        val openAction = NotificationCompat.Action(
            0,
            context.getString(R.string.notif_action_view_detail),
            openAppPendingIntent,
        )
        val stockStr = stock.toBigDecimal().stripTrailingZeros().toPlainString()
        val notification = NotificationCompat.Builder(context, CHANNEL_LOW_STOCK)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(brandColor)
            .setContentTitle(context.getString(R.string.notif_stock_title, medicationName))
            .setContentText(context.getString(R.string.notif_stock_body, stockStr, unit))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(context.getString(R.string.notif_stock_big_text, medicationName, stockStr, unit))
            )
            .addAction(openAction)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTicker(context.getString(R.string.notif_stock_ticker, medicationName))
            .build()
        notificationManager.notify((medicationId + 10000L).toInt(), notification)
    }

    /**
     * 发送"预计 N 天后用完，建议提前备货"提醒通知。
     * notifId 使用 medicationId + 20000 偏移，避免与低库存通知冲突。
     */
    fun showRefillReminderNotification(medicationId: Long, medicationName: String, daysRemaining: Int) {
        if (!notificationManager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_LOW_STOCK)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(brandColor)
            .setContentTitle(context.getString(R.string.notif_refill_title, medicationName))
            .setContentText(context.resources.getQuantityString(R.plurals.notif_refill_body, daysRemaining, daysRemaining))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.resources.getQuantityString(R.plurals.notif_refill_big_text, daysRemaining, medicationName, daysRemaining)),
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notificationManager.notify((medicationId + 20000L).toInt(), notification)
    }

    // ─── 提前预告通知 ───────────────────────────────────────────────

    /**
     * 发送"X 分钟后记得服用"预告通知。
     * @param minutesBefore 距正式服药时间的分钟数
     */
    fun showEarlyReminderNotification(
        medicationId: Long,
        medicationName: String,
        dose: String,
        minutesBefore: Int,
        timeIndex: Int = 0,
    ) {
        if (!notificationManager.areNotificationsEnabled()) return
        val notificationId = (medicationId * 100 + timeIndex).toInt() + 50_000
        val notification = NotificationCompat.Builder(context, CHANNEL_EARLY_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(brandColor)
            .setContentTitle(context.getString(R.string.notif_early_title, minutesBefore))
            .setContentText(context.getString(R.string.notif_early_body, medicationName, dose))
            .setSubText(context.getString(R.string.notif_early_subtext))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(context.resources.getQuantityString(R.plurals.notif_early_big_text, minutesBefore, minutesBefore, dose, medicationName))
            )
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(minutesBefore * 60_000L + 5 * 60_000L)  // 超时 = 预告时间 + 5 分钟
            .setTicker(context.getString(R.string.notif_early_ticker, minutesBefore))
            .build()
        notificationManager.notify(notificationId, notification)
    }

    /** 取消指定药品某时间槽的提前通知 */
    fun cancelEarlyReminderNotification(medicationId: Long, timeIndex: Int) {
        notificationManager.cancel((medicationId * 100 + timeIndex).toInt() + 50_000)
    }
}
