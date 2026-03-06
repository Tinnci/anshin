package com.example.medlog.notification

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * [computeNextTriggerPure] 纯函数测试 —— 验证闹钟调度核心算法。
 *
 * 所有测试使用固定 [nowMs] 和 [tz]，确保可确定性复现。
 */
class ComputeNextTriggerTest {

    private val tz = TimeZone.getTimeZone("Asia/Tokyo") // UTC+9
    // 固定 "现在" = 2025-06-15 10:30:00 JST（周日）
    private val nowMs: Long = Calendar.getInstance(tz).apply {
        set(2025, Calendar.JUNE, 15, 10, 30, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // ── daily ────────────────────────────────────────────────────────────

    @Test
    fun `daily future time today returns today`() {
        // 20:00 还没到 → 今天 20:00
        val result = computeNextTriggerPure("20:00", "daily", 1, "", null, tz, nowMs)
        assertNotNull(result)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = result!! }
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(20, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `daily past time today returns tomorrow`() {
        // 08:00 已过 → 明天 08:00
        val result = computeNextTriggerPure("08:00", "daily", 1, "", null, tz, nowMs)
        assertNotNull(result)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = result!! }
        assertEquals(16, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, cal.get(Calendar.HOUR_OF_DAY))
    }

    // ── interval ─────────────────────────────────────────────────────────

    @Test
    fun `interval 3 jumps forward 2 extra days from next occurrence`() {
        // 08:00 已过 → 明天 + (3-1) = 明天 + 2 天 = 6/18 08:00
        val result = computeNextTriggerPure("08:00", "interval", 3, "", null, tz, nowMs)
        assertNotNull(result)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = result!! }
        assertEquals(18, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `interval 1 behaves like daily`() {
        val result = computeNextTriggerPure("20:00", "interval", 1, "", null, tz, nowMs)
        val daily = computeNextTriggerPure("20:00", "daily", 1, "", null, tz, nowMs)
        assertEquals(daily, result)
    }

    // ── specific_days ────────────────────────────────────────────────────

    @Test
    fun `specific_days finds next matching weekday`() {
        // nowMs = 周日 (6/15). 周一=1, 周三=3, 周五=5
        // 20:00 今天（周日）不在列表中 → 查下一个周一 = 6/16 20:00
        val result = computeNextTriggerPure("20:00", "specific_days", 1, "1,3,5", null, tz, nowMs)
        assertNotNull(result)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = result!! }
        assertEquals(16, cal.get(Calendar.DAY_OF_MONTH)) // 周一
        assertEquals(20, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `specific_days today if today matches and time is future`() {
        // 周日=7 在列表中，20:00 未到
        val result = computeNextTriggerPure("20:00", "specific_days", 1, "7", null, tz, nowMs)
        assertNotNull(result)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = result!! }
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH)) // 今天周日
    }

    // ── endDate ──────────────────────────────────────────────────────────

    @Test
    fun `returns null when trigger exceeds endDate`() {
        // endDate = 今天 12:00 → 20:00 触发会在 endDate 之后
        val endMs = Calendar.getInstance(tz).apply {
            set(2025, Calendar.JUNE, 15, 12, 0, 0)
        }.timeInMillis
        // 但 20:00 是今天 → 15 日 20:00 > endMs(15 日 12:00) → null
        val result = computeNextTriggerPure("20:00", "daily", 1, "", endMs, tz, nowMs)
        assertNull(result)
    }

    @Test
    fun `returns trigger when within endDate`() {
        val endMs = Calendar.getInstance(tz).apply {
            set(2025, Calendar.JUNE, 20, 23, 59, 59)
        }.timeInMillis
        val result = computeNextTriggerPure("20:00", "daily", 1, "", endMs, tz, nowMs)
        assertNotNull(result)
    }

    // ── 边界 ─────────────────────────────────────────────────────────────

    @Test
    fun `malformed timeStr returns null`() {
        assertNull(computeNextTriggerPure("bad", "daily", 1, "", null, tz, nowMs))
        assertNull(computeNextTriggerPure("", "daily", 1, "", null, tz, nowMs))
    }

    @Test
    fun `specific_days with no matching days returns null`() {
        // nowMs = 周日, 08:00 已过 → 下一天是周一; 无任何天匹配 → null
        // 空字符串 → 没有 valid days
        val result = computeNextTriggerPure("08:00", "specific_days", 1, "", null, tz, nowMs)
        assertNull(result)
    }
}
