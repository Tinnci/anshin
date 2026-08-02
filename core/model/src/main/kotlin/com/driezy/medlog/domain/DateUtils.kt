package com.driezy.medlog.domain

import java.time.Clock
import java.time.LocalDate

/** 一天的毫秒数 */
const val ONE_DAY_MS = 86_400_000L

/** 7 天的毫秒数 */
const val SEVEN_DAYS_MS = 7L * ONE_DAY_MS

/** 30 天的毫秒数 */
const val THIRTY_DAYS_MS = 30L * ONE_DAY_MS

/** 90 天的毫秒数 */
const val NINETY_DAYS_MS = 90L * ONE_DAY_MS

/** 今日零点的毫秒时间戳（SSOT：用于全局日期范围查询） */
fun todayStart(clock: Clock): Long = LocalDate.now(clock).atStartOfDay(clock.zone).toInstant().toEpochMilli()

/** 今日最后一毫秒的时间戳（含） */
fun todayEnd(clock: Clock): Long =
    LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli() - 1L

/** 今日完整时间范围 Pair(start, end)，便于日志查询 */
fun todayRange(clock: Clock): Pair<Long, Long> = todayStart(clock) to todayEnd(clock)

/** N 天前零点的毫秒时间戳 */
fun daysAgoStart(days: Int, clock: Clock): Long {
    require(days >= 0) { "days must not be negative" }
    return LocalDate.now(clock).minusDays(days.toLong()).atStartOfDay(clock.zone).toInstant().toEpochMilli()
}
