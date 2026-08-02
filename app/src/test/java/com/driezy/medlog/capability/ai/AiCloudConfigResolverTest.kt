package com.driezy.medlog.capability.ai

import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.OpenAiCompatibleCloudAuthMode
import com.driezy.medlog.data.repository.SettingsPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCloudConfigResolverTest {

    @Test
    fun `default settings keep cloud ai off with mimo as default provider`() {
        val settings = SettingsPreferences()

        assertFalse(settings.cloudAiEnabled)
        assertFalse(settings.cloudAiImageAnalysisEnabled)
        assertFalse(settings.cloudAiHealthInsightsEnabled)
        assertTrue(settings.cloudAiWifiOnly)
        assertEquals(CloudAiProvider.MIMO, settings.cloudAiProvider)
        assertEquals("mimo-v2.5-pro", settings.cloudAiModel)
    }

    @Test
    fun `feature availability requires global opt in feature opt in and api key`() {
        val missingGlobal = SettingsPreferences(
            cloudAiImageAnalysisEnabled = true,
        )
        val missingFeature = SettingsPreferences(
            cloudAiEnabled = true,
        )
        val missingKey = SettingsPreferences(
            cloudAiEnabled = true,
            cloudAiImageAnalysisEnabled = true,
        )
        val ready = SettingsPreferences(
            cloudAiEnabled = true,
            cloudAiImageAnalysisEnabled = true,
            cloudAiModel = "mimo-v2.5",
            mimoCloudAiModel = "mimo-v2.5",
        )

        assertFalse(AiCloudConfigResolver.resolveImageAnalysis(missingGlobal, FakeKeys()).isAvailable)
        assertFalse(AiCloudConfigResolver.resolveImageAnalysis(missingFeature, FakeKeys()).isAvailable)
        assertFalse(AiCloudConfigResolver.resolveImageAnalysis(missingKey, FakeKeys()).isAvailable)
        assertTrue(
            AiCloudConfigResolver.resolveImageAnalysis(
                ready,
                FakeKeys(CloudAiProvider.MIMO to true),
            ).isAvailable,
        )
    }

    @Test
    fun `resolver builds provider config without exposing key in identity`() {
        val settings = SettingsPreferences(
            cloudAiEnabled = true,
            cloudAiHealthInsightsEnabled = true,
            cloudAiProvider = CloudAiProvider.ANTHROPIC,
            cloudAiModel = "claude-sonnet-4-20250514",
        )

        val availability = AiCloudConfigResolver.resolveHealthInsights(
            settings,
            FakeKeys(CloudAiProvider.ANTHROPIC to true),
        )
        val config = AiCloudConfigResolver.toProviderConfig(
            settings = settings,
            apiKey = "secret-key",
        )

        assertTrue(availability.isAvailable)
        assertEquals("Anthropic", availability.identity!!.provider)
        assertEquals("claude-sonnet-4-20250514", availability.identity.model)
        assertTrue(config is AiProviderConfig.Anthropic)
        assertFalse(availability.identity.toString().contains("secret-key"))
    }

    @Test
    fun `mimo token plan keys use singapore token plan endpoint`() {
        val config = AiCloudConfigResolver.toProviderConfig(
            settings = SettingsPreferences(cloudAiProvider = CloudAiProvider.MIMO),
            apiKey = "tp-s177x-example",
        )

        assertTrue(config is AiProviderConfig.Mimo)
        config as AiProviderConfig.Mimo
        assertEquals("https://token-plan-sgp.xiaomimimo.com/v1", config.baseUrl)
    }

    @Test
    fun `mimo custom endpoint overrides key inferred endpoint`() {
        val config = AiCloudConfigResolver.toProviderConfig(
            settings = SettingsPreferences(
                cloudAiProvider = CloudAiProvider.MIMO,
                mimoCloudAiBaseUrl = "https://custom.example.com/v1",
            ),
            apiKey = "tp-s177x-example",
        )

        assertTrue(config is AiProviderConfig.Mimo)
        config as AiProviderConfig.Mimo
        assertEquals("https://custom.example.com/v1", config.baseUrl)
    }

    @Test
    fun `anthropic compatible presets can override anthropic endpoint`() {
        val config = AiCloudConfigResolver.toProviderConfig(
            settings = SettingsPreferences(
                cloudAiProvider = CloudAiProvider.ANTHROPIC,
                anthropicCloudAiBaseUrl = "https://api.minimax.io/anthropic/v1",
            ),
            apiKey = "key",
        )

        assertTrue(config is AiProviderConfig.Anthropic)
        config as AiProviderConfig.Anthropic
        assertEquals("https://api.minimax.io/anthropic/v1", config.baseUrl)
    }

    @Test
    fun `openai compatible settings preserve custom endpoint and auth mode`() {
        val settings = SettingsPreferences(
            cloudAiEnabled = true,
            cloudAiImageAnalysisEnabled = true,
            cloudAiProvider = CloudAiProvider.OPENAI_COMPATIBLE,
            cloudAiModel = "gpt-4.1",
            openAiCompatibleBaseUrl = "https://api.example.com/v1",
            openAiCompatibleAuthMode = OpenAiCompatibleCloudAuthMode.API_KEY_HEADER,
            openAiCompatibleProviderName = "ExampleAI",
        )

        val config = AiCloudConfigResolver.toProviderConfig(settings, apiKey = "key")

        assertTrue(config is AiProviderConfig.OpenAiCompatible)
        config as AiProviderConfig.OpenAiCompatible
        assertEquals("https://api.example.com/v1", config.baseUrl)
        assertEquals("gpt-4.1", config.model)
        assertEquals(OpenAiAuthMode.API_KEY_HEADER, config.authMode)
        assertEquals("ExampleAI", config.providerName)
    }

    @Test
    fun `provider capabilities describe text image json and auth defaults`() {
        assertTrue(CloudAiProvider.MIMO.capabilities.supportsText)
        assertTrue(CloudAiProvider.MIMO.capabilities.supportsImageInput)
        assertTrue(CloudAiProvider.MIMO.capabilities.supportsJsonInstruction)
        assertTrue(CloudAiProvider.MIMO.capabilities.requiresApiKey)
        assertEquals(CloudAiAuthMode.API_KEY_HEADER, CloudAiProvider.MIMO.capabilities.defaultAuthMode)

        assertEquals(CloudAiAuthMode.GEMINI_QUERY_KEY, CloudAiProvider.GEMINI.capabilities.defaultAuthMode)
        assertEquals(CloudAiAuthMode.ANTHROPIC_X_API_KEY, CloudAiProvider.ANTHROPIC.capabilities.defaultAuthMode)
        assertEquals(CloudAiAuthMode.BEARER, CloudAiProvider.OPENAI_COMPATIBLE.capabilities.defaultAuthMode)
    }

    @Test
    fun `model capabilities can disable image input while keeping text insights available`() {
        val settings = SettingsPreferences(
            cloudAiEnabled = true,
            cloudAiImageAnalysisEnabled = true,
            cloudAiHealthInsightsEnabled = true,
            cloudAiProvider = CloudAiProvider.OPENAI_COMPATIBLE,
            cloudAiModel = "gpt-3.5-turbo",
            openAiCompatibleBaseUrl = "https://api.example.com/v1",
        )

        val capabilities = AiCloudConfigResolver.resolveCapabilities(settings)

        assertTrue(capabilities.supportsText)
        assertFalse(capabilities.supportsImageInput)
        assertTrue(
            AiCloudConfigResolver.resolveHealthInsights(
                settings,
                FakeKeys(CloudAiProvider.OPENAI_COMPATIBLE to true),
            ).isAvailable,
        )
        assertFalse(
            AiCloudConfigResolver.resolveImageAnalysis(
                settings,
                FakeKeys(CloudAiProvider.OPENAI_COMPATIBLE to true),
            ).isAvailable,
        )
        assertEquals(
            AiFeatureUnavailableReason.IMAGE_INPUT_UNSUPPORTED,
            AiCloudConfigResolver.resolveImageAnalysis(
                settings,
                FakeKeys(CloudAiProvider.OPENAI_COMPATIBLE to true),
            ).reason,
        )
    }

    @Test
    fun `mimo pro is text only while mimo v25 supports image input`() {
        val textOnly = SettingsPreferences(
            cloudAiProvider = CloudAiProvider.MIMO,
            cloudAiModel = "mimo-v2.5-pro",
        )
        val imageCapable = SettingsPreferences(
            cloudAiProvider = CloudAiProvider.MIMO,
            cloudAiModel = "mimo-v2.5",
        )

        assertFalse(AiCloudConfigResolver.resolveCapabilities(textOnly).supportsImageInput)
        assertTrue(AiCloudConfigResolver.resolveCapabilities(imageCapable).supportsImageInput)
    }

    @Test
    fun `resolver uses provider specific model instead of stale global model`() {
        val settings = SettingsPreferences(
            cloudAiEnabled = true,
            cloudAiHealthInsightsEnabled = true,
            cloudAiProvider = CloudAiProvider.GEMINI,
            cloudAiModel = "mimo-custom",
            geminiCloudAiModel = "gemini-2.5-pro",
        )

        val config = AiCloudConfigResolver.toProviderConfig(settings, apiKey = "key")
        val availability = AiCloudConfigResolver.resolveHealthInsights(
            settings,
            FakeKeys(CloudAiProvider.GEMINI to true),
        )

        assertTrue(config is AiProviderConfig.Gemini)
        config as AiProviderConfig.Gemini
        assertEquals("gemini-2.5-pro", config.model)
        assertEquals("gemini-2.5-pro", availability.identity!!.model)
    }

    @Test
    fun `capability checks use provider specific openai compatible model`() {
        val settings = SettingsPreferences(
            cloudAiEnabled = true,
            cloudAiImageAnalysisEnabled = true,
            cloudAiProvider = CloudAiProvider.OPENAI_COMPATIBLE,
            cloudAiModel = "mimo-custom",
            openAiCompatibleCloudAiModel = "gpt-3.5-turbo",
            openAiCompatibleBaseUrl = "https://api.example.com/v1",
        )

        val capabilities = AiCloudConfigResolver.resolveCapabilities(settings)

        assertFalse(capabilities.supportsImageInput)
        assertEquals(
            AiFeatureUnavailableReason.IMAGE_INPUT_UNSUPPORTED,
            AiCloudConfigResolver.resolveImageAnalysis(
                settings,
                FakeKeys(CloudAiProvider.OPENAI_COMPATIBLE to true),
            ).reason,
        )
    }

    private class FakeKeys(vararg entries: Pair<CloudAiProvider, Boolean>) : AiApiKeyAvailability {
        private val available = entries.toMap()

        override fun hasApiKey(provider: CloudAiProvider): Boolean = available[provider] == true
    }
}
