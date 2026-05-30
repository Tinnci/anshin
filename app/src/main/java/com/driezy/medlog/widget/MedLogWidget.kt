package com.driezy.medlog.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.driezy.medlog.R
import com.driezy.medlog.data.local.settingsDataStore
import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.data.repository.UserPreferencesRepository
import com.driezy.medlog.domain.todayEnd
import com.driezy.medlog.domain.todayStart
import com.driezy.medlog.ui.MainActivity
import dagger.hilt.android.EntryPointAccessors
import java.util.Calendar
import kotlinx.coroutines.flow.first

/**
 * 今日用药进度桌面小组件（Jetpack Glance M3）
 *
 * 根据小米小部件规范设计，支持三种响应式尺寸：
 * - 紧凑 2×2 (≤160dp 宽)：大号进度数字 + 进度条，垂直居中
 * - 标准 4×2 (≤260dp 宽)：标题行 + 进度条 + 关键待服项（含打卡按钮）
 * - 宽屏 4×4 (高度≥160dp)：完整信息 + 多条待服项（含打卡按钮）
 *
 * 交互：点击待服药品旁的按钮触发 [MarkTakenAction]，写入 TAKEN 日志并刷新。
 */
class MedLogWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(80.dp, 80.dp),    // 紧凑：2×2
            DpSize(180.dp, 80.dp),   // 标准：4×2
            DpSize(180.dp, 160.dp),  // 宽屏：4×4
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep          = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val medications = ep.medicationRepository().getActiveOnce()
        val logs        = ep.logRepository().getLogsForRangeOnce(todayStart(), todayEnd())

        val takenIds    = logs.filter { it.status == LogStatus.TAKEN }.map { it.medicationId }.toSet()
        val total       = medications.size
        val taken       = medications.count { it.id in takenIds }
        val nowMinutes  = java.util.Calendar.getInstance().let {
            it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE)
        }

        // 待服药品：id + 名称 + 下次服药时间（分钟数，用于显示标签）
        val pending = medications.filter { it.id !in takenIds }.map { med ->
            val times = parseReminderTimes(med.reminderTimes).map { (h, m) -> h * 60 + m }
            val nextTime = times.filter { it > nowMinutes }.minOrNull()
                ?: times.minOrNull()
                ?: (med.reminderHour * 60 + med.reminderMinute)
            Triple(med.id, med.name, nextTime)
        }.sortedBy { it.third }  // 按时间升序

        // 读取小组件显示设置（SSOT：与主应用共享同一 DataStore）
        val widgetPrefs = runCatching { context.settingsDataStore.data.first() }
            .getOrElse { androidx.datastore.preferences.core.emptyPreferences() }
        val widgetShowActions = widgetPrefs[UserPreferencesRepository.WIDGET_SHOW_ACTIONS] ?: true
        val appearance = widgetPrefs.medLogWidgetAppearance()

        provideContent {
            MedLogGlanceTheme(appearance = appearance) {
                WidgetContent(
                    taken = taken,
                    total = total,
                    pendingMeds = pending,
                    showActions = widgetShowActions,
                    sizing = appearance.sizing,
                )
            }
        }
    }
}

@Composable
private fun WidgetContent(
    taken: Int,
    total: Int,
    pendingMeds: List<Triple<Long, String, Int>>,
    showActions: Boolean,
    sizing: WidgetSizing,
) {
    val size      = LocalSize.current
    val isCompact = size.width < 160.dp
    val isTall    = size.height >= 160.dp
    val maxShow   = when {
        isCompact -> 0
        isTall && showActions -> 3
        isTall -> 5
        else -> 1
    }
    val allDone   = total > 0 && taken == total

    // 2×2 操作模式时需要 Top 对齐（Spacer.defaultWeight 才能把按钮推到底）
    val compactActionMode = isCompact && showActions && !allDone && total > 0 && pendingMeds.isNotEmpty()

    WidgetContainer(
        prominent = allDone,
        modifier = GlanceModifier
            .clickable(actionStartActivity<MainActivity>()),
        sizing = sizing,
        verticalAlignment   = if (compactActionMode) Alignment.Vertical.Top else if (isCompact) Alignment.Vertical.CenterVertically else Alignment.Vertical.Top,
        horizontalAlignment = if (isCompact && !compactActionMode) Alignment.Horizontal.CenterHorizontally else Alignment.Horizontal.Start,
    ) {
        if (isCompact) {
            CompactContent(
                taken        = taken,
                total        = total,
                allDone      = allDone,
                showActions  = showActions,
                firstPending = pendingMeds.firstOrNull(),
                pendingCount = pendingMeds.size,
                sizing       = sizing,
            )
        } else {
            StandardContent(
                taken       = taken,
                total       = total,
                allDone     = allDone,
                pendingMeds = pendingMeds,
                maxShow     = maxShow,
                showActions = showActions,
                sizing      = sizing,
            )
        }
    }
}

