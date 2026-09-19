package com.driezy.medlog.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import com.driezy.medlog.data.model.RoutineSchedule
import com.driezy.medlog.data.model.RoutineTime
import com.driezy.medlog.data.model.RoutineTimeSlot
import com.driezy.medlog.data.repository.AppearancePreferenceState
import com.driezy.medlog.data.repository.AppearancePreferences
import com.driezy.medlog.data.repository.FeaturePreferenceState
import com.driezy.medlog.data.repository.FeaturePreferences
import com.driezy.medlog.data.repository.ReminderPreferenceState
import com.driezy.medlog.data.repository.ReminderPreferences
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.feature.onboarding.application.CompleteOnboardingUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `draft and page survive recreation through SavedStateHandle`() = runTest {
        val handle = SavedStateHandle(mapOf("welcome.draftInitialized" to true))
        val first = viewModel(handle)

        first.onAction(WelcomeUiAction.PageChanged(3))
        first.onAction(WelcomeUiAction.TimeChanged(RoutineTimeSlot.BED, RoutineTime(23, 15)))

        val recreated = viewModel(handle)
        assertEquals(3, recreated.uiState.value.pageIndex)
        assertEquals(RoutineTime(23, 15), recreated.uiState.value.routineSchedule.bed)
    }

    @Test
    fun `rapid duplicate submit invokes completion only once`() = runTest {
        val completion: CompleteOnboardingUseCase = mock()
        val viewModel = viewModel(
            SavedStateHandle(mapOf("welcome.draftInitialized" to true)),
            completion,
        )

        viewModel.onAction(WelcomeUiAction.Submit)
        viewModel.onAction(WelcomeUiAction.Submit)

        assertTrue(viewModel.uiState.value.isSaving)
        advanceUntilIdle()
        verify(completion, times(1)).invoke(any())
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `submit saves the draft at click time even if edits arrive before the coroutine runs`() = runTest {
        val completion: CompleteOnboardingUseCase = mock()
        val viewModel = viewModel(
            SavedStateHandle(mapOf("welcome.draftInitialized" to true)),
            completion,
        )
        viewModel.onAction(WelcomeUiAction.ThemeModeChanged(ThemeMode.DARK))

        viewModel.onAction(WelcomeUiAction.Submit)
        viewModel.onAction(WelcomeUiAction.ThemeModeChanged(ThemeMode.LIGHT))
        advanceUntilIdle()

        verify(completion).invoke(check { assertEquals(ThemeMode.DARK, it.themeMode) })
        assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `late hydration preserves user edits and their saved draft`() = runTest {
        val handle = SavedStateHandle()
        val appearanceReady = CompletableDeferred<Unit>()
        val viewModel = hydratingViewModel(handle, appearanceReady)
        runCurrent()

        viewModel.onAction(WelcomeUiAction.TimeChanged(RoutineTimeSlot.BED, RoutineTime(23, 15)))
        viewModel.onAction(WelcomeUiAction.SymptomDiaryChanged(false))
        viewModel.onAction(WelcomeUiAction.DrugInteractionChanged(false))
        viewModel.onAction(WelcomeUiAction.DrugDatabaseChanged(false))
        viewModel.onAction(WelcomeUiAction.HealthModuleChanged(false))
        viewModel.onAction(WelcomeUiAction.TimePeriodModeChanged(false))
        viewModel.onAction(WelcomeUiAction.ThemeModeChanged(ThemeMode.DARK))
        viewModel.onAction(WelcomeUiAction.PageChanged(3))
        val editedDraft = viewModel.uiState.value

        val recreated = hydratingViewModel(handle, appearanceReady)
        assertEquals(editedDraft, recreated.uiState.value)
        appearanceReady.complete(Unit)
        advanceUntilIdle()

        assertEquals(editedDraft, viewModel.uiState.value)
        assertEquals(editedDraft, recreated.uiState.value)
        assertEquals(editedDraft, viewModel(handle).uiState.value)
    }

    @Test
    fun `initial hydration loads preferences after page notifications without draft edits`() = runTest {
        val handle = SavedStateHandle()
        val appearanceReady = CompletableDeferred<Unit>()
        val viewModel = hydratingViewModel(handle, appearanceReady)
        runCurrent()

        assertEquals(WelcomeUiState(), viewModel.uiState.value)
        viewModel.onAction(WelcomeUiAction.PageChanged(0))
        viewModel.onAction(WelcomeUiAction.PageChanged(1))
        appearanceReady.complete(Unit)
        advanceUntilIdle()

        val expected = WelcomeUiState(
            pageIndex = 1,
            routineSchedule = RoutineSchedule(bed = RoutineTime(22, 45)),
            themeMode = ThemeMode.LIGHT,
        )
        assertEquals(expected, viewModel.uiState.value)
        assertEquals(expected, viewModel(handle).uiState.value)
    }

    private fun hydratingViewModel(
        handle: SavedStateHandle,
        appearanceReady: CompletableDeferred<Unit>,
    ): WelcomeViewModel {
        val reminders = mock<ReminderPreferenceState> {
            on { routineSchedule } doReturn RoutineSchedule(bed = RoutineTime(22, 45))
        }
        val features = FeaturePreferenceState(true, true, true, true, true)
        val appearance = mock<AppearancePreferenceState> {
            on { themeMode } doReturn ThemeMode.LIGHT
        }
        return WelcomeViewModel(
            savedStateHandle = handle,
            reminderPreferences = mock {
                on { this.reminders } doReturn flowOf(reminders)
            },
            featurePreferences = mock {
                on { this.features } doReturn flowOf(features)
            },
            appearancePreferences = mock {
                on { this.appearance } doReturn flow {
                    appearanceReady.await()
                    emit(appearance)
                }
            },
            completeOnboarding = mock(),
        )
    }

    private fun viewModel(handle: SavedStateHandle, completion: CompleteOnboardingUseCase = mock()) = WelcomeViewModel(
        savedStateHandle = handle,
        reminderPreferences = mock<ReminderPreferences>(),
        featurePreferences = mock<FeaturePreferences>(),
        appearancePreferences = mock<AppearancePreferences>(),
        completeOnboarding = completion,
    )
}
