package com.example.medlog.widget

/**
 * 测试专用 [WidgetRefresher] 假实现。
 * 记录 [refreshAll] 被调用的次数，不执行任何真实 Glance 更新。
 */
class FakeWidgetRefresher : WidgetRefresher {
    var refreshCallCount = 0
        private set

    override suspend fun refreshAll() {
        refreshCallCount++
    }

    /** 重置调用计数（setUp 时使用）。 */
    fun reset() {
        refreshCallCount = 0
    }
}
