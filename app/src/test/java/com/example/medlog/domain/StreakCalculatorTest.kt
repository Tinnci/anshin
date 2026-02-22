package com.example.medlog.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {

    // ── currentStreak ─────────────────────────────────────────────────────────

    @Test
    fun `currentStreak returns 0 when no activity at all`() {
        val result = StreakCalculator.currentStreak(emptySet(), LocalDate.of(2024, 6, 15))
        assertEquals(0, result)
    }

    @Test
    fun `currentStreak counts consecutive days including today`() {
        val today = LocalDate.of(2024, 6, 15)
        val days = setOf(today.minusDays(2), today.minusDays(1), today)
        assertEquals(3, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun `currentStreak starts from yesterday when today is missing`() {
        val today = LocalDate.of(2024, 6, 15)
        val days = setOf(today.minusDays(3), today.minusDays(2), today.minusDays(1))
        assertEquals(3, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun `currentStreak breaks at gap`() {
        val today = LocalDate.of(2024, 6, 15)
        // today is present but day before today is missing → streak = 1
        val days = setOf(today.minusDays(5), today.minusDays(4), today)
        assertEquals(1, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun `currentStreak is 0 when yesterday and today are both missing`() {
        val today = LocalDate.of(2024, 6, 15)
        val days = setOf(today.minusDays(7), today.minusDays(6))
        assertEquals(0, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun `currentStreak handles single day being today`() {
        val today = LocalDate.of(2024, 6, 15)
        assertEquals(1, StreakCalculator.currentStreak(setOf(today), today))
    }

    // ── longestStreak ─────────────────────────────────────────────────────────

    @Test
    fun `longestStreak returns 0 for empty input`() {
        assertEquals(0, StreakCalculator.longestStreak(emptyList()))
    }

    @Test
    fun `longestStreak returns 1 for single element`() {
        val days = listOf(LocalDate.of(2024, 1, 5))
        assertEquals(1, StreakCalculator.longestStreak(days))
    }

    @Test
    fun `longestStreak counts perfect consecutive run`() {
        val base = LocalDate.of(2024, 6, 1)
        val days = (0..6).map { base.plusDays(it.toLong()) }
        assertEquals(7, StreakCalculator.longestStreak(days))
    }

    @Test
    fun `longestStreak returns longest of two runs separated by gap`() {
        val base = LocalDate.of(2024, 6, 1)
        // run of 3, gap, run of 5
        val days = (0..2).map { base.plusDays(it.toLong()) } +
                   (10..14).map { base.plusDays(it.toLong()) }
        assertEquals(5, StreakCalculator.longestStreak(days))
    }

    @Test
    fun `longestStreak handles unsorted input`() {
        val base = LocalDate.of(2024, 6, 1)
        val days = listOf(base.plusDays(2), base, base.plusDays(1))
        assertEquals(3, StreakCalculator.longestStreak(days))
    }

    @Test
    fun `longestStreak with duplicate dates counts correctly`() {
        val today = LocalDate.of(2024, 6, 10)
        // Set removes duplicates; pass as list to test sortedBy deduplication
        val days = listOf(today, today, today.plusDays(1))
        // longest should be 2 (today + tomorrow)
        assertEquals(2, StreakCalculator.longestStreak(days))
    }
}
