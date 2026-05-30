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
    fun `settings screen top level cards follow user task groups`() {
        val screen = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsScreen.kt")
        val expectedOrder = listOf(
            "settings_group_appearance_home",
            "settings_group_appearance_home_desc",
            "settings_group_reminders_routine",
            "settings_group_reminders_routine_desc",
            "settings_group_intelligence",
            "settings_group_intelligence_desc",
            "settings_group_modules_meds",
            "settings_group_modules_meds_desc",
            "settings_card_widgets",
            "settings_group_widgets_desc",
            "settings_group_data_about",
            "settings_group_data_about_desc",
        )

        var cursor = -1
        expectedOrder.forEach { token ->
            val next = screen.indexOf(token)
            assertTrue("SettingsScreen should contain top-level group $token.", next >= 0)
            assertTrue("$token should appear after the previous top-level group.", next > cursor)
            cursor = next
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
