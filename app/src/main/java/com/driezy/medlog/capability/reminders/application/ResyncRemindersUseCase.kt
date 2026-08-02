package com.driezy.medlog.capability.reminders.application

import com.driezy.medlog.data.model.RoutineSchedule
import com.driezy.medlog.data.model.resolve
import com.driezy.medlog.data.model.toDomainSchedule
import com.driezy.medlog.data.model.withResolvedRoutineTime
import com.driezy.medlog.data.repository.MedicationRepository
import com.driezy.medlog.domain.ReminderReconcileReason
import com.driezy.medlog.domain.model.MedicationSchedule
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用例：当用户更改作息时间（起床、早/午/晚餐、就寝）后，
 * 自动为所有作息锚点药品重新计算类型化计划，
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
    private val reconcileReminders: ReconcileRemindersUseCase,
) {
    /**
     * 传入最新的 [prefs]，对所有活跃（未归档）、非 PRN、非 EXACT 药品：
     * 1. 根据类型化作息锚点重新计算时间
     * 2. 更新数据库
     * 3. 取消旧闹钟 → 调度新闹钟
     */
    suspend operator fun invoke(schedule: RoutineSchedule) {
        val meds = medicationRepository.getActiveMedications().first()
        val updates = meds.mapNotNull { med ->
            val medicationSchedule = med.toDomainSchedule() as? MedicationSchedule.RoutineAnchored
                ?: return@mapNotNull null
            val newTime = schedule.resolve(medicationSchedule.anchor)
            if (newTime == medicationSchedule.resolvedTime) return@mapNotNull null
            med.withResolvedRoutineTime(newTime)
        }
        if (updates.isNotEmpty()) medicationRepository.updateMedications(updates)
        reconcileReminders.all(ReminderReconcileReason.ROUTINE_CHANGED)
    }
}
