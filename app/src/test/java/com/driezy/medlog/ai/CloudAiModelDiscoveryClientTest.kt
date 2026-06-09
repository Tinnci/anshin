package com.driezy.medlog.ai

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAiModelDiscoveryClientTest {
    @Test
    fun `openai compatible discovery fetches models and detects image capable metadata`() = runTest {
        val http = RecordingAiHttpTransport(
            response = AiHttpResponse(
                code = 200,
                body = """
                    {
                      "data": [
                        { "id": "gpt-3.5-turbo" },
                        { "id": "gpt-4.1-mini", "modalities": ["text", "image"] },
                        { "id": "llava", "capabilities": { "vision": true } }
                      ]
                    }
                """.trimIndent(),
            ),
        )
        val client = CloudAiModelDiscoveryClient(http)

        val result = client.fetch(
            AiProviderConfig.OpenAiCompatible(
                baseUrl = "https://api.example.com/v1",
                model = "gpt-3.5-turbo",
                apiKey = "key",
                authMode = OpenAiAuthMode.API_KEY_HEADER,
                providerName = "ExampleAI",
            ),
        )

        assertTrue(result.isConnected)
        assertEquals("https://api.example.com/v1/models", http.lastRequest!!.url)
        assertEquals("key", http.lastRequest!!.headers["api-key"])
        assertEquals(listOf("gpt-3.5-turbo", "gpt-4.1-mini", "llava"), result.models.map { it.id })
        assertFalse(result.models[0].supportsImageInput)
        assertTrue(result.models[1].supportsImageInput)
        assertTrue(result.models[2].supportsImageInput)
        assertEquals("gpt-4.1-mini", result.selectBestModel(requireImageInput = true)?.id)
    }

    @Test
    fun `nvidia nim discovery uses hosted v1 models endpoint and detects vl models`() = runTest {
        val http = RecordingAiHttpTransport(
            response = AiHttpResponse(
                code = 200,
                body = """
                    {
                      "object": "list",
                      "data": [
                        {
                          "id": "nvidia/llama-3.1-nemotron-ultra-253b-v1",
                          "object": "model",
                          "owned_by": "nvidia"
                        },
                        {
                          "id": "nvidia/nemotron-nano-12b-v2-vl",
                          "object": "model",
                          "owned_by": "nvidia"
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )
        val client = CloudAiModelDiscoveryClient(http)

        val result = client.fetch(
            AiProviderConfig.OpenAiCompatible(
                baseUrl = "https://integrate.api.nvidia.com/v1",
                model = "nvidia/llama-3.1-nemotron-ultra-253b-v1",
                apiKey = "nvapi-key",
                authMode = OpenAiAuthMode.BEARER,
                providerName = "NVIDIA NIM APIs",
            ),
        )

        assertTrue(result.isConnected)
        assertEquals("https://integrate.api.nvidia.com/v1/models", http.lastRequest!!.url)
        assertEquals("Bearer nvapi-key", http.lastRequest!!.headers["Authorization"])
        assertEquals("NVIDIA NIM APIs", result.providerName)
        assertFalse(result.models[0].supportsImageInput)
        assertTrue(result.models[1].supportsImageInput)
        assertEquals("nvidia/nemotron-nano-12b-v2-vl", result.selectBestModel(requireImageInput = true)?.id)
    }

    @Test
    fun `model discovery reports failed connectivity without exposing the api key`() = runTest {
        val http = RecordingAiHttpTransport(
            response = AiHttpResponse(
                code = 401,
                body = """{"error":"bad key"}""",
            ),
        )
        val client = CloudAiModelDiscoveryClient(http)

        val result = client.fetch(
            AiProviderConfig.Mimo(
                apiKey = "secret-key",
                model = "mimo-v2.5-pro",
            ),
        )

        assertFalse(result.isConnected)
        assertEquals(401, result.statusCode)
        assertTrue(result.errorMessage!!.contains("HTTP 401"))
        assertFalse(result.errorMessage.contains("secret-key"))
    }

    @Test
    fun `mimo discovery probes chat completions and uses official catalog for image selection`() = runTest {
        val http = RecordingAiHttpTransport(
            response = AiHttpResponse(
                code = 200,
                body = """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""",
            ),
        )
        val client = CloudAiModelDiscoveryClient(http)

        val result = client.fetch(
            AiProviderConfig.Mimo(
                apiKey = "mimo-key",
                model = "mimo-v2.5-pro",
            ),
        )

        assertTrue(result.isConnected)
        assertEquals("https://api.xiaomimimo.com/v1/chat/completions", http.lastRequest!!.url)
        assertEquals("mimo-key", http.lastRequest!!.headers["api-key"])

        val body = Json.parseToJsonElement(http.lastRequest!!.body).jsonObject
        assertEquals("mimo-v2.5-pro", body["model"]!!.jsonPrimitive.content)
        assertEquals("1", body["max_completion_tokens"]!!.jsonPrimitive.content)
        assertEquals("mimo-v2.5", result.selectBestModel(requireImageInput = true)?.id)
    }

    @Test
    fun `anthropic discovery uses v1 models endpoint and required headers`() = runTest {
        val http = RecordingAiHttpTransport(
            response = AiHttpResponse(
                code = 200,
                body = """{"data":[{"id":"claude-sonnet-4-20250514"}]}""",
            ),
        )
        val client = CloudAiModelDiscoveryClient(http)

        val result = client.fetch(
            AiProviderConfig.Anthropic(
                apiKey = "anthropic-key",
                model = "claude-sonnet-4-20250514",
            ),
        )

        assertTrue(result.isConnected)
        assertEquals("https://api.anthropic.com/v1/models", http.lastRequest!!.url)
        assertEquals("anthropic-key", http.lastRequest!!.headers["x-api-key"])
        assertEquals("2023-06-01", http.lastRequest!!.headers["anthropic-version"])
        assertTrue(result.models.single().supportsImageInput)
    }

    private class RecordingAiHttpTransport(
        private val response: AiHttpResponse,
    ) : AiHttpTransport {
        var lastRequest: AiHttpRequest? = null

        override suspend fun post(request: AiHttpRequest): AiHttpResponse {
            lastRequest = request
            return response
        }

        override suspend fun get(request: AiHttpRequest): AiHttpResponse {
            lastRequest = request
            return response
        }
    }
}
