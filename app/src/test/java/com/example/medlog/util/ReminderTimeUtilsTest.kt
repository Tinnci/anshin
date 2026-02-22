package com.example.medlog.util

import com.example.medlog.data.model.TimePeriod
import com.example.medlog.data.repository.SettingsPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 纯 JVM 单元测试：[ReminderTimeUtils]
 *
 * - [ReminderTimeUtils.adjustTime]：分钟偏移 + 跨零点边界处理
 * - [ReminderTimeUtils.timePeriodToReminderTime]：11 个 TimePeriod 枚举的映射正确性
 */
class ReminderTimeUtilsTest {

    // ── 测试用 SettingsPreferences（使用数据类默认值：wake=7:00, breakfast=8:00,
    //    lunch=12:00, dinner=18:00, bed=22:00）──────────────────────────────────
    private val defaultPrefs = SettingsPreferences()

    // ─── adjustTime ─────────────────────────────────────────────────────────

    @Test
    fun `adjustTime zero delta returns same time`() {
        assertEquals("08:00", ReminderTimeUtils.adjustTime(8, 0, 0))
    }

    @Test
    fun `adjustTime negative delta subtracts minutes correctly`() {
        assertEquals("07:40", ReminderTimeUtils.adjustTime(8, 10, -30))
    }

    @Test
    fun `adjustTime crosses midnight backwards`() {
        // 00:00 - 120 min → total=-120, -120/60=-2 → h=((-2)%24+24)%24=22, m=0 → "22:00"
        assertEquals("22:00", ReminderTimeUtils.adjustTime(0, 0, -120))
    }

    @Test
    fun `adjustTime crosses midnight forwards`() {
        // 23:50 + 15 min = 00:05
        assertEquals("00:05", ReminderTimeUtils.adjustTime(23, 50, 15))
    }

    @Test
    fun `adjustTime exactly midnight result is 00-00`() {
        // 23:50 + 10 = 00:00
        assertEquals("00:00", ReminderTimeUtils.adjustTime(23, 50, 10))
    }

    @Test
    fun `adjustTime formats single-digit hour with leading zero`() {
        assertEquals("07:45", ReminderTimeUtils.adjustTime(8, 0, -15))
    }

    // ─── timePeriodToReminderTime ────────────────────────────────────────────

    @Test
    fun `EXACT returns empty string`() {
        assertEquals("", ReminderTimeUtils.timePeriodToReminderTime(TimePeriod.EXACT, defaultPrefs))
    }

    @Test
    fun `AFTERNOON returns hardcoded 15-00`() {
        assertEquals("15:00", ReminderTimeUtils.timePeriodToReminderTime(TimePeriod.AFTERNOON, defaultPrefs))
    }

    @Test
    fun `MORNING returns wake time`() {
        // defaultPrefs: wakeHour=7, wakeMinute=0
        assertEquals("07:00", ReminderTimeUtils.timePeriodToReminderTime(TimePeriod.MORNING, defaultPrefs))
    }

    @Test
    fun `BEDTIME returns bed time`() {
        // defaultPrefs: bedHour=22, bedMinute=0
        assertEquals("22:00", ReminderTimeUtils.timePeriodToReminderTime(TimePeriod.BEDTIME, defaultPrefs))
    }

    @Test
    fun `BEFORE_BREAKFAST is 15 minutes before breakfast`() {
        // breakfast 08:00 - 15 = 07:45
        assertEquals("07:45", ReminderTimeUtils.timePeriodToReminderTime(TimePeriod.BEFORE_BREAKFAST, defaultPrefs))
    }

    @Test
    fun `AFTER_BREAKFAST is 15 minutes after breakfast`() {
        // breakfast 08:00 + 15 = 08:15
        assertEquals("08:15", ReminderTimeUtils.timePeriodToReminderTime(TimePeriod.AFTER_BREAKFAST, defaultPrefs))
    }

    @Test
    fun `BEFORE_LUNCH is 15 minutes before lunch`() {
        // lunch 12:00 - 15 = 11:45
        assertEquals("11:45", ReminderTimeUtils.timePeriodToReminderTime(TimePeriod.BEFORE_LUNCH, defaultPrefs))
    }

    @Test
    fun `AFTER_LUNCH is 15 minutes after lunch`() {
        // lunch 12:00 + 15 = 12:15
        assertEquals("12:15", ReminderTimeUtils.timePeriodToReminderTime(TimePeriod.AFTER_LUNCH, defaultPrefs))
    }

    @Test
    fun `BEFORE_DINNER is 15 minutes before dinner`() {
        // dinner 18:00 - 15 = 17:45
        assertEquals("17:45", ReminderTimeUtils.timePeriodToReminderTime(TimePeriod.BEFORE_DINNER, defaultPrefs))
    }

    @Test
    fun `AFTER_DINNER is 15 minutes after dinner`() {
        // dinner 18:00 + 15 = 18:15
        assertEquals("18:15", ReminderTimeUtils.timePeriodToReminderTime(TimePeriod.AFTER_DINNER, defaultPrefs))
    }

    @Test
    fun `EVENING is 60 minutes before bed`() {
        // bed 22:00 - 60 = 21:00
        assertEquals("21:00", ReminderTimeUtils.timePeriodToReminderTime(TimePeriod.EVENING, defaultPrefs))
    }

    @Test
    fun `MORNING respects custom wake time`() {
        val prefs = defaultPrefs.copy(wakeHour = 6, wakeMinute = 30)
        assertEquals("06:30", ReminderTimeUtils.timePeriodToReminderTime(TimePeriod.MORNING, prefs))
    }

    @Test
    fun `BEDTIME respects custom bed time`() {
        val prefs = defaultPrefs.copy(bedHour = 23, bedMinute = 15)
        assertEquals("23:15", ReminderTimeUtils.timePeriodToReminderTime(TimePeriod.BEDTIME, prefs))
    }

    @Test
    fun `EVENING wraps correctly when bed time is midnight`() {
        // bed 00:00 - 60 min = total -60; -60/60=-1 → hour wraps to 23, min=0 → 23:00
        val prefs = defaultPrefs.copy(bedHour = 0, bedMinute = 0)
        assertEquals("23:00", ReminderTimeUtils.timePeriodToReminderTime(TimePeriod.EVENING, prefs))
    }
}
