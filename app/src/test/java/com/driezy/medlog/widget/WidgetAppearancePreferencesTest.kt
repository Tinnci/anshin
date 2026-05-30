package com.driezy.medlog.widget

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAppearancePreferencesTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun source(path: String) = File(projectRoot, path).readText()

    @Test
    fun `widget appearance has independent theme color density and text preferences`() {
        val prefs = source("app/src/main/java/com/driezy/medlog/data/repository/UserPreferencesRepository.kt")

        assertTrue(prefs.contains("enum class WidgetThemeMode"))
        assertTrue(prefs.contains("enum class WidgetColorSource"))
        assertTrue(prefs.contains("enum class WidgetDensityScale"))
        assertTrue(prefs.contains("enum class WidgetTextScale"))
        assertTrue(prefs.contains("WIDGET_THEME_MODE"))
        assertTrue(prefs.contains("WIDGET_COLOR_SOURCE"))
        assertTrue(prefs.contains("WIDGET_PALETTE"))
        assertTrue(prefs.contains("WIDGET_DENSITY_SCALE"))
        assertTrue(prefs.contains("WIDGET_TEXT_SCALE"))
        assertTrue(prefs.contains("updateWidgetAppearance("))
    }

    @Test
    fun `glance theme reads widget appearance before app theme`() {
        val theme = source("app/src/main/java/com/driezy/medlog/widget/MedLogGlanceTheme.kt")
        val medLog = source("app/src/main/java/com/driezy/medlog/widget/MedLogWidget.kt")
        val nextDose = source("app/src/main/java/com/driezy/medlog/widget/NextDoseWidget.kt")
        val streak = source("app/src/main/java/com/driezy/medlog/widget/StreakWidget.kt")

        assertTrue(theme.contains("medLogWidgetAppearance"))
        assertTrue(theme.contains("WidgetThemeMode.APP"))
        assertTrue(theme.contains("WidgetColorSource.APP_THEME"))
        assertTrue(theme.contains("WidgetSizing"))
        assertTrue(medLog.contains("val appearance = widgetPrefs.medLogWidgetAppearance()"))
        assertTrue(nextDose.contains("val appearance = widgetPrefs.medLogWidgetAppearance()"))
        assertTrue(streak.contains("val appearance = widgetPrefs.medLogWidgetAppearance()"))
    }

    @Test
    fun `widget settings exposes appearance controls and refreshes all widgets`() {
        val settings = source("app/src/main/java/com/driezy/medlog/widget/WidgetSettingsActivity.kt")

        assertTrue(settings.contains("widgetThemeMode"))
        assertTrue(settings.contains("widgetColorSource"))
        assertTrue(settings.contains("widgetDensityScale"))
        assertTrue(settings.contains("widgetTextScale"))
        assertTrue(settings.contains("setWidgetAppearance("))
        assertTrue(settings.contains("refreshPlacedWidgets()"))
    }
}
