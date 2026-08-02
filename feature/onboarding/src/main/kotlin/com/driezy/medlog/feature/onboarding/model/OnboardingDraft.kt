package com.driezy.medlog.feature.onboarding.model

import com.driezy.medlog.data.model.RoutineSchedule
import com.driezy.medlog.data.repository.ThemeMode

data class OnboardingDraft(
    val routineSchedule: RoutineSchedule,
    val enableSymptomDiary: Boolean,
    val enableDrugInteractionCheck: Boolean,
    val enableDrugDatabase: Boolean,
    val enableHealthModule: Boolean,
    val enableTimePeriodMode: Boolean,
    val themeMode: ThemeMode,
)

class OnboardingValidationException(message: String) : IllegalArgumentException(message)

/** Accepts ordinary and night-shift routines while rejecting ambiguous multi-day ordering. */
fun OnboardingDraft.requireValid() {
    val times = with(routineSchedule) { listOf(wake, breakfast, lunch, dinner, bed) }
    val minutes = times.map { it.hour * 60 + it.minute }
    val forwardGaps = minutes.zipWithNext { from, to -> (to - from + MINUTES_PER_DAY) % MINUTES_PER_DAY }
    if (forwardGaps.any { it == 0 } || forwardGaps.sum() >= MINUTES_PER_DAY) {
        throw OnboardingValidationException("Routine times must form one unambiguous wake-to-bed day")
    }
}

private const val MINUTES_PER_DAY = 24 * 60
