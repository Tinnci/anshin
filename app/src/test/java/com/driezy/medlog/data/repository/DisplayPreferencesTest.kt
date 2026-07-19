package com.driezy.medlog.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayPreferencesTest {
    @Test
    fun `display preferences default to system font and standard scale`() {
        val prefs = SettingsPreferences()

        assertEquals(FontMode.SYSTEM, prefs.fontMode)
        assertEquals(AppTextScale.STANDARD, prefs.appTextScale)
        assertEquals(UiDensityScale.STANDARD, prefs.uiDensityScale)
        assertEquals(HomeHeroStyle.ACTION, prefs.homeHeroStyle)
    }

    @Test
    fun `invalid stored display preference names fall back to defaults`() {
        assertEquals(FontMode.SYSTEM, FontMode.fromStoredName("missing"))
        assertEquals(AppTextScale.STANDARD, AppTextScale.fromStoredName("missing"))
        assertEquals(UiDensityScale.STANDARD, UiDensityScale.fromStoredName("missing"))
        assertEquals(HomeHeroStyle.ACTION, HomeHeroStyle.fromStoredName("missing"))
    }
}
