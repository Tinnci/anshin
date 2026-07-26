package com.driezy.medlog.ui.screen.welcome

import com.driezy.medlog.data.model.RoutineTimeSlot
import com.driezy.medlog.data.repository.SettingsPreferences
import com.driezy.medlog.data.repository.routineSchedule
import com.driezy.medlog.ui.screen.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class WelcomeUiStateTest {
    @Test
    fun `onboarding and settings expose the same configured slot schedule`() {
        val schedule = SettingsPreferences(
            wakeHour = 6,
            wakeMinute = 5,
            breakfastHour = 7,
            breakfastMinute = 10,
            lunchHour = 12,
            lunchMinute = 15,
            dinnerHour = 18,
            dinnerMinute = 20,
            bedHour = 23,
            bedMinute = 25,
        ).routineSchedule()
        val state = WelcomeUiState(
            routineSchedule = schedule,
        )
        val settingsState = SettingsUiState(routineSchedule = schedule)

        RoutineTimeSlot.entries.forEach { slot ->
            assertEquals(settingsState.routineSchedule[slot], state.routineSchedule[slot])
        }
    }
}