// ─── 紧凑模式（2×2）────────────────────────────────────────────────────────
@Composable
private fun CompactContent(
    taken: Int,
    total: Int,
    allDone: Boolean,
    showActions: Boolean,
    firstPending: Triple<Long, String, Int>?,
    pendingCount: Int,
    sizing: WidgetSizing,
) {
    val ctx = LocalContext.current
    when {
        total == 0 -> {
            WidgetEmptyState(
                icon = R.drawable.ic_symbol_medication,
                title = ctx.getString(R.string.widget_no_plan),
                compact = true,
                sizing = sizing,
            )
        }
        allDone -> {
            WidgetIconBadge(icon = R.drawable.ic_symbol_check_circle, prominent = true, size = sizing.dp(44), iconSize = sizing.dp(26))
            Spacer(GlanceModifier.height(sizing.dp(6)))
            Text(
                ctx.getString(R.string.widget_all_done),
                style = TextStyle(
                    fontSize   = sizing.sp(11),
                    fontWeight = FontWeight.Medium,
                    color      = GlanceTheme.colors.onTertiaryContainer,
                ),
            )
        }
        showActions && firstPending != null -> {
            // 操作模式 2×2：显示下一个待服药品 + 全宽服药按钮
            val moreBadge = if (pendingCount > 1) "  +${pendingCount - 1}" else ""
            Text(
                "${firstPending.second}$moreBadge",
                style = TextStyle(
                    fontSize   = sizing.sp(12),
                    fontWeight = FontWeight.Medium,
                    color      = GlanceTheme.colors.onSurface,
                ),
            )
            Spacer(GlanceModifier.height(sizing.dp(2)))
            Text(
                "%02d:%02d".format(firstPending.third / 60, firstPending.third % 60),
                style = TextStyle(fontSize = sizing.sp(11), color = GlanceTheme.colors.onSurfaceVariant),
            )
            Spacer(GlanceModifier.height(sizing.dp(10)))
            WidgetActionButton(
                label = ctx.getString(R.string.widget_action_btn),
                action = actionRunCallback<MarkTakenAction>(
                    actionParametersOf(MarkTakenAction.medIdKey to firstPending.first),
                ),
                modifier = GlanceModifier.fillMaxWidth().height(sizing.dp(48)),
                sizing = sizing,
            )
        }
        else -> {
            // 状态模式 或 无待服药品：显示进度数字 + 进度条
            Text(
                "$taken/$total",
                style = TextStyle(
                    fontSize   = sizing.sp(24),
                    fontWeight = FontWeight.Bold,
                    color      = GlanceTheme.colors.onSurface,
                ),
            )
            Spacer(GlanceModifier.height(sizing.dp(2)))
            Text(ctx.getString(R.string.widget_taken_label), style = TextStyle(fontSize = sizing.sp(11), color = GlanceTheme.colors.onSurfaceVariant))
            Spacer(GlanceModifier.height(sizing.dp(8)))
            LinearProgressIndicator(
                progress        = taken.toFloat() / total,
                modifier        = GlanceModifier.fillMaxWidth().height(sizing.dp(6)),
                color           = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.outline,
            )
        }
    }
}

