package com.example.medlog.domain

import com.example.medlog.data.model.Medication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class FuturePlanCalculatorTest {

    private val calculator = FuturePlanCalculator()
    private val tz = TimeZone.getTimeZone("Asia/Tokyo")

    // 辅助：创建某天零点的毫秒戳
    private fun dayMs(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(tz).apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun baseMed(
        name: String = "TestMed",
        frequencyType: String = "daily",
        frequencyInterval: Int = 1,
        frequencyDays: String = "1,2,3,4,5,6,7",
        reminderTimes: String = "08:00",
        startDate: Long = dayMs(2025, 1, 1),
        endDate: Long? = null,
        intervalHours: Int = 0,
        isPRN: Boolean = false,
        isArchived: Boolean = false,
    ) = Medication(
        id = 1,
        name = name,
        dose = 10.0,
        doseUnit = "mg",
        frequencyType = frequencyType,
        frequencyInterval = frequencyInterval,
        frequencyDays = frequencyDays,
        reminderTimes = reminderTimes,
        startDate = startDate,
        endDate = endDate,
        intervalHours = intervalHours,
        isPRN = isPRN,
        isArchived = isArchived,
    )

    // ── daily ────────────────────────────────────────────────────────────

    @Test
    fun `daily medication produces one item per day per slot`() {
        val med = baseMed(reminderTimes = "08:00,20:00")
        val from = dayMs(2025, 6, 1)
        val items = calculator.calculate(listOf(med), days = 3, fromMs = from, tz = tz)
        // 3 天 × 2 时间槽 = 6 条
        assertEquals(6, items.size)
        assertEquals(0, items[0].timeSlotIndex) // 08:00 6/1
        assertEquals(1, items[1].timeSlotIndex) // 20:00 6/1
        assertEquals("08:00", items[0].timeLabel)
        assertEquals("20:00", items[1].timeLabel)
    }

    @Test
    fun `daily medication single slot for 7 days`() {
        val med = baseMed()
        val from = dayMs(2025, 6, 1)
        val items = calculator.calculate(listOf(med), days = 7, fromMs = from, tz = tz)
        assertEquals(7, items.size)
    }

    // ── specific_days ────────────────────────────────────────────────────

    @Test
    fun `specific_days filters only matching weekdays`() {
        // 1=周一, 3=周三, 5=周五
        val med = baseMed(frequencyType = "specific_days", frequencyDays = "1,3,5")
        // 2025-06-02 是周一
        val from = dayMs(2025, 6, 2)
        val items = calculator.calculate(listOf(med), days = 7, fromMs = from, tz = tz)
        // 周一 6/2, 周三 6/4, 周五 6/6 = 3 天
        assertEquals(3, items.size)
    }

    // ── interval ─────────────────────────────────────────────────────────

    @Test
    fun `interval frequency produces doses every N days`() {
        val start = dayMs(2025, 6, 1)
        val med = baseMed(frequencyType = "interval", frequencyInterval = 3, startDate = start)
        val from = start
        val items = calculator.calculate(listOf(med), days = 10, fromMs = from, tz = tz)
        // 天 0, 3, 6, 9 → 4 条
        assertEquals(4, items.size)
    }

    // ── intervalHours ────────────────────────────────────────────────────

    @Test
    fun `intervalHours produces items at fixed hour intervals`() {
        val start = dayMs(2025, 6, 1)
        val med = baseMed(intervalHours = 8, startDate = start)
        val items = calculator.calculate(listOf(med), days = 1, fromMs = start, tz = tz)
        // 24h / 8h = 3 条（00:00, 08:00, 16:00）
        assertEquals(3, items.size)
    }

    // ── endDate ──────────────────────────────────────────────────────────

    @Test
    fun `endDate truncates plan`() {
        val start = dayMs(2025, 6, 1)
        val end = dayMs(2025, 6, 3) // 到 6/3（含）
        val med = baseMed(startDate = start, endDate = end)
        val items = calculator.calculate(listOf(med), days = 7, fromMs = start, tz = tz)
        // 6/1, 6/2, 6/3 → 3 条
        assertEquals(3, items.size)
    }

    // ── PRN / archived 跳过 ─────────────────────────────────────────────

    @Test
    fun `PRN medications are skipped`() {
        val med = baseMed(isPRN = true)
        val items = calculator.calculate(listOf(med), days = 7, fromMs = dayMs(2025, 6, 1), tz = tz)
        assertTrue(items.isEmpty())
    }

    @Test
    fun `archived medications are skipped`() {
        val med = baseMed(isArchived = true)
        val items = calculator.calculate(listOf(med), days = 7, fromMs = dayMs(2025, 6, 1), tz = tz)
        assertTrue(items.isEmpty())
    }

    // ── startDate 在未来 ─────────────────────────────────────────────────

    @Test
    fun `future startDate skips days before it`() {
        val start = dayMs(2025, 6, 5)
        val med = baseMed(startDate = start)
        val from = dayMs(2025, 6, 1)
        val items = calculator.calculate(listOf(med), days = 7, fromMs = from, tz = tz)
        // 6/5, 6/6, 6/7 = 3 天
        assertEquals(3, items.size)
    }

    // ── 排序 ─────────────────────────────────────────────────────────────

    @Test
    fun `results are sorted by scheduledMs`() {
        val med1 = baseMed(name = "AM", reminderTimes = "08:00").copy(id = 1)
        val med2 = baseMed(name = "PM", reminderTimes = "20:00").copy(id = 2)
        val from = dayMs(2025, 6, 1)
        val items = calculator.calculate(listOf(med2, med1), days = 1, fromMs = from, tz = tz)
        assertTrue(items[0].scheduledMs < items[1].scheduledMs)
        assertEquals("AM", items[0].medication.name)
    }

    // ── 空列表 ───────────────────────────────────────────────────────────

    @Test
    fun `empty medication list returns empty`() {
        val items = calculator.calculate(emptyList(), days = 7)
        assertTrue(items.isEmpty())
    }
}
