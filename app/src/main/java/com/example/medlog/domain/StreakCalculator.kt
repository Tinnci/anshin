package com.example.medlog.domain

import java.time.LocalDate

/**
 * 纯函数工具类：连续打卡 (streak) 计算。
 * 无 Android 依赖，可在 JVM 单元测试中直接调用。
 */
object StreakCalculator {

    /**
     * 计算以 [today] 结尾的当前 streak 天数。
     * 若今天不在 [daysWithActivity] 中，则从昨天开始向前计数（兼容"昨天已完成今天未服药"场景）。
     */
    fun currentStreak(
        daysWithActivity: Set<LocalDate>,
        today: LocalDate = LocalDate.now(),
    ): Int {
        val startDay = if (today in daysWithActivity) today else today.minusDays(1)
        var count = 0
        var cursor = startDay
        while (cursor in daysWithActivity) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /**
     * 计算 [daysWithActivity] 中历史最长连续天数。
     * 输入顺序无关（内部排序）。
     */
    fun longestStreak(daysWithActivity: Iterable<LocalDate>): Int {
        var longest = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in daysWithActivity.sortedBy { it }) {
            run = if (prev != null && d == prev.plusDays(1)) run + 1 else 1
            if (run > longest) longest = run
            prev = d
        }
        return longest
    }
}