// ─── 标准 / 宽屏模式（4×2 & 4×4）────────────────────────────────────────────
@Composable
private fun StandardContent(
    taken: Int,
    total: Int,
    allDone: Boolean,
    pendingMeds: List<Triple<Long, String, Int>>,
    maxShow: Int,
    showActions: Boolean,
    sizing: WidgetSizing,
) {
    val ctx = LocalContext.current
    // 标题 + 核心数字（F 型阅读动线）
    WidgetHeader(
        icon = R.drawable.ic_symbol_medication,
        title = ctx.getString(R.string.widget_app_label),
        trailing = when {
            total == 0 -> "--"
            allDone -> ctx.getString(R.string.widget_goal_done)
            else -> "$taken / $total"
        },
        prominent = allDone,
        sizing = sizing,
    )

    Spacer(GlanceModifier.height(sizing.dp(7)))

    if (total > 0) {
        LinearProgressIndicator(
            progress        = taken.toFloat() / total,
            modifier        = GlanceModifier.fillMaxWidth().height(sizing.dp(7)),
            color           = if (allDone) GlanceTheme.colors.tertiary else GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.outline,
        )
    }

    // 空数据态
    if (total == 0) {
        Spacer(GlanceModifier.height(sizing.dp(12)))
        WidgetEmptyState(
            icon = R.drawable.ic_symbol_add_to_home_screen,
            title = ctx.getString(R.string.widget_no_plan_today),
            body = ctx.getString(R.string.widget_add_prompt),
            sizing = sizing,
        )
        return
    }

    // 全部完成态
    if (allDone) {
        Spacer(GlanceModifier.height(sizing.dp(12)))
        WidgetEmptyState(
            icon = R.drawable.ic_symbol_check_circle,
            title = ctx.getString(R.string.widget_all_done_msg),
            sizing = sizing,
        )
        return
    }

    // ── 待服列表 + 打卡按钮 ──────────────────────────────────
    if (maxShow > 0 && pendingMeds.isNotEmpty()) {
        Spacer(GlanceModifier.height(sizing.dp(8)))
        Text(
            ctx.getString(R.string.widget_pending_label),
            style = TextStyle(fontSize = sizing.sp(11), fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurfaceVariant),
        )
        Spacer(GlanceModifier.height(sizing.dp(2)))

        pendingMeds.take(maxShow).forEach { (medId, name, scheduledMinutes) ->
            Spacer(GlanceModifier.height(sizing.dp(3)))
            Row(
                modifier          = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                // 药品名
                Text(
                    "· $name",
                    style    = TextStyle(fontSize = sizing.sp(11), color = GlanceTheme.colors.onSurface),
                    modifier = GlanceModifier.defaultWeight(),
                )
                // 服药时间标签
                val timeLabel = "%02d:%02d".format(scheduledMinutes / 60, scheduledMinutes % 60)
                Text(
                    timeLabel,
                    style = TextStyle(fontSize = sizing.sp(10), color = GlanceTheme.colors.onSurfaceVariant),
                )
                // 操作模式：服药按钮（较大圆角矩形）；状态模式：空心圆表示"待服"
                if (showActions) {
                    WidgetActionButton(
                        label = ctx.getString(R.string.widget_action_btn),
                        action = actionRunCallback<MarkTakenAction>(
                            actionParametersOf(MarkTakenAction.medIdKey to medId),
                        ),
                        sizing = sizing,
                    )
                } else {
                    // 状态模式：○ 空心圆表示待服，无点击
                    Box(
                        modifier = GlanceModifier
                            .size(sizing.dp(18))
                            .background(GlanceTheme.colors.primaryContainer)
                            .cornerRadius(sizing.dp(9)),
                        contentAlignment = Alignment.Center,
                    ) {}
                }
            }
        }

        val remaining = pendingMeds.size - maxShow
        if (remaining > 0) {
            Spacer(GlanceModifier.height(sizing.dp(3)))
            Text(
                ctx.resources.getQuantityString(R.plurals.widget_remaining_fmt, remaining, remaining),
                style = TextStyle(fontSize = sizing.sp(10), color = GlanceTheme.colors.onSurfaceVariant),
            )
        }
    }
}
