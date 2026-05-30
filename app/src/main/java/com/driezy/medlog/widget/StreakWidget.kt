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
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.driezy.medlog.R
import com.driezy.medlog.data.local.settingsDataStore
import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.domain.StreakCalculator
import com.driezy.medlog.domain.daysAgoStart
import com.driezy.medlog.domain.todayEnd
import com.driezy.medlog.ui.MainActivity
import dagger.hilt.android.EntryPointAccessors
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * 连续打卡桌面小组件（Jetpack Glance M3）
 *
 * 展示用户连续每日完成用药的天数（Streak），以及最近 7 天的完成情况可视化。
 *
 * 支持两种尺寸：
 * - 紧凑 2×2：大号 Streak 天数 + 图标徽章
 * - 标准 4×2：7 天打点图 + Streak 天数 + 简述
 */
class StreakWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(80.dp, 80.dp),   // 2×2
            DpSize(180.dp, 80.dp),  // 4×2
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep            = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val medications   = ep.medicationRepository().getActiveOnce()
        // PRN（按需）药品无固定服药计划，不计入每日完成判断
        val scheduledMeds = medications.filter { !it.isPRN }
        val total         = scheduledMeds.size
        val scheduledIds  = scheduledMeds.map { it.id }.toSet()

        // 查询最近 30 天的所有日志（用于 Streak 计算）
        val rangeStart = daysAgoStart(29)
        val rangeEnd   = todayEnd()
        val allLogs    = ep.logRepository().getLogsForRangeOnce(rangeStart, rangeEnd)
        val zone       = ZoneId.systemDefault()

        // 判断某天是否完成（所有计划药品均已标记 TAKEN）
        fun dayComplete(dayStartMs: Long): Boolean {
            if (total == 0) return false
            val dayEndMs = dayStartMs + 86_400_000L - 1
            val taken    = allLogs.count {
                it.scheduledTimeMs in dayStartMs..dayEndMs &&
                it.status == LogStatus.TAKEN &&
                it.medicationId in scheduledIds
            }
            return taken >= total
        }

        // SSOT：使用 domain/StreakCalculator 计算连续天数
        val daysWithActivity = (0..29).mapNotNullTo(mutableSetOf()) { daysBack ->
            val dayStart = daysAgoStart(daysBack)
            if (dayComplete(dayStart)) Instant.ofEpochMilli(dayStart).atZone(zone).toLocalDate()
            else null
        }
        val streak = StreakCalculator.currentStreak(daysWithActivity)

        // 最近 7 天完成情况（index 0 = 6天前，index 6 = 今天）
        // 注意：(6 downTo 0)映射得 daysBack=6在 index 0，daysBack=0在 index 6，与 isToday = (index == size-1) 匹配
        val dayData = (6 downTo 0).map { daysBack ->
            val dayStartMs = daysAgoStart(daysBack)
            val localDate  = Instant.ofEpochMilli(dayStartMs).atZone(zone).toLocalDate()
            val label = when (localDate.dayOfWeek) {
                DayOfWeek.SUNDAY    -> context.getString(R.string.widget_weekday_sun)
                DayOfWeek.MONDAY    -> context.getString(R.string.widget_weekday_mon)
                DayOfWeek.TUESDAY   -> context.getString(R.string.widget_weekday_tue)
                DayOfWeek.WEDNESDAY -> context.getString(R.string.widget_weekday_wed)
                DayOfWeek.THURSDAY  -> context.getString(R.string.widget_weekday_thu)
                DayOfWeek.FRIDAY    -> context.getString(R.string.widget_weekday_fri)
                DayOfWeek.SATURDAY  -> context.getString(R.string.widget_weekday_sat)
                else                -> "?"
            }
            Pair(dayComplete(dayStartMs), label)
        }
        val widgetPrefs = runCatching { context.settingsDataStore.data.first() }
            .getOrElse { androidx.datastore.preferences.core.emptyPreferences() }
        val appearance = widgetPrefs.medLogWidgetAppearance()

        provideContent {
            MedLogGlanceTheme(appearance = appearance) {
                StreakContent(
                    total = total,
                    streak = streak,
                    dayData = dayData,
                    sizing = appearance.sizing,
                )
            }
        }
    }
}

