package com.example.medlog.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Widget 刷新抽象接口（SSOT）。
 *
 * 设计目标：
 * - 将"如何刷新 Widget"的决策封装在基础设施层，领域层只依赖此接口（满足 SRP / 依赖倒置）
 * - [GlanceWidgetRefresher] 实现直接调用 Glance updateAll()，延迟 <100ms
 * - [WidgetRefreshWorker] 保留用于 15 分钟定期后台刷新（WorkManager 批处理）
 */
interface WidgetRefresher {
    /**
     * 立即刷新所有已放置的桌面小组件。
     * 可在任意协程上下文中调用（suspend，内部在 IO dispatcher 上运行 Glance 更新）。
     */
    suspend fun refreshAll()
}

/**
 * [WidgetRefresher] 的 Glance 实现：直接调用 [androidx.glance.appwidget.GlanceAppWidget.updateAll]，
 * 绕过 WorkManager 调度延迟，适合用户交互后的即时响应。
 *
 * 三个小组件同步刷新：
 * - [MedLogWidget]   今日服药进度
 * - [NextDoseWidget] 下次服药时间
 * - [StreakWidget]   连续打卡天数
 */
@Singleton
class GlanceWidgetRefresher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : WidgetRefresher {
    override suspend fun refreshAll() {
        MedLogWidget().updateAll(context)
        NextDoseWidget().updateAll(context)
        StreakWidget().updateAll(context)
    }
}
