package com.driezy.medlog.domain

import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.OpenAiCompatibleCloudAuthMode
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedImportPayloadCodecTest {
    @Test
    fun `detects existing medication plan QR payload`() {
        val encoded = PlanExportCodec.encode(
            listOf(
                Medication(
                    name = "阿司匹林",
                    dose = 1.0,
                    doseUnit = "片",
                    timePeriod = "exact",
                    reminderTimes = "08:00",
                    reminderHour = 8,
                    reminderMinute = 0,
                ),
            ),
        )!!

        val payload = UnifiedImportPayloadCodec.decode(encoded)

        assertTrue(payload is UnifiedImportPayload.MedicationPlan)
        assertEquals("阿司匹林", (payload as UnifiedImportPayload.MedicationPlan).plan.meds.single().name)
    }

    @Test
    fun `decodes standard anshin API key payload`() {
        val encoded = apiKeyPayload(
            """
            {
              "v": 1,
              "app": "anshin",
              "kind": "cloud_ai_key",
              "provider": "nvidia-nim",
              "apiKey": "nvapi-secret",
              "model": "meta/llama-3.2-11b-vision-instruct"
            }
            """.trimIndent(),
        )

        val payload = UnifiedImportPayloadCodec.decode(encoded)

        assertTrue(payload is UnifiedImportPayload.CloudAiApiKey)
        val key = (payload as UnifiedImportPayload.CloudAiApiKey).key
        assertEquals(CloudAiProvider.OPENAI_COMPATIBLE, key.provider)
        assertEquals("NVIDIA NIM APIs", key.providerName)
        assertEquals("https://integrate.api.nvidia.com/v1", key.baseUrl)
        assertEquals(OpenAiCompatibleCloudAuthMode.BEARER, key.openAiAuthMode)
        assertEquals("nvapi-secret", key.apiKey)
        assertEquals("meta/llama-3.2-11b-vision-instruct", key.model)
    }

    @Test
    fun `matches NVIDIA env import to NIM endpoint`() {
        val payload = UnifiedImportPayloadCodec.decode(
            """
            NVIDIA_API_KEY=nvapi-from-env
            NVIDIA_MODEL=meta/llama-3.1-8b-instruct
            """.trimIndent(),
        )

        assertTrue(payload is UnifiedImportPayload.CloudAiApiKey)
        val key = (payload as UnifiedImportPayload.CloudAiApiKey).key
        assertEquals(CloudAiProvider.OPENAI_COMPATIBLE, key.provider)
        assertEquals("NVIDIA NIM APIs", key.providerName)
        assertEquals("https://integrate.api.nvidia.com/v1", key.baseUrl)
        assertEquals("nvapi-from-env", key.apiKey)
        assertEquals("meta/llama-3.1-8b-instruct", key.model)
    }

    @Test
    fun `parses OpenAI compatible env block with base url and model`() {
        val payload = UnifiedImportPayloadCodec.decode(
            """
            OPENAI_API_KEY=sk-test
            OPENAI_BASE_URL=https://api.openrouter.ai/api/v1
            OPENAI_MODEL=openai/gpt-4.1
            OPENAI_PROVIDER_NAME=OpenRouter
            """.trimIndent(),
        )

        assertTrue(payload is UnifiedImportPayload.CloudAiApiKey)
        val key = (payload as UnifiedImportPayload.CloudAiApiKey).key
        assertEquals(CloudAiProvider.OPENAI_COMPATIBLE, key.provider)
        assertEquals("OpenRouter", key.providerName)
        assertEquals("https://api.openrouter.ai/api/v1", key.baseUrl)
        assertEquals("openai/gpt-4.1", key.model)
        assertEquals("sk-test", key.apiKey)
    }

    @Test
    fun `returns unknown for unmatched scan text`() {
        val payload = UnifiedImportPayloadCodec.decode("hello world")

        assertTrue(payload is UnifiedImportPayload.Unknown)
    }

    private fun apiKeyPayload(json: String): String =
        UnifiedImportPayloadCodec.API_KEY_SCHEME +
            Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))
}
