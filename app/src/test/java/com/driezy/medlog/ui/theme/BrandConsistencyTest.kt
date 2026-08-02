package com.driezy.medlog.ui.theme

import com.driezy.medlog.data.repository.SettingsPreferences
import com.driezy.medlog.feature.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BrandConsistencyTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun source(path: String) = File(projectRoot, path).readText()

    @Test
    fun `default theme starts from the launcher icon palette`() {
        assertEquals(AnshinBrandTeal, primaryLight)
        assertEquals(AnshinBrandCream, brandSurfaceAccentLight)
        assertEquals(
            listOf(AnshinBrandTeal, AnshinBrandCream),
            ThemePalette.ANSHIN.previewColors(darkTheme = false).take(2),
        )
        assertFalse(SettingsPreferences().useDynamicColor)
        assertFalse(SettingsUiState().useDynamicColor)
    }

    @Test
    fun `launcher splash and welcome reuse the same brand assets`() {
        val colors = source("app/src/main/res/values/colors.xml")
        val launcherBackground = source("app/src/main/res/drawable/ic_launcher_background.xml")
        val launcherForeground = source("app/src/main/res/drawable/ic_launcher_foreground.xml")
        val shortcutAdd = source("app/src/main/res/drawable/ic_shortcut_add.xml")
        val shortcutToday = source("app/src/main/res/drawable/ic_shortcut_today.xml")
        val preferences = source(
            "app/src/main/java/com/driezy/medlog/data/repository/UserPreferencesRepository.kt",
        )
        val widgetTheme = source(
            "app/src/main/java/com/driezy/medlog/capability/widgets/MedLogGlanceTheme.kt",
        )
        val welcome = source(
            "app/src/main/java/com/driezy/medlog/feature/onboarding/WelcomeIntroPages.kt",
        )

        assertTrue(colors.contains("""name="brand_icon_background">#F7EBD8</color>"""))
        assertTrue(colors.contains("""name="brand_icon_foreground">#0B5F63</color>"""))
        assertTrue(launcherBackground.contains("@color/brand_icon_background"))
        assertTrue(launcherForeground.contains("@color/brand_icon_foreground"))
        assertTrue(shortcutAdd.contains("@color/brand_icon_foreground"))
        assertTrue(shortcutToday.contains("@color/brand_icon_foreground"))
        assertTrue(preferences.contains("prefs[USE_DYNAMIC_COLOR] ?: false"))
        assertTrue(widgetTheme.contains("USE_DYNAMIC_COLOR] ?: false"))
        assertTrue(welcome.contains("AnshinBrandMark"))
    }
}
