package com.example.medlog.domain

import android.content.Context
import com.example.medlog.data.repository.MedicationRepository
import com.example.medlog.notification.AlarmScheduler
import com.example.medlog.widget.WidgetRefreshWorker
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val alarmScheduler: AlarmScheduler,
    private val toggleDoseUseCase: ToggleMedicationDoseUseCase,
    @param:ApplicationContext private val context: Context,
) {
    suspend operator fun invoke(plan: PlanExport, mode: ImportMode) {
        val newMeds = plan.meds.map { with(PlanExportCodec) { it.toMedication() } }

        if (mode == ImportMode.REPLACE) {
            // 取消旧药品的所有提醒并删除记录
            medicationRepo.getActiveOnce().forEach { med ->
                toggleDoseUseCase.cancelAllReminders(med.id)
                medicationRepo.deleteMedication(med)
            }
        }

        // 合并模式：跳过同名（不区分大小写）的药品
        val existingNames: Set<String> = if (mode == ImportMode.MERGE) {
            medicationRepo.getActiveOnce().map { it.name.trim().lowercase() }.toSet()
        } else {
            emptySet()
        }

        newMeds.forEach { med ->
            if (mode == ImportMode.MERGE && med.name.trim().lowercase() in existingNames) return@forEach

            // id = 0 → Room 自动生成新主键，不使用导出时的 id
            val newId = medicationRepo.addMedication(med.copy(id = 0))
            medicationRepo.getMedicationById(newId)?.let { saved ->
                alarmScheduler.scheduleAllReminders(saved)
            }
        }

        WidgetRefreshWorker.scheduleImmediateRefresh(context)
    }
}
