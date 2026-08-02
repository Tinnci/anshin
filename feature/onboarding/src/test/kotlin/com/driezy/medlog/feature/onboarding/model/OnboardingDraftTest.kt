package com.driezy.medlog.feature.onboarding.model

import com.driezy.medlog.data.model.RoutineSchedule
import com.driezy.medlog.data.model.RoutineTime
import com.driezy.medlog.data.repository.ThemeMode
import org.junit.Assert.assertThrows
import org.junit.Test

class OnboardingDraftTest {
    @Test
    fun `valid night-shift routine can cross midnight`() {
        draft(
            RoutineSchedule(
                wake = RoutineTime(15, 0),
                breakfast = RoutineTime(16, 0),
                lunch = RoutineTime(21, 0),
                dinner = RoutineTime(2, 0),
                bed = RoutineTime(7, 0),
            ),
        ).requireValid()
    }

    @Test
    fun `ambiguous duplicate or multi-day routine ordering is rejected`() {
        assertThrows(OnboardingValidationException::class.java) {
            draft(RoutineSchedule(breakfast = RoutineTime(7, 0))).requireValid()
        }
        assertThrows(OnboardingValidationException::class.java) {
            draft(
                RoutineSchedule(
                    wake = RoutineTime(7, 0),
                    breakfast = RoutineTime(18, 0),
                    lunch = RoutineTime(8, 0),
                    dinner = RoutineTime(19, 0),
                    bed = RoutineTime(22, 0),
                ),
            ).requireValid()
        }
    }

    private fun draft(schedule: RoutineSchedule) = OnboardingDraft(
        routineSchedule = schedule,
        enableSymptomDiary = true,
        enableDrugInteractionCheck = true,
        enableDrugDatabase = true,
        enableHealthModule = true,
        enableTimePeriodMode = true,
        themeMode = ThemeMode.SYSTEM,
    )
}
