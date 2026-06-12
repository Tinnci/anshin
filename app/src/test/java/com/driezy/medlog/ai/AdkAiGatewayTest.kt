package com.driezy.medlog.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdkAiGatewayTest {
    @Test
    fun `chat factory routes every provider through adk model gateway`() = runTest {
        val configs = listOf(
            AiProviderConfig.Mimo(apiKey = "mimo-key"),
            AiProviderConfig.Gemini(apiKey = "gemini-key"),
            AiProviderConfig.Anthropic(apiKey = "anthropic-key"),
            AiProviderConfig.OpenAiCompatible(
                baseUrl = "https://integrate.api.nvidia.com/v1",
                model = "meta/llama-3.1-70b-instruct",
                apiKey = "nvapi-key",
                providerName = "NVIDIA NIM APIs",
            ),
        )

        configs.zip(
            listOf(
                "mimo-v2.5-pro",
                "gemini-2.5-flash",
                "claude-sonnet-4-20250514",
                "meta/llama-3.1-70b-instruct",
            ),
        ).forEach { (config, expectedModel) ->
            val client = AiChatClientFactory.create(
                config = config,
                transport = StaticAiHttpTransport(),
            )

            assertTrue(
                "${config::class.simpleName} should use the ADK gateway.",
                client is AdkAiChatClient,
            )
            assertEquals(expectedModel, (client as AdkAiChatClient).model.name)
        }
    }

    private class StaticAiHttpTransport : AiHttpTransport {
        override suspend fun post(request: AiHttpRequest): AiHttpResponse =
            when {
                request.url.contains("generativelanguage.googleapis.com") ->
                    AiHttpResponse(
                        code = 200,
                        body = """{"candidates":[{"content":{"parts":[{"text":"ok"}]},"finishReason":"STOP"}]}""",
                    )
                request.url.contains("anthropic.com") ->
                    AiHttpResponse(
                        code = 200,
                        body = """{"content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn"}""",
                    )
                else ->
                    AiHttpResponse(
                        code = 200,
                        body = """{"choices":[{"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}""",
                    )
            }
    }
}
