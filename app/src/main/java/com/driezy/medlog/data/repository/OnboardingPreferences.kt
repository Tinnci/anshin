package com.driezy.medlog.data.repository

import com.driezy.medlog.feature.onboarding.model.OnboardingDraft
import kotlinx.coroutines.flow.Flow

data class OnboardingPreferenceState(val hasSeenWelcome: Boolean)

interface OnboardingPreferences {
    val onboarding: Flow<OnboardingPreferenceState>
    suspend fun updateHasSeenWelcome(seen: Boolean)
    suspend fun saveOnboardingDraft(draft: OnboardingDraft)
}