@Composable
private fun StreakContent(
    total: Int,
    streak: Int,
    dayData: List<Pair<Boolean, String>>,
    sizing: WidgetSizing,
) {
    val size      = LocalSize.current
    val isCompact = size.width < 160.dp
    val ctx = LocalContext.current

    // 背景色：streak >= 7 使用 tertiaryContainer（高激励色），否则默认
    WidgetContainer(
        prominent = streak >= 7,
        modifier = GlanceModifier
            .clickable(actionStartActivity<MainActivity>()),
        sizing = sizing,
        verticalAlignment   = if (isCompact) Alignment.Vertical.CenterVertically else Alignment.Vertical.Top,
        horizontalAlignment = if (isCompact) Alignment.Horizontal.CenterHorizontally else Alignment.Horizontal.Start,
    ) {
        if (total == 0) {
            // 无用药计划
            if (isCompact) {
                WidgetEmptyState(
                    icon = R.drawable.ic_symbol_medication,
                    title = ctx.getString(R.string.widget_no_plan),
                    compact = true,
                    sizing = sizing,
                )
            } else {
                WidgetHeader(
                    icon = R.drawable.ic_symbol_event_repeat,
                    title = ctx.getString(R.string.widget_streak_title),
                    trailing = ctx.getString(R.string.widget_streak_zero),
                    sizing = sizing,
                )
                Spacer(GlanceModifier.height(sizing.dp(8)))
                WidgetEmptyState(
                    icon = R.drawable.ic_symbol_add_to_home_screen,
                    title = ctx.getString(R.string.widget_no_plan_today),
                    body = ctx.getString(R.string.widget_add_prompt),
                    sizing = sizing,
                )
            }
            return@WidgetContainer
        }

        if (isCompact) {
            // ── 紧凑 2×2 ─────────────────────────────────────────
            WidgetIconBadge(
                icon = R.drawable.ic_symbol_event_repeat,
                prominent = streak >= 7,
                size = sizing.dp(40),
                iconSize = sizing.dp(22),
            )
            Spacer(GlanceModifier.height(sizing.dp(4)))
            Text(
                "$streak",
                style = TextStyle(
                    fontSize   = sizing.sp(26),
                    fontWeight = FontWeight.Bold,
                    color      = if (streak >= 7) GlanceTheme.colors.tertiary else GlanceTheme.colors.primary,
                ),
            )
            Text(
                ctx.getString(R.string.widget_streak_days_unit),
                style = TextStyle(fontSize = sizing.sp(11), color = GlanceTheme.colors.onSurfaceVariant),
            )
        } else {
            // ── 标准 4×2 ─────────────────────────────────────
            // 标题行
            WidgetHeader(
                icon = R.drawable.ic_symbol_event_repeat,
                title = ctx.getString(R.string.widget_streak_title),
                trailing = if (streak > 0) {
                    ctx.resources.getQuantityString(R.plurals.widget_streak_days_fmt, streak, streak)
                } else {
                    ctx.getString(R.string.widget_streak_zero)
                },
                prominent = streak >= 7,
                sizing = sizing,
            )
            Spacer(GlanceModifier.height(sizing.dp(10)))
            // 7 天打点图
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            ) {
                dayData.forEachIndexed { index, (isComplete, label) ->
                    val isToday  = index == dayData.size - 1
                    Column(
                        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                    ) {
                        // 圆点
                        Box(
                            modifier = GlanceModifier
                                .size(sizing.dp(22))
                                .cornerRadius(sizing.dp(11))
                                .background(
                                    when {
                                        isComplete && isToday -> GlanceTheme.colors.tertiary
                                        isComplete            -> GlanceTheme.colors.primary
                                        else                  -> GlanceTheme.colors.outline
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isComplete) {
                                Text(
                                    "✓",
                                    style = TextStyle(
                                        fontSize   = sizing.sp(10),
                                        fontWeight = FontWeight.Bold,
                                        color      = if (isToday) GlanceTheme.colors.onTertiary else GlanceTheme.colors.onPrimary,
                                    ),
                                )
                            }
                        }
                        Spacer(GlanceModifier.height(sizing.dp(3)))
                        // 周几标签
                        Text(
                            label,
                            style = TextStyle(
                                fontSize = sizing.sp(9),
                                color    = if (isToday) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant,
                                fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal,
                            ),
                        )
                    }
                    if (index < dayData.size - 1) {
                        Spacer(GlanceModifier.width(sizing.dp(6)))
                    }
                }
            }
        }
    }
}
