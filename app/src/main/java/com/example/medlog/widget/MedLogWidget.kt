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
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
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
 * - 标准 4×2 (≤260dp 宽)：标题行 + 进度条 + 最多 2 条待服药品
 * - 宽屏 4×4 (>260dp 或高度≥160dp)：完整信息 + 最多 5 条待服药品
 *
 * 规范对照：
 * - 圆角：20dp（规范 55px ≈ 18-20dp）
 * - 安全边距：14dp（规范 ≥42px = 14dp @3x）
 * - 文字层级：标题用 onSurfaceVariant / 核心数据用 Bold+primary
 * - allDone 态：背景切换为 tertiaryContainer，提升完成感
 * - 深色模式：完全通过 GlanceTheme.colors 自适应
 */
class MedLogWidget : GlanceAppWidget() {

    // 三种尺寸断点：对应规范的 2×2 / 4×2 / 4×4
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(80.dp, 80.dp),    // 紧凑：2×2
            DpSize(180.dp, 80.dp),   // 标准：4×2（横向扩展）
            DpSize(180.dp, 160.dp),  // 宽屏：4×4（纵向扩展）
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 直接访问 Room 数据库（Glance Worker 进程无法使用 Hilt）
        val db          = MedLogDatabase.getInstance(context)
        val medications = db.medicationDao().getAllMedicationsOnce()
        val logs        = db.medicationLogDao().getLogsForDateOnce(todayStart())

        val takenIds = logs.filter { it.status == LogStatus.TAKEN }.map { it.medicationId }.toSet()
        val total    = medications.size
        val taken    = medications.count { it.id in takenIds }
        val pending  = medications.filter { it.id !in takenIds }.map { it.name }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    taken        = taken,
                    total        = total,
                    pendingNames = pending,
                )
            }
        }
    }
}

@Composable
private fun WidgetContent(
    taken: Int,
    total: Int,
    pendingNames: List<String>,
) {
    val size       = LocalSize.current
    val isCompact  = size.width < 160.dp
    val isTall     = size.height >= 160.dp
    val maxPending = when { isCompact -> 0; isTall -> 5; else -> 2 }
    val allDone    = total > 0 && taken == total

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            // allDone 时使用 tertiaryContainer 提升完成感觉（深色模式自适应）
            .background(
                if (allDone) GlanceTheme.colors.tertiaryContainer
                else         GlanceTheme.colors.surfaceVariant,
            )
            .cornerRadius(20.dp)          // 规范：手机端 55px ≈ 20dp
            .padding(14.dp)               // 规范：安全区 ≥42px = 14dp @3x
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment   = if (isCompact) Alignment.Vertical.CenterVertically
                              else           Alignment.Vertical.Top,
        horizontalAlignment = if (isCompact) Alignment.Horizontal.CenterHorizontally
                              else           Alignment.Horizontal.Start,
    ) {
        if (isCompact) {
            CompactContent(taken = taken, total = total, allDone = allDone)
        } else {
            StandardContent(
                taken        = taken,
                total        = total,
                allDone      = allDone,
                pendingNames = pendingNames,
                maxPending   = maxPending,
            )
        }
    }
}

// ─── 紧凑模式（2×2）：大号数字 + 简短说明 ───────────────────────────────
@Composable
private fun CompactContent(taken: Int, total: Int, allDone: Boolean) {
    when {
        total == 0 -> {
            // 无计划态
            Text(
                text = "💊",
                style = TextStyle(fontSize = 22.sp),
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = "暂无计划",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = GlanceTheme.colors.onSurfaceVariant,
                ),
            )
        }
        allDone -> {
            // 全部完成态
            Text(
                text = "✓",
                style = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.tertiary,
                ),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = "全部完成",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onTertiaryContainer,
                ),
            )
        }
        else -> {
            // 正常进行态：突出分数
            Text(
                text = "$taken/$total",
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface,
                ),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = "已服",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = GlanceTheme.colors.onSurfaceVariant,
                ),
            )
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

// ─── 标准 / 宽屏模式（4×2 & 4×4）────────────────────────────────────────
@Composable
private fun StandardContent(
    taken: Int,
    total: Int,
    allDone: Boolean,
    pendingNames: List<String>,
    maxPending: Int,
) {
    // ── 标题行（F 型阅读：左标题 + 右核心数字）──────────────
    Row(
        modifier            = GlanceModifier.fillMaxWidth(),
        verticalAlignment   = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.Start,
    ) {
        // 左侧：品牌标题（规范：中等字重 + onSurfaceVariant ≈ 40% 透明度效果）
        Text(
            text     = "用药日志",
            style    = TextStyle(
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
                color      = GlanceTheme.colors.onSurfaceVariant,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        // 右侧：核心数据突出（规范：数据类核心信息 Bold + 主色）
        Text(
            text  = when {
                total == 0 -> "--"
                allDone    -> "全部 ✓"
                else       -> "$taken / $total"
            },
            style = TextStyle(
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                color      = if (allDone) GlanceTheme.colors.tertiary
                             else         GlanceTheme.colors.primary,
            ),
        )
    }

    Spacer(GlanceModifier.height(7.dp))

    // ── 进度条（全宽，track 使用 outline 与背景区分）────────
    if (total > 0) {
        LinearProgressIndicator(
            progress        = taken.toFloat() / total,
            modifier        = GlanceModifier.fillMaxWidth().height(7.dp),
            color           = if (allDone) GlanceTheme.colors.tertiary
                              else         GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.outline,
        )
    }

    // ── 空数据态 ────────────────────────────────────────────
    if (total == 0) {
        Spacer(GlanceModifier.height(10.dp))
        Text(
            text  = "今日暂无用药计划",
            style = TextStyle(
                fontSize = 12.sp,
                color    = GlanceTheme.colors.onSurfaceVariant,
            ),
        )
        Text(
            text  = "点击进入添加 →",
            style = TextStyle(
                fontSize   = 11.sp,
                fontWeight = FontWeight.Medium,
                color      = GlanceTheme.colors.primary,
            ),
        )
        return
    }

    // ── 全部完成态 ──────────────────────────────────────────
    if (allDone) {
        Spacer(GlanceModifier.height(10.dp))
        Text(
            text  = "🎉 今日用药全部完成！",
            style = TextStyle(
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
                color      = GlanceTheme.colors.onTertiaryContainer,
            ),
        )
        return
    }

    // ── 待服药品列表 ────────────────────────────────────────
    if (maxPending > 0 && pendingNames.isNotEmpty()) {
        Spacer(GlanceModifier.height(8.dp))
        // 小节标题（次要信息）
        Text(
            text  = "待服",
            style = TextStyle(
                fontSize   = 11.sp,
                fontWeight = FontWeight.Medium,
                color      = GlanceTheme.colors.onSurfaceVariant,
            ),
        )
        Spacer(GlanceModifier.height(3.dp))
        pendingNames.take(maxPending).forEach { name ->
            Text(
                text  = "· $name",
                style = TextStyle(
                    fontSize = 11.sp,
                    color    = GlanceTheme.colors.onSurface,
                ),
            )
        }
        val remaining = pendingNames.size - maxPending
        if (remaining > 0) {
            Text(
                text  = "…还有 $remaining 种",
                style = TextStyle(
                    fontSize = 10.sp,
                    color    = GlanceTheme.colors.onSurfaceVariant,
                ),
            )
        }
    }
}

/** 今日 00:00:00 的时间戳（毫秒） */
private fun todayStart(): Long =
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
