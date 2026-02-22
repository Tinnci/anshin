package com.example.medlog.ui.screen.history

import app.cash.turbine.test
import com.example.medlog.data.model.LogStatus
import com.example.medlog.data.model.MedicationLog
import com.example.medlog.data.repository.FakeLogRepository
import com.example.medlog.data.repository.FakeMedicationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth

/**
 * HistoryViewModel 单元测试（JVM，无 Android 依赖）。
 * 使用 FakeLogRepository + FakeMedicationRepository，
 * 通过 StandardTestDispatcher 控制协程执行顺序。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var logRepo: FakeLogRepository
    private lateinit var medRepo: FakeMedicationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        logRepo = FakeLogRepository()
        medRepo = FakeMedicationRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 辅助：为指定日期生成已服药日志（取当日 08:00 本地时间作为时间戳）────────────

    private fun takenLogAt(date: LocalDate, medId: Long = 1L): MedicationLog {
        val tsMs = date.atTime(8, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return MedicationLog(
            medicationId = medId,
            scheduledTimeMs = tsMs,
            status = LogStatus.TAKEN,
        )
    }

    private fun skippedLogAt(date: LocalDate, medId: Long = 1L): MedicationLog {
        val tsMs = date.atTime(8, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return MedicationLog(
            medicationId = medId,
            scheduledTimeMs = tsMs,
            status = LogStatus.SKIPPED,
        )
    }

    private fun buildViewModel() = HistoryViewModel(logRepo, medRepo)

    // ── 初始加载状态 ───────────────────────────────────────────────────────────

    @Test
    fun `initial state has isLoading=true and empty data`() = runTest {
        // ViewModel init triggers loadData(), but we haven't advanced dispatcher yet
        val vm = buildViewModel()
        assertEquals(true, vm.uiState.value.isLoading)
        assertEquals(0, vm.uiState.value.currentStreak)
    }

    // ── streak 计算 ───────────────────────────────────────────────────────────

    @Test
    fun `streak is 0 when no logs`() = runTest {
        logRepo.setLogs(emptyList())
        val vm = buildViewModel()

        vm.uiState.test {
            awaitItem() // initial loading state
            testScheduler.advanceUntilIdle()
            val state = awaitItem()
            assertEquals(0, state.currentStreak)
            assertEquals(0, state.longestStreak)
            assertEquals(false, state.isLoading)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `streak=3 when today and previous two days have taken logs`() = runTest {
        val today = LocalDate.now()
        logRepo.setLogs(listOf(
            takenLogAt(today),
            takenLogAt(today.minusDays(1)),
            takenLogAt(today.minusDays(2)),
        ))
        val vm = buildViewModel()

        vm.uiState.test {
            awaitItem() // loading
            testScheduler.advanceUntilIdle()
            val state = awaitItem()
            assertEquals(3, state.currentStreak)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `currentStreak resets at gap`() = runTest {
        val today = LocalDate.now()
        // today is present, yesterday is missing → streak = 1
        logRepo.setLogs(listOf(
            takenLogAt(today),
            takenLogAt(today.minusDays(2)),  // gap on yesterday
        ))
        val vm = buildViewModel()

        vm.uiState.test {
            awaitItem()
            testScheduler.advanceUntilIdle()
            val state = awaitItem()
            assertEquals(1, state.currentStreak)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `longestStreak finds longest run across history`() = runTest {
        val today = LocalDate.now()
        // run of 2 ending 15 days ago, run of 5 ending 3 days ago
        val days2 = (0..1).map { today.minusDays(15 + it.toLong()) }
        val days5 = (0..4).map { today.minusDays(3 + it.toLong()) }
        logRepo.setLogs((days2 + days5).map { takenLogAt(it) })
        val vm = buildViewModel()

        vm.uiState.test {
            awaitItem()
            testScheduler.advanceUntilIdle()
            val state = awaitItem()
            assertEquals(5, state.longestStreak)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `skipped logs do not count in streak`() = runTest {
        val today = LocalDate.now()
        logRepo.setLogs(listOf(
            takenLogAt(today),
            skippedLogAt(today.minusDays(1)),  // skipped → does not extend streak
            takenLogAt(today.minusDays(2)),
        ))
        val vm = buildViewModel()

        vm.uiState.test {
            awaitItem()
            testScheduler.advanceUntilIdle()
            val state = awaitItem()
            assertEquals(1, state.currentStreak)
            cancelAndConsumeRemainingEvents()
        }
    }

    // ── overallAdherence (30天坚持率) ────────────────────────────────────────

    @Test
    fun `overallAdherence is 1f when all logs are taken`() = runTest {
        val today = LocalDate.now()
        logRepo.setLogs((0..6).flatMap { offset ->
            listOf(
                takenLogAt(today.minusDays(offset.toLong())),
            )
        })
        val vm = buildViewModel()

        vm.uiState.test {
            awaitItem()
            testScheduler.advanceUntilIdle()
            val state = awaitItem()
            assertEquals(1.0f, state.overallAdherence, 0.001f)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `overallAdherence is 0_5 when half logs are taken`() = runTest {
        val today = LocalDate.now()
        logRepo.setLogs(listOf(
            takenLogAt(today),
            skippedLogAt(today),
        ))
        val vm = buildViewModel()

        vm.uiState.test {
            awaitItem()
            testScheduler.advanceUntilIdle()
            val state = awaitItem()
            assertEquals(0.5f, state.overallAdherence, 0.001f)
            cancelAndConsumeRemainingEvents()
        }
    }

    // ── selectDate / navigateMonthBy ─────────────────────────────────────────

    @Test
    fun `selectDate toggles selected date`() = runTest {
        logRepo.setLogs(emptyList())
        val vm = buildViewModel()
        testScheduler.advanceUntilIdle()

        val date = LocalDate.of(2024, 6, 15)
        vm.selectDate(date)
        assertEquals(date, vm.uiState.value.selectedDate)

        // Selecting same date again clears it
        vm.selectDate(date)
        assertNull(vm.uiState.value.selectedDate)
    }

    @Test
    fun `navigateMonthBy increments displayed month correctly`() = runTest {
        logRepo.setLogs(emptyList())
        val vm = buildViewModel()

        val initialMonth = vm.uiState.value.displayedMonth
        vm.navigateMonthBy(1)
        assertEquals(initialMonth.plusMonths(1), vm.uiState.value.displayedMonth)

        vm.navigateMonthBy(-2)
        assertEquals(initialMonth.minusMonths(1), vm.uiState.value.displayedMonth)
    }

    @Test
    fun `navigateMonthBy wraps around year boundary`() = runTest {
        logRepo.setLogs(emptyList())
        val vm = buildViewModel()

        // Navigate to January of the current year, then back one month → should be December of previous year
        val currentMonth = vm.uiState.value.displayedMonth
        val monthsToJan = currentMonth.monthValue - 1  // e.g. Feb → 1 step back
        vm.navigateMonthBy(-monthsToJan)               // now at January
        assertEquals(1, vm.uiState.value.displayedMonth.monthValue)

        vm.navigateMonthBy(-1)                         // one more step back → December
        assertEquals(12, vm.uiState.value.displayedMonth.monthValue)
        assertEquals(currentMonth.year - 1, vm.uiState.value.displayedMonth.year)
    }
}
