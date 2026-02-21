package com.example.medlog.widget

import java.util.Calendar

/** 解析 "HH:mm,HH:mm,..." 字符串为 (小时, 分钟) 对列表 */
internal fun parseReminderTimes(timesStr: String): List<Pair<Int, Int>> =
    timesStr.split(",").mapNotNull { token ->
        val parts = token.trim().split(":")
        if (parts.size >= 2) {
            val h = parts[0].toIntOrNull() ?: return@mapNotNull null
            val m = parts[1].toIntOrNull() ?: return@mapNotNull null
            Pair(h, m)
        } else null
    }

/** 返回今日零点的毫秒时间戳 */
internal fun todayStart(): Long =
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/** 返回今日最后一毫秒的时间戳 */
internal fun todayEnd(): Long = todayStart() + 86_400_000L - 1

/** 返回 N 天前零点的毫秒时间戳 */
internal fun daysAgoStart(days: Int): Long =
    Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -days)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
