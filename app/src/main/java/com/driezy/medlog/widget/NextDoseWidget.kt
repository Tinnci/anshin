package com.driezy.medlog.widget

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
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.driezy.medlog.R
import com.driezy.medlog.data.local.settingsDataStore
import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.domain.todayEnd
import com.driezy.medlog.domain.todayStart
import com.driezy.medlog.ui.MainActivity
import dagger.hilt.android.EntryPointAccessors
import java.util.Calendar
import kotlinx.coroutines.flow.first

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
        val ep          = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val medications = ep.medicationRepository().getActiveOnce()
        val logs        = ep.logRepository().getLogsForRangeOnce(todayStart(), todayEnd())

        val takenIds  = logs.filter { it.status == LogStatus.TAKEN }.map { it.medicationId }.toSet()
        val total     = medications.size
        val allDone   = total > 0 && takenIds.size >= total

        val cal = Calendar.getInstance()
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        // 找出今日尚未服用的药品及其下次服药时间
        // 对每个待服药品，解析 reminderTimes（HH:mm 逗号分隔列表）
        // 取今日最近的未来服药时间
        val nextDoseGroups = mutableMapOf<Int, MutableList<Pair<Long, String>>>() // 分钟数 → (id, name) 列表

        medications.filter { it.id !in takenIds }.forEach { med ->
            val earliest = parseReminderTimes(med.reminderTimes)
                .map { (h, m) -> h * 60 + m }
                .filter { it >= nowMinutes }
                .minOrNull()
                // 如果全部已过，也用主提醒时间（显示今日所有未服）
                ?: (med.reminderHour * 60 + med.reminderMinute)

            nextDoseGroups.getOrPut(earliest) { mutableListOf() }.add(med.id to med.name)
        }

        // 取最近的时间组
        val nextGroup = nextDoseGroups.minByOrNull { it.key }
        val widgetPrefs = runCatching { context.settingsDataStore.data.first() }
            .getOrElse { androidx.datastore.preferences.core.emptyPreferences() }
        val themeMode = widgetPrefs.medLogThemeMode()
        val useDynamicColor = widgetPrefs.medLogUseDynamicColor()
        val themePalette = widgetPrefs.medLogThemePalette()

        provideContent {
            MedLogGlanceTheme(themeMode = themeMode, useDynamicColor = useDynamicColor, themePalette = themePalette) {
                NextDoseContent(
                    total        = total,
                    allDone      = allDone,
                    nextMinutes  = nextGroup?.key,
                    nextMedPairs = nextGroup?.value ?: emptyList(),
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
    nextMedPairs: List<Pair<Long, String>>,
    nowMinutes: Int,
) {
    val size      = LocalSize.current
    val isCompact = size.width < 160.dp
    val ctx = LocalContext.current

    val hour   = (nextMinutes ?: 0) / 60
    val minute = (nextMinutes ?: 0) % 60
    val timeStr = "%02d:%02d".format(hour, minute)

    // 倒计时文字
    val diff = (nextMinutes ?: 0) - nowMinutes
    val countdownText = when {
        nextMinutes == null    -> ""
        diff <= 0              -> ctx.getString(R.string.widget_next_dose_now)
        diff < 60              -> ctx.getString(R.string.widget_next_dose_min_fmt, diff)
        diff < 120             -> ctx.getString(R.string.widget_next_dose_1h_min_fmt, diff % 60)
        else                   -> ctx.getString(R.string.widget_next_dose_h_fmt, diff / 60)
    }

    WidgetContainer(
        prominent = allDone,
        modifier = GlanceModifier
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment   = if (isCompact) Alignment.Vertical.CenterVertically else Alignment.Vertical.Top,
        horizontalAlignment = if (isCompact) Alignment.Horizontal.CenterHorizontally else Alignment.Horizontal.Start,
    ) {
        when {
            total == 0 -> {
                // 无计划
                if (isCompact) {
                    WidgetEmptyState(
                        icon = R.drawable.ic_symbol_medication,
                        title = ctx.getString(R.string.widget_no_plan),
                        compact = true,
                    )
                } else {
                    WidgetHeader(
                        icon = R.drawable.ic_symbol_schedule,
                        title = ctx.getString(R.string.widget_next_dose_title),
                        trailing = "--",
                    )
                    Spacer(GlanceModifier.height(10.dp))
                    WidgetEmptyState(
                        icon = R.drawable.ic_symbol_add_to_home_screen,
                        title = ctx.getString(R.string.widget_no_plan_today),
                        body = ctx.getString(R.string.widget_add_prompt),
                    )
                }
            }
            allDone -> {
                // 全部完成
                if (isCompact) {
                    WidgetIconBadge(icon = R.drawable.ic_symbol_check_circle, prominent = true, size = 44.dp, iconSize = 26.dp)
                    Spacer(GlanceModifier.height(6.dp))
                    Text(ctx.getString(R.string.widget_today_done_label), style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onTertiaryContainer))
                } else {
                    WidgetHeader(
                        icon = R.drawable.ic_symbol_check_circle,
                        title = ctx.getString(R.string.widget_next_dose_title),
                        trailing = ctx.getString(R.string.widget_goal_done),
                        prominent = true,
                    )
                    Spacer(GlanceModifier.height(10.dp))
                    WidgetEmptyState(
                        icon = R.drawable.ic_symbol_check_circle,
                        title = ctx.getString(R.string.widget_all_done_msg),
                    )
                }
            }
            nextMinutes == null -> {
                // 有药但没有未来时间（不应发生）
                if (!isCompact) {
                    Text(ctx.getString(R.string.widget_next_dose_title), style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
                    Spacer(GlanceModifier.height(8.dp))
                }
                Text(ctx.getString(R.string.widget_next_dose_no_pending), style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant))
            }
            isCompact -> {
                // 2×2：大号时间 + 第一个药品名（仅 1 种）或薯品总数（多种）
                WidgetIconBadge(icon = R.drawable.ic_symbol_schedule, size = 36.dp, iconSize = 20.dp)
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    timeStr,
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    if (nextMedPairs.size == 1)
                        nextMedPairs.first().second
                    else
                        ctx.resources.getQuantityString(R.plurals.widget_next_dose_count_fmt, nextMedPairs.size, nextMedPairs.size),
                    style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurface),
                )
            }
            else -> {
                // 4×2：完整显示 + 打卡按钮
                // 标题行（F 型）
                WidgetHeader(
                    icon = R.drawable.ic_symbol_schedule,
                    title = ctx.getString(R.string.widget_next_dose_title),
                    trailing = timeStr,
                )
                Spacer(GlanceModifier.height(4.dp))
                // 倒计时（次要信息）
                Text(
                    countdownText,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface),
                )
                Spacer(GlanceModifier.height(6.dp))
                // 药品列表 + ✓ 打卡按钮（行之间插入细分隔线）
                nextMedPairs.take(1).forEachIndexed { idx, (medId, name) ->
                    if (idx > 0) {
                        Spacer(
                            GlanceModifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(GlanceTheme.colors.outline),
                        )
                    }
                    Row(
                        modifier          = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Vertical.CenterVertically,
                    ) {
                        Text(
                            "· $name",
                            style    = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurface),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        WidgetActionButton(
                            label = ctx.getString(R.string.widget_action_btn),
                            action = actionRunCallback<MarkTakenAction>(
                                actionParametersOf(MarkTakenAction.medIdKey to medId),
                            ),
                        )
                    }
                }
                if (nextMedPairs.size > 1) {
                    Text(
                        ctx.resources.getQuantityString(R.plurals.widget_next_dose_remaining_fmt, nextMedPairs.size - 1, nextMedPairs.size - 1),
                        style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    )
                }
            }
        }
    }
}
