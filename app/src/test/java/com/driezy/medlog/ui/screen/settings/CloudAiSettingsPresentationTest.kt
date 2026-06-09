package com.driezy.medlog.ui.screen.settings

import com.driezy.medlog.R
import com.driezy.medlog.ai.CloudAiEndpointPreset
import com.driezy.medlog.ai.CloudAiEndpointProtocol
import com.driezy.medlog.data.model.AiUsageFeature
import com.driezy.medlog.data.repository.AiUsageSummaryRow
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudAiSettingsPresentationTest {
    @Test
    fun `settings home overview counts permissions and missing cloud key as attention`() {
        val presentation = SettingsHomeOverviewPresentation.from(
            uiState = SettingsUiState(
                cloudAiEnabled = true,
                cloudAiProviderHasApiKey = false,
            ),
            canScheduleExactAlarms = false,
            canPostNotifications = false,
        )

        assertEquals(3, presentation.attentionCount)
        assertEquals(SettingsHomeStatusTone.WARNING, presentation.reminderTone)
        assertEquals(SettingsHomeStatusTone.WARNING, presentation.intelligenceTone)
    }

    @Test
    fun `settings home overview treats local intelligence as safe info state`() {
        val presentation = SettingsHomeOverviewPresentation.from(
            uiState = SettingsUiState(
                enableSymptomDiary = true,
                enableDrugInteractionCheck = true,
                enableDrugDatabase = false,
                enableHealthModule = true,
                cloudAiEnabled = false,
            ),
            canScheduleExactAlarms = true,
            canPostNotifications = true,
        )

        assertEquals(0, presentation.attentionCount)
        assertEquals(3, presentation.enabledModuleCount)
        assertEquals(SettingsHomeStatusTone.OK, presentation.reminderTone)
        assertEquals(SettingsHomeStatusTone.INFO, presentation.intelligenceTone)
    }

    @Test
    fun `disabled cloud AI is presented as off`() {
        val presentation = CloudAiSettingsPresentation.from(
            enabled = false,
            hasApiKey = false,
            supportsImageInput = true,
        )

        assertEquals(CloudAiSettingsVisualState.OFF, presentation.visualState)
        assertEquals(R.string.settings_ai_status_off, presentation.labelRes)
    }

    @Test
    fun `enabled cloud AI without key asks for setup`() {
        val presentation = CloudAiSettingsPresentation.from(
            enabled = true,
            hasApiKey = false,
            supportsImageInput = true,
        )

        assertEquals(CloudAiSettingsVisualState.NEEDS_KEY, presentation.visualState)
        assertEquals(R.string.settings_ai_status_needs_key, presentation.labelRes)
    }

    @Test
    fun `ready cloud AI with image support is fully ready`() {
        val presentation = CloudAiSettingsPresentation.from(
            enabled = true,
            hasApiKey = true,
            supportsImageInput = true,
        )

        assertEquals(CloudAiSettingsVisualState.READY, presentation.visualState)
        assertEquals(R.string.settings_ai_status_ready, presentation.labelRes)
    }

    @Test
    fun `ready cloud AI without image support is text only`() {
        val presentation = CloudAiSettingsPresentation.from(
            enabled = true,
            hasApiKey = true,
            supportsImageInput = false,
        )

        assertEquals(CloudAiSettingsVisualState.TEXT_ONLY, presentation.visualState)
        assertEquals(R.string.settings_ai_status_text_only, presentation.labelRes)
    }

    @Test
    fun `usage summary presentation totals internal counters without raw request details`() {
        val presentation = CloudAiUsageSummaryPresentation.from(
            listOf(
                AiUsageSummaryRow(
                    feature = AiUsageFeature.HEALTH_INSIGHT,
                    totalCount = 3,
                    successCount = 2,
                    fallbackCount = 0,
                    errorCount = 1,
                    cacheHitCount = 1,
                    lastUsedAt = 2_000L,
                    lastErrorCategory = "POLICY_VIOLATION",
                ),
                AiUsageSummaryRow(
                    feature = AiUsageFeature.IMAGE_OCR,
                    totalCount = 2,
                    successCount = 2,
                    fallbackCount = 0,
                    errorCount = 0,
                    cacheHitCount = 1,
                    lastUsedAt = 3_000L,
                    lastErrorCategory = null,
                ),
            ),
        )

        assertEquals(false, presentation.isEmpty)
        assertEquals(5, presentation.totalCount)
        assertEquals(4, presentation.successCount)
        assertEquals(1, presentation.errorCount)
        assertEquals(2, presentation.cacheHitCount)
        assertEquals("POLICY_VIOLATION", presentation.latestErrorCategory)
    }

    @Test
    fun `endpoint preset list filters by provider name and api`() {
        val rows = CloudAiEndpointPresetListPresentation.from(
            presets = listOf(
                CloudAiEndpointPreset(
                    id = "opencode",
                    name = "opencode",
                    api = "https://opencode.ai/zen/v1",
                    protocol = CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
                ),
                CloudAiEndpointPreset(
                    id = "deepseek",
                    name = "DeepSeek",
                    api = "https://api.deepseek.com",
                    protocol = CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
                ),
            ),
            query = "zen",
            currentBaseUrl = "",
            protocol = CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
        ).rows

        assertEquals(1, rows.size)
        assertEquals("opencode", rows.single().name)
        assertEquals("https://opencode.ai/zen/v1", rows.single().api)
    }

    @Test
    fun `endpoint preset list marks current base url`() {
        val rows = CloudAiEndpointPresetListPresentation.from(
            presets = listOf(
                CloudAiEndpointPreset(
                    id = "deepseek",
                    name = "DeepSeek",
                    api = "https://api.deepseek.com",
                    protocol = CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
                ),
            ),
            query = "",
            currentBaseUrl = "https://api.deepseek.com/",
            protocol = CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
        ).rows

        assertEquals(true, rows.single().selected)
    }

    @Test
    fun `endpoint preset list filters by protocol`() {
        val rows = CloudAiEndpointPresetListPresentation.from(
            presets = listOf(
                CloudAiEndpointPreset(
                    id = "openai",
                    name = "OpenAI",
                    api = "https://api.openai.com/v1",
                    protocol = CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
                ),
                CloudAiEndpointPreset(
                    id = "anthropic",
                    name = "Anthropic",
                    api = "https://api.anthropic.com",
                    protocol = CloudAiEndpointProtocol.ANTHROPIC,
                ),
            ),
            query = "",
            currentBaseUrl = "",
            protocol = CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
        ).rows

        assertEquals(listOf("openai"), rows.map { it.id })
    }

    @Test
    fun `endpoint preset list features nvidia nim before the full alphabetical list`() {
        val presentation = CloudAiEndpointPresetListPresentation.from(
            presets = listOf(
                CloudAiEndpointPreset(
                    id = "abacus",
                    name = "Abacus",
                    api = "https://routellm.abacus.ai/v1",
                    protocol = CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
                ),
                CloudAiEndpointPreset(
                    id = "nvidia-nim",
                    name = "NVIDIA NIM APIs",
                    api = "https://integrate.api.nvidia.com/v1",
                    protocol = CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
                ),
                CloudAiEndpointPreset(
                    id = "openai",
                    name = "OpenAI",
                    api = "https://api.openai.com/v1",
                    protocol = CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
                ),
            ),
            query = "",
            currentBaseUrl = "",
            protocol = CloudAiEndpointProtocol.OPENAI_COMPATIBLE,
        )

        assertEquals(listOf("nvidia-nim", "openai"), presentation.featuredRows.map { it.id })
        assertEquals(listOf("NVIDIA NIM APIs", "OpenAI", "Abacus"), presentation.rows.map { it.name })
    }

    @Test
    fun `api key import presentation previews matched provider and model`() {
        val presentation = CloudAiApiKeyImportPresentation.from(
            """
            NVIDIA_API_KEY=nvapi-preview
            NVIDIA_MODEL=meta/llama-3.1-8b-instruct
            """.trimIndent(),
        )

        assertEquals(true, presentation.canImport)
        assertEquals("NVIDIA NIM APIs", presentation.providerName)
        assertEquals("meta/llama-3.1-8b-instruct", presentation.model)
    }
}
