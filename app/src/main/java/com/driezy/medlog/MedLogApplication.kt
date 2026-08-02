package com.driezy.medlog

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.driezy.medlog.capability.widgets.WidgetRefreshWorker
import com.driezy.medlog.domain.ReminderReconcileReason
import com.driezy.medlog.domain.ReminderReconciliationQueue
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MedLogApplication :
    Application(),
    Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminderReconciliationQueue: ReminderReconciliationQueue

    override fun onCreate() {
        super.onCreate()
        // Projections are disposable. Application startup is the final recovery net after
        // restore, upgrade, permission changes, or an earlier WorkManager enqueue failure.
        runCatching {
            reminderReconciliationQueue.enqueue(ReminderReconcileReason.SYSTEM_EVENT)
        }
        // 注册 15 分钟周期刷新：确保小组件数据始终与 Room 同步
        WidgetRefreshWorker.schedulePeriodic(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
