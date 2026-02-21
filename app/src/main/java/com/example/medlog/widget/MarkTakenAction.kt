package com.example.medlog.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.example.medlog.data.local.MedLogDatabase
import com.example.medlog.data.model.LogStatus
import com.example.medlog.data.model.MedicationLog
import java.util.Calendar

/**
 * 小组件内"直接打卡"回调。
 *
 * 点击某药品旁的 ✓ 按钮时触发：
 * 1. 查找今日该药品的日志记录
 * 2. 若不存在或非 TAKEN → 写入 / 更新为 TAKEN
 * 3. 刷新所有 MedLogWidget 实例
 */
class MarkTakenAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val medId = parameters[medIdKey] ?: return

        val db      = MedLogDatabase.getInstance(context)
        val dao     = db.medicationLogDao()
        val start   = todayStart()
        val end     = start + 24 * 60 * 60 * 1000L - 1L
        val existing = dao.getLogForMedicationAndDate(medId, start, end)

        val now = System.currentTimeMillis()
        if (existing == null) {
            dao.insertLog(
                MedicationLog(
                    medicationId      = medId,
                    scheduledTimeMs   = start,
                    actualTakenTimeMs = now,
                    status            = LogStatus.TAKEN,
                ),
            )
        } else if (existing.status != LogStatus.TAKEN) {
            dao.updateLog(existing.copy(status = LogStatus.TAKEN, actualTakenTimeMs = now))
        }

        // 刷新所有小组件实例
        MedLogWidget().updateAll(context)
    }

    companion object {
        val medIdKey: ActionParameters.Key<Long> =
            ActionParameters.Key("med_id")
    }
}

/** 今日 00:00:00 的时间戳 */
private fun todayStart(): Long =
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
