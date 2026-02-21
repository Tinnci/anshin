package com.example.medlog.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.medlog.R
import com.example.medlog.data.local.MedLogDatabase
import com.example.medlog.data.model.LogStatus
import com.example.medlog.ui.MainActivity
import java.util.Calendar

/**
 * 连续打卡桌面小组件（Jetpack Glance M3）
 *
 * 展示用户连续每日完成用药的天数（Streak），以及最近 7 天的完成情况可视化。
 *
 * 支持两种尺寸：
 * - 紧凑 2×2：大号 Streak 天数 + 火焰 Emoji
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
        val db          = MedLogDatabase.getInstance(context)
        val medications = db.medicationDao().getAllMedicationsOnce()
        val total       = medications.size

        // 查询最近 30 天的所有日志（用于 Streak 计算）
        val rangeStart = daysAgoStart(29)
        val rangeEnd   = todayEnd()
        val allLogs    = db.medicationLogDao().getLogsForRangeOnce(rangeStart, rangeEnd)

        // 判断某天是否完成（全部药品均已标记 TAKEN）
        fun dayComplete(dayStart: Long): Boolean {
            if (total == 0) return false
            val dayEnd   = dayStart + 86_400_000L - 1
            val taken    = allLogs.count { it.scheduledTimeMs in dayStart..dayEnd && it.status == LogStatus.TAKEN }
            return taken >= total
        }

        // 最近 7 天完成情况：Pair(isComplete, 周几标签)
        // index 0 = 6 天前, index 6 = 今天
        val cal = Calendar.getInstance()
        val dayData = (6 downTo 0).reversed().map { daysBack ->
            val tempCal = cal.clone() as Calendar
            tempCal.add(Calendar.DAY_OF_YEAR, -daysBack)
            val label = when (tempCal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY    -> context.getString(R.string.widget_weekday_sun)
                Calendar.MONDAY    -> context.getString(R.string.widget_weekday_mon)
                Calendar.TUESDAY   -> context.getString(R.string.widget_weekday_tue)
                Calendar.WEDNESDAY -> context.getString(R.string.widget_weekday_wed)
                Calendar.THURSDAY  -> context.getString(R.string.widget_weekday_thu)
                Calendar.FRIDAY    -> context.getString(R.string.widget_weekday_fri)
                Calendar.SATURDAY  -> context.getString(R.string.widget_weekday_sat)
                else               -> "?"
            }
            tempCal.set(Calendar.HOUR_OF_DAY, 0)
            tempCal.set(Calendar.MINUTE, 0)
            tempCal.set(Calendar.SECOND, 0)
            tempCal.set(Calendar.MILLISECOND, 0)
            val dayStart = tempCal.timeInMillis
            Pair(dayComplete(dayStart), label)
        }

        // 计算连续天数（从今日起倒推，连续完成的天数）
        var streak = 0
        for (daysBack in 0..29) {
            val tempCal = cal.clone() as Calendar
            tempCal.add(Calendar.DAY_OF_YEAR, -daysBack)
            tempCal.set(Calendar.HOUR_OF_DAY, 0)
            tempCal.set(Calendar.MINUTE, 0)
            tempCal.set(Calendar.SECOND, 0)
            tempCal.set(Calendar.MILLISECOND, 0)
            if (dayComplete(tempCal.timeInMillis)) streak++ else break
        }

        provideContent {
            GlanceTheme {
                StreakContent(total = total, streak = streak, dayData = dayData)
            }
        }
    }
}

@Composable
private fun StreakContent(
    total: Int,
    streak: Int,
    dayData: List<Pair<Boolean, String>>,
) {
    val size      = LocalSize.current
    val isCompact = size.width < 160.dp
    val ctx = LocalContext.current

    // 背景色：streak >= 7 使用 tertiaryContainer（高激励色），否则默认
    val bg = if (streak >= 7) GlanceTheme.colors.tertiaryContainer
             else              GlanceTheme.colors.surfaceVariant

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bg)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment   = if (isCompact) Alignment.Vertical.CenterVertically else Alignment.Vertical.Top,
        horizontalAlignment = if (isCompact) Alignment.Horizontal.CenterHorizontally else Alignment.Horizontal.Start,
    ) {
        if (total == 0) {
            // 无用药计划
            if (isCompact) {
                Text("💊", style = TextStyle(fontSize = 20.sp))
                Spacer(GlanceModifier.height(4.dp))
                Text(ctx.getString(R.string.widget_no_plan), style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant))
            } else {
                Text(
                    ctx.getString(R.string.widget_streak_title),
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurfaceVariant),
                )
                Spacer(GlanceModifier.height(8.dp))
                Text(ctx.getString(R.string.widget_no_plan_today), style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
            }
            return@Column
        }

        if (isCompact) {
            // ── 紧凑 2×2 ─────────────────────────────────────────
            Text(
                if (streak > 0) "🔥" else "💊",
                style = TextStyle(fontSize = 18.sp),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                "$streak",
                style = TextStyle(
                    fontSize   = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color      = if (streak >= 7) GlanceTheme.colors.tertiary else GlanceTheme.colors.primary,
                ),
            )
            Text(
                ctx.getString(R.string.widget_streak_days_unit),
                style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
            )
        } else {
            // ── 标准 4×2 ─────────────────────────────────────
            // 标题行
            Row(
                modifier          = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Text(
                    ctx.getString(R.string.widget_streak_title),
                    style    = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurfaceVariant),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    if (streak > 0) ctx.getString(R.string.widget_streak_days_fmt, streak) else ctx.getString(R.string.widget_streak_zero),
                    style = TextStyle(
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color      = if (streak >= 7) GlanceTheme.colors.tertiary else GlanceTheme.colors.primary,
                    ),
                )
            }
            Spacer(GlanceModifier.height(10.dp))
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
                                .size(22.dp)
                                .cornerRadius(11.dp)
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
                                        fontSize   = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color      = if (isToday) GlanceTheme.colors.onTertiary else GlanceTheme.colors.onPrimary,
                                    ),
                                )
                            }
                        }
                        Spacer(GlanceModifier.height(3.dp))
                        // 周几标签
                        Text(
                            label,
                            style = TextStyle(
                                fontSize = 9.sp,
                                color    = if (isToday) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant,
                                fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal,
                            ),
                        )
                    }
                    if (index < dayData.size - 1) {
                        Spacer(GlanceModifier.width(6.dp))
                    }
                }
            }
        }
    }
}
