package com.example.medlog.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

/**
 * [DateUtils] 单元测试。
 *
 * 覆盖：todayStart、todayEnd、todayRange、daysAgoStart 各函数的
 * 时间范围和边界正确性。纯 JVM，无 Android 运行时依赖。
 */
class DateUtilsTest {

    // ── 公共测试工具 ───────────────────────────────────────────────────────────

    /** 返回指定 Calendar 字段置零后的时间戳（midnight） */
    private fun midnightOf(cal: Calendar): Long = cal.apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // ── todayStart ────────────────────────────────────────────────────────────

    @Test
    fun `todayStart returns midnight of today`() {
        val expected = midnightOf(Calendar.getInstance())
        val actual = todayStart()
        // 允许 10ms 误差（函数执行时间）
        assertTrue("todayStart 应接近当日零点", Math.abs(actual - expected) < 10)
    }

    @Test
    fun `todayStart hour minute second ms are zero`() {
        val cal = Calendar.getInstance().apply { timeInMillis = todayStart() }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    // ── todayEnd ──────────────────────────────────────────────────────────────

    @Test
    fun `todayEnd is exactly 86400000 - 1 ms after todayStart`() {
        assertEquals(86_400_000L - 1L, todayEnd() - todayStart())
    }

    @Test
    fun `todayEnd hour is 23 minute 59 second 59`() {
        val cal = Calendar.getInstance().apply { timeInMillis = todayEnd() }
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, cal.get(Calendar.MINUTE))
        assertEquals(59, cal.get(Calendar.SECOND))
        assertEquals(999, cal.get(Calendar.MILLISECOND))
    }

    // ── todayRange ────────────────────────────────────────────────────────────

    @Test
    fun `todayRange first equals todayStart`() {
        val start = todayStart()
        val (rangeStart, _) = todayRange()
        // 两次调用间隔不超过 10ms
        assertTrue(Math.abs(rangeStart - start) < 10)
    }

    @Test
    fun `todayRange second equals todayEnd`() {
        val end = todayEnd()
        val (_, rangeEnd) = todayRange()
        assertTrue(Math.abs(rangeEnd - end) < 10)
    }

    @Test
    fun `todayRange spans exactly one day`() {
        val (start, end) = todayRange()
        assertEquals(86_400_000L - 1L, end - start)
    }

    // ── daysAgoStart ──────────────────────────────────────────────────────────

    @Test
    fun `daysAgoStart 0 equals todayStart`() {
        val start = todayStart()
        val ago0 = daysAgoStart(0)
        assertTrue(Math.abs(ago0 - start) < 10)
    }

    @Test
    fun `daysAgoStart 1 is exactly one full day before todayStart`() {
        val start = todayStart()
        val ago1 = daysAgoStart(1)
        assertEquals(86_400_000L, start - ago1)
    }

    @Test
    fun `daysAgoStart 7 is exactly seven days before todayStart`() {
        val start = todayStart()
        val ago7 = daysAgoStart(7)
        assertEquals(7 * 86_400_000L, start - ago7)
    }

    @Test
    fun `daysAgoStart returns midnight of that day`() {
        val cal = Calendar.getInstance().apply { timeInMillis = daysAgoStart(3) }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }
}
