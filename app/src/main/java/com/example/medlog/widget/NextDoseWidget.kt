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
import kotlin.math.abs

/**
 * 下次服药桌面小组件（Jetpack Glance M3）
 *
 * 根据小米小部件规范：MedLog·下次服药
 *
 * 显示今日下次服药的时间点及对应药品，帮助用户提前准备。
 * 支持两种尺寸：
 * - 紧凑 2×2：大号时间 + 第一个药品名
 * - 标准 4×2：时间 + 倒计时 + 完整药品列表
 */
class NextDoseWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(80.dp, 80.dp),   // 2×2
            DpSize(180.dp, 80.dp),  // 4×2
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db          = MedLogDatabase.getInstance(context)
        val medications = db.medicationDao().getAllMedicationsOnce()
        val logs        = db.medicationLogDao().getLogsForDateOnce(todayStart())

        val takenIds  = logs.filter { it.status == LogStatus.TAKEN }.map { it.medicationId }.toSet()
        val total     = medications.size
        val allDone   = total > 0 && takenIds.size >= total

        val cal = Calendar.getInstance()
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        // 找出今日尚未服用的药品及其下次服药时间
        // 对每个待服药品，解析 reminderTimes（HH:mm 逗号分隔列表）
        // 取今日最近的未来服药时间
        val nextDoseGroups = mutableMapOf<Int, MutableList<String>>() // 分钟数 → 药品列表

        medications.filter { it.id !in takenIds }.forEach { med ->
            val earliest = parseReminderTimes(med.reminderTimes)
                .map { (h, m) -> h * 60 + m }
                .filter { it >= nowMinutes }
                .minOrNull()
                // 如果全部已过，也用主提醒时间（显示今日所有未服）
                ?: (med.reminderHour * 60 + med.reminderMinute)

            nextDoseGroups.getOrPut(earliest) { mutableListOf() }.add(med.name)
        }

        // 取最近的时间组
        val nextGroup = nextDoseGroups.minByOrNull { it.key }

        provideContent {
            GlanceTheme {
                NextDoseContent(
                    total        = total,
                    allDone      = allDone,
                    nextMinutes  = nextGroup?.key,
                    nextMedNames = nextGroup?.value ?: emptyList(),
                    nowMinutes   = nowMinutes,
                )
            }
        }
    }
}

@Composable
private fun NextDoseContent(
    total: Int,
    allDone: Boolean,
    nextMinutes: Int?,
    nextMedNames: List<String>,
    nowMinutes: Int,
) {
    val size      = LocalSize.current
    val isCompact = size.width < 160.dp

    val hour   = (nextMinutes ?: 0) / 60
    val minute = (nextMinutes ?: 0) % 60
    val timeStr = "%02d:%02d".format(hour, minute)

    // 倒计时文字
    val diff = (nextMinutes ?: 0) - nowMinutes
    val countdownText = when {
        nextMinutes == null    -> ""
        diff <= 0              -> "现在服药"
        diff < 60              -> "还有 $diff 分钟"
        diff < 120             -> "还有 1 小时 ${diff % 60} 分钟"
        else                   -> "还有约 ${diff / 60} 小时"
    }

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
        verticalAlignment   = if (isCompact) Alignment.Vertical.CenterVertically else Alignment.Vertical.Top,
        horizontalAlignment = if (isCompact) Alignment.Horizontal.CenterHorizontally else Alignment.Horizontal.Start,
    ) {
        when {
            total == 0 -> {
                // 无计划
                if (isCompact) {
                    Text("💊", style = TextStyle(fontSize = 20.sp))
                    Spacer(GlanceModifier.height(4.dp))
                    Text("无计划", style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant))
                } else {
                    Text(
                        "下次服药",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurfaceVariant),
                    )
                    Spacer(GlanceModifier.height(10.dp))
                    Text("今日暂无用药计划", style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
                    Text(
                        "点击进入添加 →",
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.primary),
                    )
                }
            }
            allDone -> {
                // 全部完成
                if (isCompact) {
                    Text("✓", style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.tertiary))
                    Spacer(GlanceModifier.height(2.dp))
                    Text("今日完成", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onTertiaryContainer))
                } else {
                    Text(
                        "下次服药",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurfaceVariant),
                    )
                    Spacer(GlanceModifier.height(10.dp))
                    Text(
                        "🎉 今日用药全部完成！",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onTertiaryContainer),
                    )
                }
            }
            nextMinutes == null -> {
                // 有药但没有未来时间（不应发生）
                if (!isCompact) {
                    Text("下次服药", style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
                    Spacer(GlanceModifier.height(8.dp))
                }
                Text("暂无待服药品", style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant))
            }
            isCompact -> {
                // 2×2：大号时间 + 第一个药品名
                Text(
                    timeStr,
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    nextMedNames.firstOrNull() ?: "",
                    style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurface),
                )
                if (nextMedNames.size > 1) {
                    Text(
                        "+${nextMedNames.size - 1} 种",
                        style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    )
                }
            }
            else -> {
                // 4×2：完整显示
                // 标题行（F 型）
                Row(
                    modifier          = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    Text(
                        "下次服药",
                        style    = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurfaceVariant),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Text(
                        timeStr,
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary),
                    )
                }
                Spacer(GlanceModifier.height(4.dp))
                // 倒计时（次要信息）
                Text(
                    countdownText,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface),
                )
                Spacer(GlanceModifier.height(6.dp))
                // 药品列表
                nextMedNames.take(3).forEach { name ->
                    Text(
                        "· $name",
                        style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurface),
                    )
                }
                if (nextMedNames.size > 3) {
                    Text(
                        "…还有 ${nextMedNames.size - 3} 种",
                        style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    )
                }
            }
        }
    }
}

/** 解析 "HH:mm,HH:mm,..." 字符串为 (小时, 分钟) 对列表 */
private fun parseReminderTimes(timesStr: String): List<Pair<Int, Int>> =
    timesStr.split(",").mapNotNull { token ->
        val parts = token.trim().split(":")
        if (parts.size >= 2) {
            val h = parts[0].toIntOrNull() ?: return@mapNotNull null
            val m = parts[1].toIntOrNull() ?: return@mapNotNull null
            Pair(h, m)
        } else null
    }

private fun todayStart(): Long =
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
