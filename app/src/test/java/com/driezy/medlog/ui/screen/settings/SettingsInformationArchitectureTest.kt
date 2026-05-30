package com.driezy.medlog.ui.screen.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsInformationArchitectureTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun source(path: String) = File(projectRoot, path).readText()

    @Test
    fun `settings home keeps lightweight groups and links to deep settings`() {
        val screen = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsScreen.kt")
        val expectedOrder = listOf(
            "settings_group_appearance_home",
            "settings_group_appearance_home_desc",
            "settings_group_modules_meds",
            "settings_group_modules_meds_desc",
            "settings_destination_reminders",
            "settings_destination_intelligence",
            "settings_destination_widgets",
            "settings_destination_data_about",
        )

        var cursor = -1
        expectedOrder.forEach { token ->
            val next = screen.indexOf(token)
            assertTrue("SettingsScreen should contain top-level group $token.", next >= 0)
            assertTrue("$token should appear after the previous top-level group.", next > cursor)
            cursor = next
        }

        assertTrue("Settings home should expose reminder settings as a navigation row.", screen.contains("onNavigateToReminderSettings"))
        assertTrue("Settings home should expose intelligence settings as a navigation row.", screen.contains("onNavigateToIntelligenceSettings"))
        assertTrue("Settings home should expose widget settings as a navigation row.", screen.contains("onNavigateToWidgetSettings"))
        assertTrue("Settings home should expose data settings as a navigation row.", screen.contains("onNavigateToDataSettings"))
    }

    @Test
    fun `settings deep sections have typed navigation routes`() {
        val destinations = source("app/src/main/java/com/driezy/medlog/ui/navigation/MedLogDestinations.kt")
        val app = source("app/src/main/java/com/driezy/medlog/ui/MedLogApp.kt")

        listOf(
            "SettingsReminders",
            "SettingsIntelligence",
            "SettingsWidgets",
            "SettingsData",
        ).forEach { route ->
            assertTrue("Route.$route should exist.", destinations.contains("data object $route"))
            assertTrue("Route.$route should be registered in NavHost.", app.contains("composable<Route.$route>"))
        }
    }

    @Test
    fun `intelligence and module management are separate settings cards`() {
        val screen = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsScreen.kt")

        assertTrue("OCR controls should live under Intelligence.", screen.indexOf("settings_ocr_model_card_title") > screen.indexOf("settings_group_intelligence"))
        assertTrue("AI controls should live under Intelligence.", screen.indexOf("settings_ai_section_title") > screen.indexOf("settings_group_intelligence"))
        assertTrue("Feature controls should live under Modules and medications.", screen.indexOf("settings_card_features") > screen.indexOf("settings_group_modules_meds"))
        assertTrue("Archived medication controls should live under Modules and medications.", screen.indexOf("settings_card_meds") > screen.indexOf("settings_group_modules_meds"))
        assertFalse("The old mixed OCR and health group should not remain.", screen.contains("settings_group_ocr_health"))
    }
}
