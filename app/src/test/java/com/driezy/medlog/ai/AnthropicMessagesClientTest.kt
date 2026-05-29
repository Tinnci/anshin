package com.driezy.medlog.ai

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicMessagesClientTest {

    @Test
    fun `anthropic client posts messages request with required headers and system field`() = runTest {
        val http = RecordingAiHttpTransport(
            AiHttpResponse(
                code = 200,
                body = """
                    {
                      "type": "message",
                      "role": "assistant",
                      "content": [
                        { "type": "text", "text": "第一条。" },
                        { "type": "text", "text": "第二条。" }
                      ],
                      "stop_reason": "end_turn",
                      "usage": { "input_tokens": 11, "output_tokens": 7 }
                    }
                """.trimIndent(),
            ),
        )
        val client = AiChatClientFactory.create(
            AiProviderConfig.Anthropic(apiKey = "anthropic-key", model = "claude-sonnet-4-20250514"),
            http,
        )

        val response = client.generate(
            AiChatRequest(
                messages = listOf(
                    AiChatMessage.system("你是健康数据识别助手。"),
                    AiChatMessage.user("识别这段文字。"),
                ),
                temperature = 0.2,
                maxOutputTokens = 300,
            ),
        )

        assertEquals("https://api.anthropic.com/v1/messages", http.lastRequest!!.url)
        assertEquals("anthropic-key", http.lastRequest!!.headers["x-api-key"])
        assertEquals("2023-06-01", http.lastRequest!!.headers["anthropic-version"])
        assertEquals("application/json", http.lastRequest!!.headers["Content-Type"])

        val body = Json.parseToJsonElement(http.lastRequest!!.body).jsonObject
        assertEquals("claude-sonnet-4-20250514", body["model"]!!.jsonPrimitive.content)
        assertEquals("300", body["max_tokens"]!!.jsonPrimitive.content)
        assertEquals("0.2", body["temperature"]!!.jsonPrimitive.content)
        assertEquals("你是健康数据识别助手。", body["system"]!!.jsonPrimitive.content)
        val messages = body["messages"]!!.jsonArray
        assertEquals(1, messages.size)
        assertEquals("user", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("识别这段文字。", messages[0].jsonObject["content"]!!.jsonPrimitive.content)

        assertEquals("第一条。第二条。", response.text)
        assertEquals("end_turn", response.finishReason)
        val usage = response.usage!!
        assertEquals(11, usage.promptTokens)
        assertEquals(7, usage.completionTokens)
        assertEquals(18, usage.totalTokens)
    }

    @Test
    fun `anthropic client sends image content block with base64 source`() = runTest {
        val http = RecordingAiHttpTransport(
            AiHttpResponse(
                code = 200,
                body = """{"content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn"}""",
            ),
        )
        val client = AiChatClientFactory.create(
            AiProviderConfig.Anthropic(apiKey = "key", model = "claude-sonnet-4-20250514"),
            http,
        )

        client.generate(
            AiChatRequest(
                messages = listOf(
                    AiChatMessage.user(
                        parts = listOf(
                            AiChatContentPart.text("识别图片读数。"),
                            AiChatContentPart.imageBytes(byteArrayOf(1, 2, 3), "image/png"),
                        ),
                    ),
                ),
            ),
        )

        val body = Json.parseToJsonElement(http.lastRequest!!.body).jsonObject
        val content = body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        assertEquals("text", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("image", content[1].jsonObject["type"]!!.jsonPrimitive.content)
        val source = content[1].jsonObject["source"]!!.jsonObject
        assertEquals("base64", source["type"]!!.jsonPrimitive.content)
        assertEquals("image/png", source["media_type"]!!.jsonPrimitive.content)
        assertEquals("AQID", source["data"]!!.jsonPrimitive.content)
    }

    @Test
    fun `anthropic client rejects tool use only response as non text`() = runTest {
        val http = RecordingAiHttpTransport(
            AiHttpResponse(
                code = 200,
                body = """
                    {
                      "content": [
                        { "type": "tool_use", "id": "toolu_1", "name": "extract", "input": {} }
                      ],
                      "stop_reason": "tool_use"
                    }
                """.trimIndent(),
            ),
        )
        val client = AiChatClientFactory.create(AiProviderConfig.Anthropic(apiKey = "key"), http)

        val error = runCatching { client.generate(AiChatRequest.user("ping")) }.exceptionOrNull()

        assertTrue(error is AiProviderException)
        assertTrue(error!!.message!!.contains("non-text"))
    }

    @Test
    fun `anthropic client keeps provider error status and body`() = runTest {
        val http = RecordingAiHttpTransport(
            AiHttpResponse(code = 400, body = """{"type":"error","error":{"type":"invalid_request_error","message":"bad"}}"""),
        )
        val client = AiChatClientFactory.create(AiProviderConfig.Anthropic(apiKey = "bad"), http)

        val error = runCatching { client.generate(AiChatRequest.user("ping")) }.exceptionOrNull()

        assertTrue(error is AiProviderException)
        assertEquals(400, (error as AiProviderException).statusCode)
        assertTrue(error.message!!.contains("bad"))
    }

    @Test
    fun `anthropic config accepts custom compatible endpoint`() = runTest {
        val http = RecordingAiHttpTransport(
            AiHttpResponse(code = 200, body = """{"content":[{"type":"text","text":"ok"}]}"""),
        )
        val client = AiChatClientFactory.create(
            AiProviderConfig.Anthropic(
                apiKey = "key",
                baseUrl = "https://example.com/anthropic",
                model = "claude-compat",
            ),
            http,
        )

        val response = client.generate(AiChatRequest.user("ping"))

        assertEquals("https://example.com/anthropic/v1/messages", http.lastRequest!!.url)
        assertNull(http.lastRequest!!.headers["Authorization"])
        assertEquals("ok", response.text)
    }

    private class RecordingAiHttpTransport(
        private val response: AiHttpResponse,
    ) : AiHttpTransport {
        var lastRequest: AiHttpRequest? = null

        override suspend fun post(request: AiHttpRequest): AiHttpResponse {
            lastRequest = request
            return response
        }
    }
}
