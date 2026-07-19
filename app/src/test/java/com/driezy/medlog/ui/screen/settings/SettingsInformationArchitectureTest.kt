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
            "settings_destination_bpx1",
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
        assertTrue("Settings home should expose reminder settings as a destination.", screen.contains("onNavigateToReminderSettings"))
        assertTrue("Settings home should expose intelligence settings as a destination.", screen.contains("onNavigateToIntelligenceSettings"))
        assertTrue("Settings home should expose BPX1 settings as a destination.", screen.contains("onNavigateToBpx1Settings"))
        assertTrue("Settings home should expose widget settings as a destination.", screen.contains("onNavigateToWidgetSettings"))
        assertTrue("Settings home should expose data settings as a destination.", screen.contains("onNavigateToDataSettings"))
    }

    @Test
    fun `settings home uses compact navigation rows instead of nested destination tiles`() {
        val dashboard = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsHomeDashboardContent.kt")

        assertTrue("Settings home should present destinations as navigation rows.", dashboard.contains("SettingsNavigationRow("))
        assertFalse("Settings home should not reintroduce nested destination tiles.", dashboard.contains("SettingsDestinationTile("))
        assertTrue("Reminder state should be represented on the dashboard.", dashboard.contains("settings_home_tile_reminders_status"))
        assertTrue("Intelligence state should be represented on the dashboard.", dashboard.contains("settings_home_tile_ai_status"))
        assertTrue("BPX1 maintenance should be represented on the dashboard.", dashboard.contains("settings_home_tile_bpx1_status"))
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
            "opencode",
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
        val rows = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsRowsComponents.kt")
        val display = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsDisplayOptions.kt")

        assertTrue("Navigation rows should bind button role on clickable.", rows.contains(".clickable(\n                role = Role.Button"))
        assertFalse("Navigation rows should not split clickable and button semantics.", rows.contains(".clickable(onClick = onClick)\n            .semantics { role = Role.Button }"))
        assertTrue("Palette chips should use selectable for radio behavior.", display.contains(".selectable("))
        assertFalse("Palette chips should not split clickable and radio semantics.", display.contains(".clickable(onClick = onClick)\n            .semantics { role = Role.RadioButton }"))
    }

    @Test
    fun `cloud api settings follow configuration order`() {
        val panel = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/CloudAiSettingsPanel.kt")
        val providerModel = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/CloudAiProviderModelSection.kt")
        val viewModel = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsViewModel.kt")
        val providerIndex = panel.indexOf("CloudAiProviderSection(")
        val keyIndex = panel.indexOf("ApiKeyManagementSection(")
        val endpointIndex = panel.indexOf("EndpointConfigSection(")
        val modelIndex = panel.indexOf("CloudAiModelSection(")
        val detailsIndex = panel.indexOf("ProviderDetailsDisclosureRow(")
        val featureIndex = panel.indexOf("CloudAiFeatureToggles(")

        assertTrue("Service should be selected before credentials.", providerIndex >= 0 && providerIndex < keyIndex)
        assertTrue("Provider detail disclosure should come before credentials for custom endpoints.", detailsIndex < keyIndex)
        assertTrue("Endpoint fields should come before credentials when a provider needs them.", endpointIndex < keyIndex)
        assertTrue("API key should be configured before model discovery.", keyIndex < modelIndex)
        assertTrue("Model discovery should come before upload feature switches.", modelIndex < featureIndex)
        assertFalse("ADK implementation details should not be a primary setting.", panel.contains("AdkAgentSection()"))
        assertFalse("Provider and model should not be merged into one section.", panel.contains("ProviderAndModelSection("))
        assertTrue("Model check should be gated by API key state.", providerModel.contains("uiState.cloudAiProviderHasApiKey"))
        val keySaveBody = viewModel.substringAfter("fun setCloudAiApiKey(").substringBefore("\n    fun setCurrentCloudAiApiKey")
        assertTrue(
            "Saving a manual API key should trigger model discovery.",
            keySaveBody.contains("refreshCloudAiModelsForCurrentSettings()"),
        )
        val keyImportBody = viewModel.substringAfter("fun importCloudAiApiKey(").substringBefore("\n    fun refreshCloudAiModels")
        assertTrue(
            "Importing an API key should trigger model discovery.",
            keyImportBody.contains("refreshCloudAiModelsForCurrentSettings()"),
        )
        assertTrue("The model selector should render detailed rows, not terse chips.", providerModel.contains("DiscoveredModelRows("))
        assertFalse("Model selection should not compress discovered models into chips.", providerModel.contains("DiscoveredModelChips("))
        assertTrue("Model rows should label model capability information.", providerModel.contains("settings_ai_model_capability_image"))
        assertTrue("Model rows should label context-window information when available.", providerModel.contains("settings_ai_model_context_window"))
        assertTrue("Model rows should surface provider descriptions when available.", providerModel.contains("model.description"))
    }

    @Test
    fun `cloud api provider details are collapsed until requested`() {
        val panel = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/CloudAiSettingsPanel.kt")

        assertTrue("Provider details should have local expanded state.", panel.contains("showProviderDetails"))
        assertTrue("Provider details should have a visible disclosure row.", panel.contains("ProviderDetailsDisclosureRow("))
        assertTrue(
            "Endpoint controls should render after provider details are opened.",
            panel.contains("visible = showProviderDetails && uiState.cloudAiProvider.needsCustomEndpoint"),
        )
    }

    @Test
    fun `cloud api keeps post key controls out of the initial path`() {
        val panel = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/CloudAiSettingsPanel.kt")

        assertTrue("Configured controls should be gated by saved key state.", panel.contains("val showConfiguredControls = uiState.cloudAiProviderHasApiKey"))
        assertTrue("Model controls should only appear after a key is saved.", panel.contains("visible = showConfiguredControls"))
        assertTrue("Provider details should appear before key entry when an endpoint is required.", panel.contains("visible = uiState.cloudAiProvider.needsCustomEndpoint"))
        assertTrue("Endpoint controls should stay behind the provider details disclosure.", panel.contains("visible = showProviderDetails && uiState.cloudAiProvider.needsCustomEndpoint"))
        assertTrue("Connection failure should open provider details for recovery.", panel.contains("uiState.cloudAiModelDiscoveryConnected == false"))
    }

    @Test
    fun `api key setup keeps manual paste and scan together`() {
        val apiKeySection = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/CloudAiApiKeySection.kt")
        val manualIndex = apiKeySection.indexOf("ManualApiKeyInput(")
        val importIndex = apiKeySection.indexOf("ApiKeyImportInput(")
        val scanIndex = apiKeySection.indexOf("settings_ai_api_key_import_scan")

        assertTrue("Manual key input should be present.", manualIndex >= 0)
        assertTrue("Paste import should be present.", importIndex >= 0)
        assertTrue("Scan action should be present.", scanIndex >= 0)
        assertTrue("Manual input should come before paste import.", manualIndex < importIndex)
        assertTrue("Scan action should live with import actions.", importIndex < scanIndex)
        assertFalse("API key setup should not hide input methods behind tabs.", apiKeySection.contains("SecondaryTabRow"))
    }

    @Test
    fun `settings deep sections have typed navigation routes`() {
        val destinations = source("app/src/main/java/com/driezy/medlog/ui/navigation/MedLogDestinations.kt")
        val app = source("app/src/main/java/com/driezy/medlog/ui/MedLogApp.kt")

        listOf(
            "SettingsReminders",
            "SettingsIntelligence",
            "SettingsCloudApi",
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
        assertTrue("Feature controls should live under Modules and medications.", screen.indexOf("SettingsModuleToggleCard(") > screen.indexOf("settings_group_modules_meds"))
        assertTrue("Archived medication controls should live under Modules and medications.", screen.indexOf("settings_card_meds") > screen.indexOf("settings_group_modules_meds"))
        assertFalse("The old mixed OCR and health group should not remain.", screen.contains("settings_group_ocr_health"))
    }

    @Test
    fun `cloud api setup is a child settings screen`() {
        val settingsScreen = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsScreen.kt")
        val intelligence = source("app/src/main/java/com/driezy/medlog/ui/screen/settings/SettingsIntelligenceContent.kt")

        assertTrue("Settings should expose a cloud API screen mode.", settingsScreen.contains("CLOUD_API(R.string.settings_ai_config_title)"))
        assertTrue("Settings should expose a cloud API screen composable.", settingsScreen.contains("fun CloudApiSettingsScreen("))
        val cloudApiModeIndex = settingsScreen.indexOf("SettingsScreenMode.CLOUD_API ->")
        val cloudApiContentIndex = settingsScreen.indexOf("CloudApiSettingsContent(", startIndex = cloudApiModeIndex)
        assertTrue(
            "Cloud API screen should render CloudAiSettingsPanel.",
            cloudApiModeIndex >= 0 && cloudApiContentIndex > cloudApiModeIndex,
        )
        assertTrue("Intelligence screen should navigate to cloud API settings.", intelligence.contains("onNavigateToCloudApiSettings"))
        assertTrue("Intelligence screen should use a navigation row for cloud API settings.", intelligence.contains("settings_ai_config_title"))
        assertFalse("Intelligence screen should not render the cloud API form inline.", intelligence.contains("CloudAiSettingsPanel("))
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
