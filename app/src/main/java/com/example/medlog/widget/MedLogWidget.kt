package com.example.medlog.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.medlog.data.local.MedLogDatabase
import com.example.medlog.data.model.LogStatus
import com.example.medlog.ui.MainActivity
import java.util.Calendar

/**
 * 今日用药进度桌面小组件（Jetpack Glance M3）
 *
 * 根据小米小部件规范设计，支持三种响应式尺寸：
 * - 紧凑 2×2 (≤160dp 宽)：大号进度数字 + 进度条，垂直居中
 * - 标准 4×2 (≤260dp 宽)：标题行 + 进度条 + 最多 2 条待服（含 ✓ 打卡按钮）
 * - 宽屏 4×4 (高度≥160dp)：完整信息 + 最多 5 条待服（含 ✓ 打卡按钮）
 *
 * 交互：点击待服药品旁的 ✓ 触发 [MarkTakenAction]，写入 TAKEN 日志并刷新。
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
        val db          = MedLogDatabase.getInstance(context)
        val medications = db.medicationDao().getAllMedicationsOnce()
        val logs        = db.medicationLogDao().getLogsForDateOnce(todayStart())

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

        provideContent {
            GlanceTheme {
                WidgetContent(taken = taken, total = total, pendingMeds = pending)
            }
        }
    }
}

@Composable
private fun WidgetContent(
    taken: Int,
    total: Int,
    pendingMeds: List<Triple<Long, String, Int>>,
) {
    val size      = LocalSize.current
    val isCompact = size.width < 160.dp
    val isTall    = size.height >= 160.dp
    val maxShow   = when { isCompact -> 0; isTall -> 5; else -> 2 }
    val allDone   = total > 0 && taken == total

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(
                if (allDone) GlanceTheme.colors.tertiaryContainer
                else         GlanceTheme.colors.surfaceVariant,
            )
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment   = if (isCompact) Alignment.Vertical.CenterVertically   else Alignment.Vertical.Top,
        horizontalAlignment = if (isCompact) Alignment.Horizontal.CenterHorizontally else Alignment.Horizontal.Start,
    ) {
        if (isCompact) {
            CompactContent(taken = taken, total = total, allDone = allDone)
        } else {
            StandardContent(
                taken      = taken,
                total      = total,
                allDone    = allDone,
                pendingMeds = pendingMeds,
                maxShow    = maxShow,
            )
        }
    }
}

// ─── 紧凑模式（2×2）────────────────────────────────────────────────────────
@Composable
private fun CompactContent(taken: Int, total: Int, allDone: Boolean) {
    when {
        total == 0 -> {
            Text("💊", style = TextStyle(fontSize = 22.sp))
            Spacer(GlanceModifier.height(4.dp))
            Text(
                "暂无计划",
                style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
            )
        }
        allDone -> {
            Text(
                "✓",
                style = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.tertiary,
                ),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                "全部完成",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onTertiaryContainer,
                ),
            )
        }
        else -> {
            Text(
                "$taken/$total",
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface,
                ),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text("已服", style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant))
            Spacer(GlanceModifier.height(8.dp))
            LinearProgressIndicator(
                progress        = taken.toFloat() / total,
                modifier        = GlanceModifier.fillMaxWidth().height(6.dp),
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
) {
    // 标题 + 核心数字（F 型阅读动线）
    Row(
        modifier            = GlanceModifier.fillMaxWidth(),
        verticalAlignment   = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.Start,
    ) {
        Text(
            "用药日志",
            style    = TextStyle(
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
                color      = GlanceTheme.colors.onSurfaceVariant,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text  = when {
                total == 0 -> "--"
                allDone    -> "全部 ✓"
                else       -> "$taken / $total"
            },
            style = TextStyle(
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                color      = if (allDone) GlanceTheme.colors.tertiary else GlanceTheme.colors.primary,
            ),
        )
    }

    Spacer(GlanceModifier.height(7.dp))

    if (total > 0) {
        LinearProgressIndicator(
            progress        = taken.toFloat() / total,
            modifier        = GlanceModifier.fillMaxWidth().height(7.dp),
            color           = if (allDone) GlanceTheme.colors.tertiary else GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.outline,
        )
    }

    // 空数据态
    if (total == 0) {
        Spacer(GlanceModifier.height(10.dp))
        Text("今日暂无用药计划", style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
        Text(
            "点击进入添加 →",
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.primary),
        )
        return
    }

    // 全部完成态
    if (allDone) {
        Spacer(GlanceModifier.height(10.dp))
        Text(
            "🎉 今日用药全部完成！",
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onTertiaryContainer),
        )
        return
    }

    // ── 待服列表 + 打卡按钮 ──────────────────────────────────
    if (maxShow > 0 && pendingMeds.isNotEmpty()) {
        Spacer(GlanceModifier.height(8.dp))
        Text(
            "待服",
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurfaceVariant),
        )
        Spacer(GlanceModifier.height(2.dp))

        pendingMeds.take(maxShow).forEach { (medId, name, scheduledMinutes) ->
            Spacer(GlanceModifier.height(3.dp))
            Row(
                modifier          = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                // 药品名
                Text(
                    "· $name",
                    style    = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurface),
                    modifier = GlanceModifier.defaultWeight(),
                )
                // 服药时间标签
                val timeLabel = "%02d:%02d".format(scheduledMinutes / 60, scheduledMinutes % 60)
                Text(
                    timeLabel,
                    style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
                )
                // 圆形打卡按钮 ✓（点击标记已服）
                Box(
                    modifier = GlanceModifier
                        .size(26.dp)
                        .background(GlanceTheme.colors.primaryContainer)
                        .cornerRadius(13.dp)
                        .clickable(
                            actionRunCallback<MarkTakenAction>(
                                actionParametersOf(MarkTakenAction.medIdKey to medId),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "✓",
                        style = TextStyle(
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color      = GlanceTheme.colors.primary,
                        ),
                    )
                }
            }
        }

        val remaining = pendingMeds.size - maxShow
        if (remaining > 0) {
            Spacer(GlanceModifier.height(3.dp))
            Text(
                "…还有 $remaining 种",
                style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
            )
        }
    }
}
