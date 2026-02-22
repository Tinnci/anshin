package com.example.medlog.domain

import java.util.Calendar

/** 今日零点的毫秒时间戳（SSOT：用于全局日期范围查询） */
fun todayStart(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** 今日最后一毫秒的时间戳（含） */
fun todayEnd(): Long = todayStart() + 86_400_000L - 1L

/** 今日完整时间范围 Pair(start, end)，便于日志查询 */
fun todayRange(): Pair<Long, Long> = todayStart() to todayEnd()

/** N 天前零点的毫秒时间戳 */
fun daysAgoStart(days: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, -days)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
