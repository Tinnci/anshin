package com.driezy.medlog.ui.navigation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationHierarchyTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun source(path: String) = File(projectRoot, path).readText()

    @Test
    fun `mobile top level navigation keeps five primary destinations`() {
        assertEquals(5, TOP_LEVEL_DESTINATIONS.size)
        assertFalse(TOP_LEVEL_DESTINATIONS.any { it.route == Route.Settings })
    }

    @Test
    fun `primary screens expose settings as a secondary action`() {
        val app = source("app/src/main/java/com/driezy/medlog/ui/MedLogApp.kt")
        val screens = listOf(
            "home/HomeScreen.kt" to "HomeScreen(",
            "history/HistoryScreen.kt" to "HistoryScreen(",
            "drugs/DrugsScreen.kt" to "DrugsScreen(",
            "symptom/SymptomDiaryScreen.kt" to "SymptomDiaryScreen(",
            "health/HealthScreen.kt" to "HealthScreen(",
        )

        screens.forEach { (relativePath, call) ->
            val screen = source("app/src/main/java/com/driezy/medlog/ui/screen/$relativePath")
            assertTrue("$relativePath should accept a settings action.", screen.contains("onOpenSettings: () -> Unit"))
            assertTrue("$relativePath should render the settings action.", screen.contains("settings_action_open"))
            assertTrue("MedLogApp should wire settings navigation for $call.", app.contains("$call") && app.contains("onOpenSettings = { navController.navigate(Route.Settings) }"))
        }
    }
}
