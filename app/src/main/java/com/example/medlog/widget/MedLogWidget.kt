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
 * 支持三种响应式尺寸：
 * - 紧凑 (≤140dp 宽)：仅显示 "X/Y 已服" + 进度条
 * - 标准 (≤260dp 宽)：标题 + 进度 + 最多 2 条待服药品
 * - 宽屏 (>260dp 宽)：标题 + 进度 + 最多 4 条待服药品
 */
class MedLogWidget : GlanceAppWidget() {

    // 定义三种响应式尺寸断点
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(120.dp, 80.dp),   // 紧凑：2×1
            DpSize(180.dp, 100.dp),  // 标准：3×2
            DpSize(280.dp, 100.dp),  // 宽屏：4×2
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 直接访问 Room 数据库（Glance Worker 进程无法使用 Hilt）
        val db = MedLogDatabase.getInstance(context)

        val medications = db.medicationDao().getAllMedicationsOnce()      // 活跃药品
        val logs       = db.medicationLogDao().getLogsForDateOnce(todayStart())

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
    val size = LocalSize.current
    val isCompact = size.width < 150.dp
    val isWide    = size.width >= 260.dp
    val maxPending = when { isWide -> 4; isCompact -> 0; else -> 2 }

    val openAppAction = actionStartActivity<MainActivity>()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(12.dp)
            .clickable(openAppAction),
        verticalAlignment = Alignment.Vertical.Top,
    ) {
        // ── 标题行（紧凑模式隐藏）──────────────────────────────
        if (!isCompact) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Text(
                    text = "💊 用药日志",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.primary,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
        }

        // ── 进度文字 ─────────────────────────────────────────
        val progressText = when {
            total == 0     -> "今日无用药计划"
            taken == total -> "🎉 全部完成！"
            else           -> "已服 $taken / $total"
        }
        Text(
            text = progressText,
            style = TextStyle(
                fontSize = if (isCompact) 13.sp else 12.sp,
                fontWeight = if (isCompact) FontWeight.Bold else FontWeight.Medium,
                color = GlanceTheme.colors.onSurface,
            ),
        )

        Spacer(GlanceModifier.height(5.dp))

        // ── 进度条 ───────────────────────────────────────────
        if (total > 0) {
            LinearProgressIndicator(
                progress = taken.toFloat() / total,
                modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.primaryContainer,
            )
        }

        // ── 待服药品名（按尺寸控制条数）─────────────────────
        if (maxPending > 0 && pendingNames.isNotEmpty()) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = "待服：",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = GlanceTheme.colors.onSurfaceVariant,
                ),
            )
            pendingNames.take(maxPending).forEach { name ->
                Text(
                    text = "• $name",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurface,
                    ),
                )
            }
            // 超出显示条数时提示省略
            val remaining = pendingNames.size - maxPending
            if (remaining > 0) {
                Text(
                    text = "…还有 $remaining 种",
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
                    ),
                )
            }
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
