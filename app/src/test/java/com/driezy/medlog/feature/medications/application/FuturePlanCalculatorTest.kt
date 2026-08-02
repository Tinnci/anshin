package com.driezy.medlog.feature.medications.application

import com.driezy.medlog.data.model.Medication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class FuturePlanCalculatorTest {

    private val zone = ZoneId.of("Asia/Tokyo")
    private val calculator = FuturePlanCalculator(Clock.fixed(Instant.parse("2025-06-01T00:00:00Z"), zone))

    private fun day(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)

    private fun dayInstant(year: Int, month: Int, day: Int): Instant =
        day(year, month, day).atStartOfDay(zone).toInstant()

    private fun baseMed(
        name: String = "TestMed",
        frequencyType: String = "daily",
        frequencyInterval: Int = 1,
        frequencyDays: String = "1,2,3,4,5,6,7",
        reminderTimes: String = "08:00",
        startDate: Long = dayInstant(2025, 1, 1).toEpochMilli(),
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
        val from = dayInstant(2025, 6, 1)
        val items = calculator.calculate(listOf(med), days = 3, from = from, zoneId = zone)
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
        val from = dayInstant(2025, 6, 1)
        val items = calculator.calculate(listOf(med), days = 7, from = from, zoneId = zone)
        assertEquals(7, items.size)
    }

    // ── specific_days ────────────────────────────────────────────────────

    @Test
    fun `specific_days filters only matching weekdays`() {
        // 1=周一, 3=周三, 5=周五
        val med = baseMed(frequencyType = "specific_days", frequencyDays = "1,3,5")
        // 2025-06-02 是周一
        val from = dayInstant(2025, 6, 2)
        val items = calculator.calculate(listOf(med), days = 7, from = from, zoneId = zone)
        // 周一 6/2, 周三 6/4, 周五 6/6 = 3 天
        assertEquals(3, items.size)
    }

    // ── interval ─────────────────────────────────────────────────────────

    @Test
    fun `interval frequency produces doses every N days`() {
        val start = dayInstant(2025, 6, 1)
        val med = baseMed(frequencyType = "interval", frequencyInterval = 3, startDate = start.toEpochMilli())
        val from = start
        val items = calculator.calculate(listOf(med), days = 10, from = from, zoneId = zone)
        // 天 0, 3, 6, 9 → 4 条
        assertEquals(4, items.size)
    }

    // ── intervalHours ────────────────────────────────────────────────────

    @Test
    fun `intervalHours produces items at fixed hour intervals`() {
        val start = dayInstant(2025, 6, 1)
        val med = baseMed(intervalHours = 8, startDate = start.toEpochMilli())
        val items = calculator.calculate(listOf(med), days = 1, from = start, zoneId = zone)
        // 24h / 8h = 3 条（00:00, 08:00, 16:00）
        assertEquals(3, items.size)
    }

    // ── endDate ──────────────────────────────────────────────────────────

    @Test
    fun `endDate truncates plan`() {
        val start = dayInstant(2025, 6, 1)
        val end = dayInstant(2025, 6, 3) // 到 6/3（含）
        val med = baseMed(startDate = start.toEpochMilli(), endDate = end.toEpochMilli())
        val items = calculator.calculate(listOf(med), days = 7, from = start, zoneId = zone)
        // 6/1, 6/2, 6/3 → 3 条
        assertEquals(3, items.size)
    }

    // ── PRN / archived 跳过 ─────────────────────────────────────────────

    @Test
    fun `PRN medications are skipped`() {
        val med = baseMed(isPRN = true)
        val items = calculator.calculate(listOf(med), days = 7, from = dayInstant(2025, 6, 1), zoneId = zone)
        assertTrue(items.isEmpty())
    }

    @Test
    fun `archived medications are skipped`() {
        val med = baseMed(isArchived = true)
        val items = calculator.calculate(listOf(med), days = 7, from = dayInstant(2025, 6, 1), zoneId = zone)
        assertTrue(items.isEmpty())
    }

    // ── startDate 在未来 ─────────────────────────────────────────────────

    @Test
    fun `future startDate skips days before it`() {
        val start = dayInstant(2025, 6, 5)
        val med = baseMed(startDate = start.toEpochMilli())
        val from = dayInstant(2025, 6, 1)
        val items = calculator.calculate(listOf(med), days = 7, from = from, zoneId = zone)
        // 6/5, 6/6, 6/7 = 3 天
        assertEquals(3, items.size)
    }

    // ── 排序 ─────────────────────────────────────────────────────────────

    @Test
    fun `results are sorted by scheduledMs`() {
        val med1 = baseMed(name = "AM", reminderTimes = "08:00").copy(id = 1)
        val med2 = baseMed(name = "PM", reminderTimes = "20:00").copy(id = 2)
        val from = dayInstant(2025, 6, 1)
        val items = calculator.calculate(listOf(med2, med1), days = 1, from = from, zoneId = zone)
        assertTrue(items[0].scheduledAt < items[1].scheduledAt)
        assertEquals("AM", items[0].medication.name)
    }

    @Test
    fun `daily local time follows DST instead of adding fixed 24 hour milliseconds`() {
        val newYork = ZoneId.of("America/New_York")
        val from = LocalDate.of(2025, 3, 8).atStartOfDay(newYork).toInstant()
        val med = baseMed(
            reminderTimes = "08:00",
            startDate = LocalDate.of(2025, 3, 1).atStartOfDay(newYork).toInstant().toEpochMilli(),
        )

        val items = calculator.calculate(listOf(med), days = 2, from = from, zoneId = newYork)

        assertEquals(LocalTime.of(8, 0), items[0].scheduledAt.atZone(newYork).toLocalTime())
        assertEquals(LocalTime.of(8, 0), items[1].scheduledAt.atZone(newYork).toLocalTime())
        assertEquals(Duration.ofHours(23), Duration.between(items[0].scheduledAt, items[1].scheduledAt))
    }

    @Test
    fun `travel zone reinterprets clock schedules while fixed intervals remain instants`() {
        val from = dayInstant(2025, 6, 1)
        val med = baseMed(reminderTimes = "08:00")
        val losAngeles = ZoneId.of("America/Los_Angeles")

        val tokyoItem = calculator.calculate(listOf(med), 1, from, zone).single()
        val losAngelesItem = calculator.calculate(listOf(med), 1, from, losAngeles).single()

        assertEquals(LocalTime.of(8, 0), tokyoItem.scheduledAt.atZone(zone).toLocalTime())
        assertEquals(LocalTime.of(8, 0), losAngelesItem.scheduledAt.atZone(losAngeles).toLocalTime())
    }

    // ── 空列表 ───────────────────────────────────────────────────────────

    @Test
    fun `empty medication list returns empty`() {
        val items = calculator.calculate(emptyList(), days = 7)
        assertTrue(items.isEmpty())
    }
}
