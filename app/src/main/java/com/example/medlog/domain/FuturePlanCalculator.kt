package com.example.medlog.domain

import com.example.medlog.data.model.Medication
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 单条未来服药计划条目。
 *
 * @param medication   药品元数据
 * @param dayMs        当日零点毫秒时间戳（便于按天分组）
 * @param scheduledMs  本剂量的计划服药时间戳（精确到分钟）
 * @param timeSlotIndex 时间槽下标，对应 [Medication.reminderTimes] 的逗号分隔序号
 * @param timeLabel    可读时间字符串，例如 "08:00"
 */
data class FuturePlanItem(
    val medication: Medication,
    val dayMs: Long,
    val scheduledMs: Long,
    val timeSlotIndex: Int,
    val timeLabel: String,
)

/**
 * 未来服药计划计算器 — 纯函数，无副作用。
 *
 * 根据 [Medication] 列表及其 frequencyType / interval / specificDays / startDate / endDate
 * 计算未来 N 天的计划服药条目，按时间戳升序返回。
 *
 * **使用场景**：
 * - 主页"未来计划"预览
 * - 日历视图中的"待服药"标记
 * - 导出/分享计划
 *
 * 不依赖 Android 框架，可独立单元测试。
 */
@Singleton
class FuturePlanCalculator @Inject constructor() {

    /**
     * 计算 [medications] 在 [fromMs]..[fromMs + days*86400000) 时间范围内的所有计划条目。
     *
     * @param medications 活跃药品列表（PRN 和已归档药品会被跳过）
     * @param days        未来天数（含今天），默认 7
     * @param fromMs      起始时刻，默认当前时间（取其零点）
     * @param tz          时区，默认系统时区
     * @return 按 [FuturePlanItem.scheduledMs] 升序排列的条目列表
     */
    fun calculate(
        medications: List<Medication>,
        days: Int = 7,
        fromMs: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault(),
    ): List<FuturePlanItem> {
        val result = mutableListOf<FuturePlanItem>()
        val startOfDay = startOfDay(fromMs, tz)

        for (med in medications) {
            if (med.isPRN || med.isArchived) continue

            // 间隔给药：按 intervalHours 推进
            if (med.intervalHours > 0) {
                result += expandInterval(med, startOfDay, days, tz)
                continue
            }

            val times = med.reminderTimes.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            for (dayOffset in 0 until days) {
                val dayCal = Calendar.getInstance(tz).apply {
                    timeInMillis = startOfDay
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                }
                val dayMs = dayCal.timeInMillis

                // 止日期校验
                if (med.endDate != null && dayMs > med.endDate) break
                // 起始日期校验
                if (dayMs < startOfDay(med.startDate, tz)) continue

                if (!matchesFrequency(med, dayCal)) continue

                for ((slotIndex, timeStr) in times.withIndex()) {
                    val scheduledMs = resolveTime(dayCal, timeStr) ?: continue
                    result += FuturePlanItem(
                        medication = med,
                        dayMs = dayMs,
                        scheduledMs = scheduledMs,
                        timeSlotIndex = slotIndex,
                        timeLabel = timeStr,
                    )
                }
            }
        }

        return result.sortedBy { it.scheduledMs }
    }

    // ─── 内部辅助 ─────────────────────────────────────────────────────────

    /**
     * 判断 [cal] 所在日期是否符合药品频率要求。
     */
    private fun matchesFrequency(med: Medication, cal: Calendar): Boolean {
        return when (med.frequencyType) {
            "daily" -> true
            "interval" -> {
                val startDay = dayIndex(med.startDate, cal.timeZone)
                val currentDay = dayIndex(cal.timeInMillis, cal.timeZone)
                val diff = currentDay - startDay
                diff >= 0 && diff % med.frequencyInterval == 0
            }
            "specific_days" -> {
                val allowedDays = med.frequencyDays.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .map { if (it == 7) Calendar.SUNDAY else it + 1 }
                cal.get(Calendar.DAY_OF_WEEK) in allowedDays
            }
            else -> true
        }
    }

    /** 展开间隔给药在 [days] 天范围内的条目。 */
    private fun expandInterval(
        med: Medication,
        startOfRange: Long,
        days: Int,
        tz: TimeZone,
    ): List<FuturePlanItem> {
        val intervalMs = med.intervalHours * 3_600_000L
        if (intervalMs <= 0) return emptyList()
        val endOfRange = startOfRange + days.toLong() * ONE_DAY_MS

        val items = mutableListOf<FuturePlanItem>()
        // 从药品录入时间开始，按间隔步进
        var cursor = med.startDate
        // 快速跳到范围起点附近
        if (cursor < startOfRange) {
            val skippable = (startOfRange - cursor) / intervalMs
            cursor += skippable * intervalMs
        }
        while (cursor < endOfRange) {
            if (cursor >= startOfRange) {
                if (med.endDate != null && cursor > med.endDate) break
                val dayMs = startOfDay(cursor, tz)
                val cal = Calendar.getInstance(tz).apply { timeInMillis = cursor }
                val label = "%02d:%02d".format(
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                )
                items += FuturePlanItem(
                    medication = med,
                    dayMs = dayMs,
                    scheduledMs = cursor,
                    timeSlotIndex = 0,
                    timeLabel = label,
                )
            }
            cursor += intervalMs
        }
        return items
    }

    /** 给定零点 Calendar 和 "HH:mm" 字符串，返回该时刻的毫秒戳。 */
    private fun resolveTime(dayCal: Calendar, timeStr: String): Long? {
        val parts = timeStr.split(":").mapNotNull { it.trim().toIntOrNull() }
        if (parts.size < 2) return null
        return (dayCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, parts[0])
            set(Calendar.MINUTE, parts[1])
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** 将毫秒时间戳截断为零点。 */
    private fun startOfDay(ms: Long, tz: TimeZone): Long =
        Calendar.getInstance(tz).apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** 以天为单位的索引（用于间隔给药天数差计算）。 */
    private fun dayIndex(ms: Long, tz: TimeZone): Int =
        (startOfDay(ms, tz) / ONE_DAY_MS).toInt()
}
