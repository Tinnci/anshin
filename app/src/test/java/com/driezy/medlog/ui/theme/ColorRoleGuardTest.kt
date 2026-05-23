package com.driezy.medlog.ui.theme

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorRoleGuardTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun source(path: String) = File(projectRoot, path).readText()

    @Test
    fun `static Material color schemes provide current M3 color roles`() {
        val theme = source("app/src/main/java/com/driezy/medlog/ui/theme/Theme.kt")
        val color = source("app/src/main/java/com/driezy/medlog/ui/theme/Color.kt")
        val requiredRoleNames = listOf(
            "surfaceTint",
            "surfaceDim",
            "surfaceBright",
            "surfaceContainerLowest",
            "surfaceContainerLow",
            "surfaceContainer",
            "surfaceContainerHigh",
            "surfaceContainerHighest",
            "scrim",
            "primaryFixed",
            "primaryFixedDim",
            "onPrimaryFixed",
            "onPrimaryFixedVariant",
            "secondaryFixed",
            "secondaryFixedDim",
            "onSecondaryFixed",
            "onSecondaryFixedVariant",
            "tertiaryFixed",
            "tertiaryFixedDim",
            "onTertiaryFixed",
            "onTertiaryFixedVariant",
        )

        requiredRoleNames.forEach { role ->
            assertTrue("Theme.kt should assign $role in the static color schemes.", theme.contains("$role "))
            assertTrue(
                "Color.kt should define light and dark fallback values for $role.",
                color.contains("${role}Light") && color.contains("${role}Dark"),
            )
        }
    }

    @Test
    fun `custom visual states use Material color roles instead of standalone hardcoded colors`() {
        val forbidden = mapOf(
            "app/src/main/java/com/driezy/medlog/ui/theme/Color.kt" to "calendarWarning",
            "app/src/main/java/com/driezy/medlog/ui/screen/history/HistoryScreen.kt" to "calendarWarning",
            "app/src/main/java/com/driezy/medlog/ui/screen/detail/MedicationDetailScreen.kt" to "calendarWarning",
            "app/src/main/java/com/driezy/medlog/ui/screen/health/HealthScreen.kt" to "android.graphics.Color.GRAY",
            "app/src/main/java/com/driezy/medlog/ui/components/ViewfinderOverlay.kt" to "Color.Black.copy",
        )

        forbidden.forEach { (path, token) ->
            assertFalse("$path should not use $token.", source(path).contains(token))
        }
    }

    @Test
    fun `notifications and Glance widgets consume app Material color roles`() {
        val notificationHelper = source("app/src/main/java/com/driezy/medlog/notification/NotificationHelper.kt")
        val widgetTheme = source("app/src/main/java/com/driezy/medlog/widget/MedLogGlanceTheme.kt")
        val widgetFiles = listOf(
            "app/src/main/java/com/driezy/medlog/widget/MedLogWidget.kt",
            "app/src/main/java/com/driezy/medlog/widget/NextDoseWidget.kt",
            "app/src/main/java/com/driezy/medlog/widget/StreakWidget.kt",
        )

        assertFalse(
            "Notification colors should resolve from Material roles, not launcher resources.",
            notificationHelper.contains("ic_launcher_background"),
        )
        assertTrue(
            "Notification colors should resolve through a Material color-role helper.",
            notificationHelper.contains("notificationColor"),
        )
        assertTrue(
            "Glance widgets should have a project-owned Material color provider.",
            widgetTheme.contains("ColorProviders(") &&
                widgetTheme.contains("dynamicLightColorScheme") &&
                widgetTheme.contains("MedLogLightColorScheme"),
        )
        widgetFiles.forEach { path ->
            val text = source(path)
            assertTrue("$path should use MedLogGlanceTheme.", text.contains("MedLogGlanceTheme("))
            assertFalse("$path should not use a bare GlanceTheme.", text.contains("GlanceTheme {"))
        }
    }
}
