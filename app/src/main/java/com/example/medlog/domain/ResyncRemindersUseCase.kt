package com.example.medlog.domain

import com.example.medlog.data.model.TimePeriod
import com.example.medlog.data.repository.MedicationRepository
import com.example.medlog.data.repository.SettingsPreferences
import com.example.medlog.notification.NotificationHelper
import com.example.medlog.util.ReminderTimeUtils
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用例：当用户更改作息时间（起床、早/午/晚餐、就寝）后，
 * 自动为所有「非 EXACT、非 PRN」的活跃药品重新计算 reminderTimes，
 * 更新数据库并重新调度闹钟。
 *
 * 调用时机：
 *  - SettingsViewModel.updateRoutineTime() 保存成功后
 *
 * 不处理：
 *  - TimePeriod.EXACT — 提醒时间由用户手动指定，不自动覆盖
 *  - isPRN 药品     — 按需服用，不设置固定闹钟
 */
@Singleton
class ResyncRemindersUseCase @Inject constructor(
    private val medicationRepository: MedicationRepository,
    private val notificationHelper: NotificationHelper,
) {
    /**
     * 传入最新的 [prefs]，对所有活跃（未归档）、非 PRN、非 EXACT 药品：
     * 1. 根据 timePeriod 重新计算 reminderTimes
     * 2. 更新数据库
     * 3. 取消旧闹钟 → 调度新闹钟
     */
    suspend operator fun invoke(prefs: SettingsPreferences) {
        val meds = medicationRepository.getActiveMedications().first()
        meds.forEach { med ->
            if (med.isPRN) return@forEach
            val period = TimePeriod.fromKey(med.timePeriod)
            if (period == TimePeriod.EXACT) return@forEach

            val newTime = ReminderTimeUtils.timePeriodToReminderTime(period, prefs)
            if (newTime.isBlank()) return@forEach          // 防御性检查
            if (newTime == med.reminderTimes) return@forEach // 没有变化，跳过

            val updatedMed = med.copy(
                reminderTimes = newTime,
                reminderHour  = newTime.substringBefore(":").toIntOrNull() ?: med.reminderHour,
                reminderMinute = newTime.substringAfter(":").toIntOrNull() ?: med.reminderMinute,
            )
            medicationRepository.updateMedication(updatedMed)
            notificationHelper.cancelAllReminders(med.id)
            notificationHelper.scheduleAllReminders(updatedMed)
        }
    }
}
