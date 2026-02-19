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
import javax.inject.Inject
import javax.inject.Singleton

const val CHANNEL_REMINDER = "med_reminder"
const val CHANNEL_LOW_STOCK = "low_stock"
const val EXTRA_MED_ID = "med_id"
const val EXTRA_MED_NAME = "med_name"

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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

    fun showReminderNotification(medicationId: Long, medicationName: String, dose: String) {
        val intent = Intent(context, MedLogAlarmReceiver::class.java).apply {
            putExtra(EXTRA_MED_ID, medicationId)
            putExtra(EXTRA_MED_NAME, medicationName)
        }
        val takenPendingIntent = PendingIntent.getBroadcast(
            context,
            (medicationId * 10 + 1).toInt(),
            intent.apply { action = "ACTION_TAKEN" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val skipPendingIntent = PendingIntent.getBroadcast(
            context,
            (medicationId * 10 + 2).toInt(),
            intent.apply { action = "ACTION_SKIP" },
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

        notificationManager.notify(medicationId.toInt(), notification)
    }

    fun cancelReminder(medicationId: Long) {
        notificationManager.cancel(medicationId.toInt())
    }

    fun scheduleAlarm(medication: Medication, triggerAtMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = PendingIntent.getBroadcast(
            context,
            medication.id.toInt(),
            Intent(context, MedLogAlarmReceiver::class.java).apply {
                putExtra(EXTRA_MED_ID, medication.id)
                putExtra(EXTRA_MED_NAME, medication.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            alarmManager.canScheduleExactAlarms()
        ) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, intent)
        }
    }

    fun cancelAlarm(medicationId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = PendingIntent.getBroadcast(
            context,
            medicationId.toInt(),
            Intent(context, MedLogAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(intent)
    }
}
