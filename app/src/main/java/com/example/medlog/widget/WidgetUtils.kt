package com.example.medlog.widget

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
