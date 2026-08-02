package com.driezy.medlog.util

import com.driezy.medlog.data.model.RoutineSchedule
import com.driezy.medlog.data.model.TimePeriod
import com.driezy.medlog.data.repository.SettingsPreferences
import com.driezy.medlog.data.repository.routineSchedule

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
        timePeriodToReminderTime(period, prefs.routineSchedule())

    fun timePeriodToReminderTime(period: TimePeriod, schedule: RoutineSchedule): String = when (period) {
        TimePeriod.EXACT -> "" // Exact 时间由用户手动指定，调用方保持原值
        TimePeriod.MORNING -> schedule.wake.format()
        TimePeriod.BEFORE_BREAKFAST -> adjustTime(schedule.breakfast.hour, schedule.breakfast.minute, -15)
        TimePeriod.AFTER_BREAKFAST -> adjustTime(schedule.breakfast.hour, schedule.breakfast.minute, +15)
        TimePeriod.BEFORE_LUNCH -> adjustTime(schedule.lunch.hour, schedule.lunch.minute, -15)
        TimePeriod.AFTER_LUNCH -> adjustTime(schedule.lunch.hour, schedule.lunch.minute, +15)
        TimePeriod.BEFORE_DINNER -> adjustTime(schedule.dinner.hour, schedule.dinner.minute, -15)
        TimePeriod.AFTER_DINNER -> adjustTime(schedule.dinner.hour, schedule.dinner.minute, +15)
        TimePeriod.EVENING -> adjustTime(schedule.bed.hour, schedule.bed.minute, -60)
        TimePeriod.BEDTIME -> schedule.bed.format()
        TimePeriod.AFTERNOON -> "15:00"
    }

    /** 按分钟偏移时间，正确处理小时进退位，返回 HH:mm */
    fun adjustTime(hour: Int, minute: Int, deltaMinutes: Int): String {
        val total = hour * 60 + minute + deltaMinutes
        val h = ((total / 60) % 24 + 24) % 24
        val m = ((total % 60) + 60) % 60
        return "%02d:%02d".format(h, m)
    }
}

private fun com.driezy.medlog.data.model.RoutineTime.format(): String = "%02d:%02d".format(hour, minute)
