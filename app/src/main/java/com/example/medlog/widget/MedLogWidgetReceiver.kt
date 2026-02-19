package com.example.medlog.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 桌面小组件的 BroadcastReceiver 入口。
 * 系统会调用此 Receiver 更新 [MedLogWidget] 内容。
 */
class MedLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MedLogWidget()
}
