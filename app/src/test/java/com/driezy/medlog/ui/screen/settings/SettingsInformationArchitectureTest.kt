package com.driezy.medlog.ui.screen.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsInformationArchitectureTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun source(path: String) = File(projectRoot, path).readText()

    private fun settingsSources(): String =
        File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/screen/settings")
            .walkTopDown()
            .filter { it.extension == "kt" }
            .sortedBy { it.name }
            .joinToString("\n") { it.readText() }

    private fun settingsResourceText(): String =
        listOf(
            "app/src/main/res/values/strings.xml",
            "app/src/main/res/values/settings_ai_strings.xml",
            "app/src/main/res/values-en/strings.xml",
            "app/src/main/res/values-en/settings_ai_strings.xml",
        ).joinToString("\n") { path ->
            source(path)
                .lineSequence()
                .filter { it.contains("name=\"settings_") }
                .joinToString("\n")
        }

    @Test
    fun `settings home keeps lightweight groups and links to deep settings`() {
        val screen = settingsSources()
        val home = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsHomeModulesContent.kt")
        val expectedTokens = listOf(
            "settings_group_appearance_home",
            "settings_group_appearance_home_desc",
            "settings_home_dashboard_title",
            "settings_home_dashboard_desc",
            "settings_group_modules_meds",
            "settings_group_modules_meds_desc",
            "settings_destination_reminders",
            "settings_destination_intelligence",
            "settings_destination_widgets",
            "settings_destination_data_about",
        )

        expectedTokens.forEach { token ->
            assertTrue("Settings should contain top-level group $token.", screen.contains(token))
        }

        assertTrue(
            "Settings home should render the dashboard before module controls.",
            home.indexOf("SettingsHomeDashboard(") < home.indexOf("settings_group_modules_meds"),
        )
        assertTrue("Settings home should expose reminder settings as a tile.", screen.contains("onNavigateToReminderSettings"))
        assertTrue("Settings home should expose intelligence settings as a tile.", screen.contains("onNavigateToIntelligenceSettings"))
        assertTrue("Settings home should expose widget settings as a tile.", screen.contains("onNavigateToWidgetSettings"))
        assertTrue("Settings home should expose data settings as a tile.", screen.contains("onNavigateToDataSettings"))
    }

    @Test
    fun `settings home uses dashboard tiles instead of only navigation rows`() {
        val screen = settingsSources()

        assertTrue("Settings home should present destination tiles.", screen.contains("SettingsDestinationTile("))
        assertTrue("Reminder tile should be represented on the dashboard.", screen.contains("settings_home_tile_reminders_status"))
        assertTrue("Intelligence tile should be represented on the dashboard.", screen.contains("settings_home_tile_ai_status"))
        assertTrue("Module controls should use compact module toggles.", screen.contains("SettingsModuleToggleCard("))
    }

    @Test
    fun `settings copy uses nouns states and actions instead of adjectives`() {
        val resources = settingsResourceText()
        val bannedCopy = listOf(
            "超强",
            "高精度",
            "极速",
            "极简",
            "极致",
            "极小",
            "超低",
            "强悍",
            "精准",
            "特别",
            "推荐",
            "更强",
            "智能",
            "增强",
            "常用",
            "正常",
            "标准",
            "文艺",
            "更有气息",
            "清爽",
            "柔软",
            "浅绿",
            "薄雾",
            "静水",
            "暖光",
            "Ultra",
            "Best",
            "High accuracy",
            "Recommended",
            "stronger",
            "Enhanced",
            "enhanced",
            "common",
            "right settings",
            "normally",
            "standard import",
            "Compact",
            "Standard",
            "Roomy",
            "Poetic",
            "crafted",
            "Clean",
            "Soft",
            "Fresh",
            "Warm",
        )

        bannedCopy.forEach { word ->
            assertFalse("Settings copy should not use adjective '$word'.", resources.contains(word))
        }
    }

    @Test
    fun `module toggle cards expose the whole card as a material switch target`() {
        val moduleToggles = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsModuleToggleContent.kt")

        assertTrue("Module cards should let people tap the whole setting card.", moduleToggles.contains(".toggleable("))
        assertTrue("Module cards should use switch semantics for accessibility.", moduleToggles.contains("Role.Switch"))
        assertTrue("Module cards should expose a readable on or off state.", moduleToggles.contains("stateDescription"))
        assertTrue("Module cards should keep the visible enabled status.", moduleToggles.contains("R.string.settings_on"))
        assertTrue("Module cards should keep the visible disabled status.", moduleToggles.contains("R.string.settings_off"))
    }

    @Test
    fun `settings entry controls bind role to the interaction modifier`() {
        val dashboard = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsHomeDashboardContent.kt")
        val rows = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsRowsComponents.kt")
        val display = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsDisplayOptions.kt")

        assertTrue("Dashboard tiles should bind button role on clickable.", dashboard.contains(".clickable(\n                role = Role.Button"))
        assertFalse("Dashboard tiles should not split clickable and button semantics.", dashboard.contains(".clickable(onClick = onClick)\n            .semantics { role = Role.Button }"))
        assertTrue("Navigation rows should bind button role on clickable.", rows.contains(".clickable(\n                role = Role.Button"))
        assertFalse("Navigation rows should not split clickable and button semantics.", rows.contains(".clickable(onClick = onClick)\n            .semantics { role = Role.Button }"))
        assertTrue("Palette chips should use selectable for radio behavior.", display.contains(".selectable("))
        assertFalse("Palette chips should not split clickable and radio semantics.", display.contains(".clickable(onClick = onClick)\n            .semantics { role = Role.RadioButton }"))
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
        val screen = settingsSources()

        assertTrue("OCR controls should live under Intelligence.", screen.indexOf("settings_ocr_model_card_title") > screen.indexOf("settings_group_intelligence"))
        assertTrue("AI controls should live under Intelligence.", screen.indexOf("settings_ai_section_title") > screen.indexOf("settings_group_intelligence"))
        assertTrue("Feature controls should live under Modules and medications.", screen.indexOf("settings_card_features") > screen.indexOf("settings_group_modules_meds"))
        assertTrue("Archived medication controls should live under Modules and medications.", screen.indexOf("settings_card_meds") > screen.indexOf("settings_group_modules_meds"))
        assertFalse("The old mixed OCR and health group should not remain.", screen.contains("settings_group_ocr_health"))
    }

    @Test
    fun `settings implementation is split into focused files`() {
        val settingsDir = File(projectRoot, "app/src/main/java/com/driezy/medlog/ui/screen/settings")
        val files = settingsDir.listFiles { file -> file.extension == "kt" }?.map { it.name }.orEmpty()
        val screen = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsScreen.kt")
        val cloudAiPanel = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/CloudAiSettingsPanel.kt")

        assertTrue(files.contains("CloudAiSettingsPanel.kt"))
        assertTrue(files.contains("CloudAiApiKeySection.kt"))
        assertTrue(files.contains("CloudAiProviderModelSection.kt"))
        assertTrue(files.contains("CloudAiEndpointSection.kt"))
        assertTrue(files.contains("CloudAiEndpointPresetPicker.kt"))
        assertTrue(files.contains("CloudAiUsageSummaryCard.kt"))
        assertTrue(files.contains("SettingsCardComponents.kt"))
        assertTrue(files.contains("SettingsRowsComponents.kt"))
        assertFalse("CloudAiSettingsPanel should delegate endpoint UI.", cloudAiPanel.contains("settings_ai_endpoint_presets_title"))
        assertFalse("CloudAiSettingsPanel should delegate model discovery UI.", cloudAiPanel.contains("settings_ai_models_fetching"))
        assertFalse("CloudAiSettingsPanel should delegate API key import UI.", cloudAiPanel.contains("settings_ai_api_key_import_label"))
        assertFalse("SettingsScreen should delegate mode-specific content.", screen.contains("SettingsIntelligenceContent") && screen.contains("settings_ai_section_title"))
        assertFalse("SettingsScreen should delegate widget implementation.", screen.contains("WidgetPreviewCarousel("))
    }
}
