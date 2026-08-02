package com.driezy.medlog.feature.onboarding.application

import com.driezy.medlog.capability.reminders.application.ResyncRemindersUseCase
import com.driezy.medlog.data.repository.OnboardingPreferences
import com.driezy.medlog.feature.onboarding.model.OnboardingDraft
import com.driezy.medlog.feature.onboarding.model.requireValid
import javax.inject.Inject

/** Persists onboarding as one preference edit and reconciles plans that depend on routine anchors. */
class CompleteOnboardingUseCase @Inject constructor(
    private val preferences: OnboardingPreferences,
    private val resyncReminders: ResyncRemindersUseCase,
) {
    suspend operator fun invoke(draft: OnboardingDraft) {
        draft.requireValid()
        preferences.saveOnboardingDraft(draft)
        resyncReminders(draft.routineSchedule)
        preferences.updateHasSeenWelcome(true)
    }
}
