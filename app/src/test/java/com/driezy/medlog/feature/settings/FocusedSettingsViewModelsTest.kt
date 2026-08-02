package com.driezy.medlog.feature.settings

import com.driezy.medlog.capability.reminders.application.ReconcileRemindersUseCase
import com.driezy.medlog.capability.reminders.application.ResyncRemindersUseCase
import com.driezy.medlog.data.model.RoutineSchedule
import com.driezy.medlog.data.repository.FeaturePreferences
import com.driezy.medlog.data.repository.ReminderPreferenceState
import com.driezy.medlog.data.repository.ReminderPreferences
import com.driezy.medlog.domain.ReminderReconcileReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class FocusedSettingsViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-08-02T04:00:00Z"), ZoneId.of("Asia/Shanghai"))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `early reminder action persists preference and rebuilds reminder projection`() = runTest {
        val preferences: ReminderPreferences = mock()
        whenever(preferences.reminders).thenReturn(flowOf(reminderState()))
        val features: FeaturePreferences = mock()
        val resync: ResyncRemindersUseCase = mock()
        val reconcile: ReconcileRemindersUseCase = mock()
        val viewModel = SettingsReminderViewModel(preferences, features, resync, reconcile, clock)

        viewModel.onAction(SettingsUiAction.SetEarlyReminder(30))
        advanceUntilIdle()

        verify(preferences).updateEarlyReminderMinutes(30)
        verify(reconcile).all(ReminderReconcileReason.ROUTINE_CHANGED)
    }

    @Test
    fun `permission recovery requests a full projection reconciliation`() = runTest {
        val preferences: ReminderPreferences = mock()
        whenever(preferences.reminders).thenReturn(flowOf(reminderState()))
        val features: FeaturePreferences = mock()
        val resync: ResyncRemindersUseCase = mock()
        val reconcile: ReconcileRemindersUseCase = mock()
        val viewModel = SettingsReminderViewModel(preferences, features, resync, reconcile, clock)

        viewModel.onAction(SettingsUiAction.PermissionsRecovered)
        advanceUntilIdle()

        verify(reconcile).all(ReminderReconcileReason.SYSTEM_EVENT)
    }

    private fun reminderState() = ReminderPreferenceState(
        persistentReminder = false,
        persistentIntervalMinutes = 5,
        routineSchedule = RoutineSchedule(),
        travelMode = false,
        homeTimeZoneId = "",
        earlyReminderMinutes = 0,
        followUpReminderEnabled = false,
        followUpDelayMinutes = 15,
        followUpMaxCount = 1,
    )
}
