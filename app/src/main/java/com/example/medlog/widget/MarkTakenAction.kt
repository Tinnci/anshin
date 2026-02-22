package com.example.medlog.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.EntryPointAccessors

/**
 * 小组件内"直接打卡"回调。
 *
 * 点击某药品旁的 ✓ 按钮时触发：
 * 1. 通过 [WidgetEntryPoint] 获取 [com.example.medlog.domain.ToggleMedicationDoseUseCase]
 * 2. 调用 markTakenById：写入日志、扣库存、取消闹钟、取消通知（完整副作用，与主应用一致）
 * 3. 刷新所有 Widget 实例
 */
class MarkTakenAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val medId = parameters[medIdKey] ?: return

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        entryPoint.toggleMedicationDoseUseCase().markTakenById(medId)

        // 立即重新渲染所有小组件实例
        MedLogWidget().updateAll(context)
        NextDoseWidget().updateAll(context)
        StreakWidget().updateAll(context)
    }

    companion object {
        val medIdKey: ActionParameters.Key<Long> =
            ActionParameters.Key("med_id")
    }
}

