package com.driezy.medlog.ai

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiVisionClientTest {

    @Test
    fun `openai compatible client sends base64 image_url content part`() = runTest {
        val http = RecordingAiHttpTransport(
            AiHttpResponse(
                code = 200,
                body = """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""",
            ),
        )
        val client = AiChatClientFactory.create(
            AiProviderConfig.OpenAiCompatible(
                baseUrl = "http://10.0.2.2:11434/v1",
                model = "llava",
                apiKey = null,
            ),
            http,
        )

        client.generate(
            AiChatRequest(
                messages = listOf(
                    AiChatMessage.user(
                        parts = listOf(
                            AiChatContentPart.text("识别图中健康数据。"),
                            AiChatContentPart.imageBytes(byteArrayOf(1, 2, 3), "image/png"),
                        ),
                    ),
                ),
                temperature = 0.0,
            ),
        )

        val body = Json.parseToJsonElement(http.lastRequest!!.body).jsonObject
        val content = body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        assertEquals("text", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("image_url", content[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(
            "data:image/png;base64,AQID",
            content[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `gemini client sends inlineData image part`() = runTest {
        val http = RecordingAiHttpTransport(
            AiHttpResponse(
                code = 200,
                body = """{"candidates":[{"content":{"parts":[{"text":"ok"}]},"finishReason":"STOP"}]}""",
            ),
        )
        val client = AiChatClientFactory.create(
            AiProviderConfig.Gemini(apiKey = "key", model = "gemini-2.5-flash"),
            http,
        )

        client.generate(
            AiChatRequest(
                messages = listOf(
                    AiChatMessage.user(
                        parts = listOf(
                            AiChatContentPart.text("识别图中健康数据。"),
                            AiChatContentPart.imageBytes(byteArrayOf(1, 2, 3), "image/jpeg"),
                        ),
                    ),
                ),
                temperature = 0.0,
            ),
        )

        val body = Json.parseToJsonElement(http.lastRequest!!.body).jsonObject
        val parts = body["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        assertEquals("识别图中健康数据。", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("image/jpeg", parts[1].jsonObject["inlineData"]!!.jsonObject["mimeType"]!!.jsonPrimitive.content)
        assertEquals("AQID", parts[1].jsonObject["inlineData"]!!.jsonObject["data"]!!.jsonPrimitive.content)
    }

    @Test
    fun `image parts require image mime types`() {
        val error = runCatching {
            AiChatContentPart.imageBytes(byteArrayOf(1), "application/pdf")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    private class RecordingAiHttpTransport(private val response: AiHttpResponse) : AiHttpTransport {
        var lastRequest: AiHttpRequest? = null

        override suspend fun post(request: AiHttpRequest): AiHttpResponse {
            lastRequest = request
            return response
        }
    }
}
