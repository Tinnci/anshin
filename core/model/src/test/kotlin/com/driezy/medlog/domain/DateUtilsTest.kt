package com.driezy.medlog.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * [DateUtils] 单元测试。
 *
 * 覆盖：todayStart、todayEnd、todayRange、daysAgoStart 各函数的
 * 时间范围和边界正确性。纯 JVM，无 Android 运行时依赖。
 */
class DateUtilsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-01-15T04:00:00Z"), zone)

    // ── todayStart ────────────────────────────────────────────────────────────

    @Test
    fun `todayStart returns midnight of today`() {
        assertEquals(Instant.parse("2026-01-14T16:00:00Z").toEpochMilli(), todayStart(clock))
    }

    @Test
    fun `todayStart hour minute second ms are zero`() {
        val start = Instant.ofEpochMilli(todayStart(clock)).atZone(zone)
        assertEquals(0, start.hour)
        assertEquals(0, start.minute)
        assertEquals(0, start.second)
        assertEquals(0, start.nano)
    }

    // ── todayEnd ──────────────────────────────────────────────────────────────

    @Test
    fun `todayEnd is exactly 86400000 - 1 ms after todayStart`() {
        assertEquals(86_400_000L - 1L, todayEnd(clock) - todayStart(clock))
    }

    @Test
    fun `todayEnd hour is 23 minute 59 second 59`() {
        val end = Instant.ofEpochMilli(todayEnd(clock)).atZone(zone)
        assertEquals(23, end.hour)
        assertEquals(59, end.minute)
        assertEquals(59, end.second)
        assertEquals(999_000_000, end.nano)
    }

    // ── todayRange ────────────────────────────────────────────────────────────

    @Test
    fun `todayRange first equals todayStart`() {
        val start = todayStart(clock)
        val (rangeStart, _) = todayRange(clock)
        assertEquals(start, rangeStart)
    }

    @Test
    fun `todayRange second equals todayEnd`() {
        val end = todayEnd(clock)
        val (_, rangeEnd) = todayRange(clock)
        assertEquals(end, rangeEnd)
    }

    @Test
    fun `todayRange spans exactly one day`() {
        val (start, end) = todayRange(clock)
        assertEquals(86_400_000L - 1L, end - start)
    }

    // ── daysAgoStart ──────────────────────────────────────────────────────────

    @Test
    fun `daysAgoStart 0 equals todayStart`() {
        val start = todayStart(clock)
        val ago0 = daysAgoStart(0, clock)
        assertEquals(start, ago0)
    }

    @Test
    fun `daysAgoStart 1 is exactly one full day before todayStart`() {
        val start = todayStart(clock)
        val ago1 = daysAgoStart(1, clock)
        assertEquals(86_400_000L, start - ago1)
    }

    @Test
    fun `daysAgoStart 7 is exactly seven days before todayStart`() {
        val start = todayStart(clock)
        val ago7 = daysAgoStart(7, clock)
        assertEquals(7 * 86_400_000L, start - ago7)
    }

    @Test
    fun `daysAgoStart returns midnight of that day`() {
        val start = Instant.ofEpochMilli(daysAgoStart(3, clock)).atZone(zone)
        assertEquals(0, start.hour)
        assertEquals(0, start.minute)
        assertEquals(0, start.second)
        assertEquals(0, start.nano)
    }

    @Test
    fun `today range follows a 23 hour daylight saving day`() {
        val zone = ZoneId.of("America/New_York")
        val clock = Clock.fixed(Instant.parse("2026-03-08T16:00:00Z"), zone)

        val (start, end) = todayRange(clock)

        assertEquals(23 * 60 * 60 * 1_000L - 1L, end - start)
    }

    @Test
    fun `daysAgoStart follows calendar days rather than fixed durations across daylight saving`() {
        val zone = ZoneId.of("America/New_York")
        val clock = Clock.fixed(Instant.parse("2026-03-09T16:00:00Z"), zone)

        assertEquals(23 * 60 * 60 * 1_000L, todayStart(clock) - daysAgoStart(1, clock))
    }
}
