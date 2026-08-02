package com.driezy.medlog.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import com.driezy.medlog.data.model.RoutineTime
import com.driezy.medlog.data.model.RoutineTimeSlot
import com.driezy.medlog.data.repository.AppearancePreferences
import com.driezy.medlog.data.repository.FeaturePreferences
import com.driezy.medlog.data.repository.ReminderPreferences
import com.driezy.medlog.feature.onboarding.application.CompleteOnboardingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
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

    private fun viewModel(handle: SavedStateHandle, completion: CompleteOnboardingUseCase = mock()) = WelcomeViewModel(
        savedStateHandle = handle,
        reminderPreferences = mock<ReminderPreferences>(),
        featurePreferences = mock<FeaturePreferences>(),
        appearancePreferences = mock<AppearancePreferences>(),
        completeOnboarding = completion,
    )
}
