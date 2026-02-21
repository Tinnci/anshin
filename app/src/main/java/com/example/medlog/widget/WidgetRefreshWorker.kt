package com.example.medlog.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 定期刷新所有 Glance 桌面小组件的 WorkManager Worker。
 *
 * 每 15 分钟（WorkManager 最小周期）运行一次，确保：
 * - 「今日进度」小组件数据与 Room 保持同步
 * - 「下次服药」倒计时随时间推移自动更新
 *
 * 除定期刷新外，应用内数据变更时会通过 [scheduleImmediateRefresh] 立即触发一次性刷新。
 */
class WidgetRefreshWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        MedLogWidget().updateAll(context)
        NextDoseWidget().updateAll(context)
        StreakWidget().updateAll(context)
        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "widget_periodic_refresh"
        private const val ONE_TIME_WORK_NAME  = "widget_immediate_refresh"

        /**
         * 注册 15 分钟周期刷新任务（如已存在则保留，不重复注册）。
         * 通常在 [MedLogApplication.onCreate] 中调用一次。
         */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                15, TimeUnit.MINUTES,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * 触发一次立即刷新（应用内数据变更后调用）。
         * WorkManager 会在后台线程上尽快执行（通常 <1s 延迟）。
         */
        fun scheduleImmediateRefresh(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .addTag("immediate")
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    ONE_TIME_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
        }
    }
}
