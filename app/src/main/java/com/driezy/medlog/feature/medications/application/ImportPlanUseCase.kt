package com.driezy.medlog.feature.medications.application

import com.driezy.medlog.capability.reminders.application.ReconcileRemindersUseCase
import com.driezy.medlog.data.repository.MedicationRepository
import com.driezy.medlog.domain.ReminderReconcileReason
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用例：将 [PlanExport] 导入数据库（合并或替换）并重新调度提醒。
 *
 * - [ImportMode.MERGE]   保留现有药品，仅新增名称（不区分大小写）不重复的条目。
 * - [ImportMode.REPLACE] 先取消并删除所有活跃药品，再全量导入。
 *
 * 符合 SRP：导入逻辑与 UI 状态管理完全分离。
 */
@Singleton
class ImportPlanUseCase @Inject constructor(
    private val medicationRepo: MedicationRepository,
    private val reconcileReminders: ReconcileRemindersUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(plan: PlanExport, mode: ImportMode) {
        val newMeds = plan.meds.map {
            with(PlanExportCodec) {
                it.toMedication(defaultStart = clock.instant(), zoneId = clock.zone)
            }
        }

        when (mode) {
            ImportMode.MERGE -> medicationRepo.mergeMedicationsByName(newMeds)
            ImportMode.REPLACE -> medicationRepo.replaceActiveMedications(newMeds)
        }

        reconcileReminders.all(ReminderReconcileReason.MEDICATION_CHANGED)
    }
}
