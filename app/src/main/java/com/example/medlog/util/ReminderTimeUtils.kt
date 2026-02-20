package com.example.medlog.util

import com.example.medlog.data.model.TimePeriod
import com.example.medlog.data.repository.SettingsPreferences

/**
 * 纯函数工具：将服药时段 + 用户作息偏好 转换为 HH:mm 提醒时间字符串。
 *
 * 提取自 AddMedicationViewModel，便于在 ResyncRemindersUseCase、
 * BootReceiver 等场景中复用，无需依赖 ViewModel 实例。
 */
object ReminderTimeUtils {

    /**
     * 根据 [period] 和用户当前的 [prefs] 计算对应的 HH:mm 字符串。
     *
     * 注意：[TimePeriod.EXACT] 需由调用方自行维护，此处返回空字符串占位。
     */
    fun timePeriodToReminderTime(period: TimePeriod, prefs: SettingsPreferences): String =
        when (period) {
            TimePeriod.EXACT            -> ""   // Exact 时间由用户手动指定，调用方保持原值
            TimePeriod.MORNING          -> "%02d:%02d".format(prefs.wakeHour,      prefs.wakeMinute)
            TimePeriod.BEFORE_BREAKFAST -> adjustTime(prefs.breakfastHour, prefs.breakfastMinute, -15)
            TimePeriod.AFTER_BREAKFAST  -> adjustTime(prefs.breakfastHour, prefs.breakfastMinute, +15)
            TimePeriod.BEFORE_LUNCH     -> adjustTime(prefs.lunchHour,     prefs.lunchMinute,     -15)
            TimePeriod.AFTER_LUNCH      -> adjustTime(prefs.lunchHour,     prefs.lunchMinute,     +15)
            TimePeriod.BEFORE_DINNER    -> adjustTime(prefs.dinnerHour,    prefs.dinnerMinute,    -15)
            TimePeriod.AFTER_DINNER     -> adjustTime(prefs.dinnerHour,    prefs.dinnerMinute,    +15)
            TimePeriod.EVENING          -> adjustTime(prefs.bedHour,       prefs.bedMinute,       -60)
            TimePeriod.BEDTIME          -> "%02d:%02d".format(prefs.bedHour, prefs.bedMinute)
            TimePeriod.AFTERNOON        -> "15:00"
        }

    /** 按分钟偏移时间，正确处理小时进退位，返回 HH:mm */
    fun adjustTime(hour: Int, minute: Int, deltaMinutes: Int): String {
        val total = hour * 60 + minute + deltaMinutes
        val h = ((total / 60) % 24 + 24) % 24
        val m = ((total % 60) + 60) % 60
        return "%02d:%02d".format(h, m)
    }
}
