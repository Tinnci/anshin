package com.driezy.medlog.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SystemBarStyleGuardTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun source(path: String) = File(projectRoot, path).readText()

    @Test
    fun `activities reapply system bar appearance when app theme changes`() {
        val mainActivity = source("app/src/main/java/com/driezy/medlog/ui/MainActivity.kt")
        val widgetSettings = source("app/src/main/java/com/driezy/medlog/widget/WidgetSettingsActivity.kt")

        listOf(
            "MainActivity" to mainActivity,
            "WidgetSettingsActivity" to widgetSettings,
        ).forEach { (name, text) ->
            assertTrue("$name should reapply system bars from Compose theme state.", text.contains("SideEffect {"))
            assertTrue(
                "$name should pass resolved darkTheme to system bars.",
                text.contains("applyMedLogSystemBars(darkTheme)"),
            )
        }
    }

    @Test
    fun `system bar helper forces icon contrast independently of OEM defaults`() {
        val helper = source("app/src/main/java/com/driezy/medlog/ui/theme/SystemBars.kt")

        assertTrue(helper.contains("enableEdgeToEdge()"))
        assertTrue(helper.contains("window.statusBarColor = Color.TRANSPARENT"))
        assertTrue(helper.contains("controller.isAppearanceLightStatusBars = !darkTheme"))
        assertTrue(helper.contains("controller.isAppearanceLightNavigationBars = !darkTheme"))
    }
}
