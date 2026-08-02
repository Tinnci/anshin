package com.driezy.medlog.feature.onboarding.application

import com.driezy.medlog.capability.reminders.application.ResyncRemindersUseCase
import com.driezy.medlog.data.model.RoutineSchedule
import com.driezy.medlog.data.model.RoutineTime
import com.driezy.medlog.data.repository.OnboardingPreferences
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.feature.onboarding.model.OnboardingDraft
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CompleteOnboardingUseCaseTest {
    @Test
    fun `completion saves draft then resyncs before exposing completion`() = runTest {
        val preferences: OnboardingPreferences = mock()
        val resync: ResyncRemindersUseCase = mock()
        val draft = OnboardingDraft(
            routineSchedule = RoutineSchedule(),
            enableSymptomDiary = true,
            enableDrugInteractionCheck = true,
            enableDrugDatabase = true,
            enableHealthModule = true,
            enableTimePeriodMode = true,
            themeMode = ThemeMode.SYSTEM,
        )

        CompleteOnboardingUseCase(preferences, resync)(draft)

        inOrder(preferences, resync) {
            verify(preferences).saveOnboardingDraft(draft)
            verify(resync).invoke(draft.routineSchedule)
            verify(preferences).updateHasSeenWelcome(true)
        }
    }

    @Test
    fun `failed plan rebuild leaves onboarding incomplete for a safe retry`() = runTest {
        val preferences: OnboardingPreferences = mock()
        val resync: ResyncRemindersUseCase = mock()
        val draft = OnboardingDraft(
            routineSchedule = RoutineSchedule(),
            enableSymptomDiary = true,
            enableDrugInteractionCheck = true,
            enableDrugDatabase = true,
            enableHealthModule = true,
            enableTimePeriodMode = true,
            themeMode = ThemeMode.SYSTEM,
        )
        whenever(resync.invoke(draft.routineSchedule)).thenThrow(IllegalStateException("database unavailable"))

        runCatching { CompleteOnboardingUseCase(preferences, resync)(draft) }

        verify(preferences).saveOnboardingDraft(draft)
        verify(preferences, never()).updateHasSeenWelcome(true)
    }

    @Test
    fun `invalid draft is rejected before any preference mutation`() = runTest {
        val preferences: OnboardingPreferences = mock()
        val resync: ResyncRemindersUseCase = mock()
        val draft = OnboardingDraft(
            routineSchedule = RoutineSchedule(breakfast = RoutineTime(7, 0)),
            enableSymptomDiary = true,
            enableDrugInteractionCheck = true,
            enableDrugDatabase = true,
            enableHealthModule = true,
            enableTimePeriodMode = true,
            themeMode = ThemeMode.SYSTEM,
        )

        runCatching { CompleteOnboardingUseCase(preferences, resync)(draft) }

        verify(preferences, never()).saveOnboardingDraft(draft)
        verify(resync, never()).invoke(draft.routineSchedule)
    }
}
