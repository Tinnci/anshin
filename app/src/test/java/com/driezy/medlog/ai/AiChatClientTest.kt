package com.driezy.medlog.ai

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatClientTest {

    @Test
    fun `mimo client posts openai compatible chat request with api key auth`() = runTest {
        val http = RecordingAiHttpTransport(
            response = AiHttpResponse(
                code = 200,
                body = """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "可以随餐服用，但请遵医嘱。"
                          },
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 12,
                        "completion_tokens": 8,
                        "total_tokens": 20
                      }
                    }
                """.trimIndent(),
            ),
        )
        val client = AiChatClientFactory.create(
            AiProviderConfig.Mimo(apiKey = "mimo-key"),
            http,
        )

        val response = client.generate(
            AiChatRequest(
                messages = listOf(
                    AiChatMessage.system("你是用药助手。"),
                    AiChatMessage.user("布洛芬怎么吃？"),
                ),
                temperature = 0.2,
                maxOutputTokens = 256,
            ),
        )

        assertEquals("https://api.xiaomimimo.com/v1/chat/completions", http.lastRequest!!.url)
        assertEquals("mimo-key", http.lastRequest!!.headers["api-key"])
        assertEquals("application/json", http.lastRequest!!.headers["Content-Type"])
        assertFalse(http.lastRequest!!.headers.containsKey("Authorization"))

        val body = Json.parseToJsonElement(http.lastRequest!!.body).jsonObject
        assertEquals("mimo-v2.5-pro", body["model"]!!.jsonPrimitive.content)
        assertEquals("0.2", body["temperature"]!!.jsonPrimitive.content)
        assertEquals("256", body["max_completion_tokens"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("max_tokens"))
        val messages = body["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("你是用药助手。", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)

        assertEquals("可以随餐服用，但请遵医嘱。", response.text)
        assertEquals("stop", response.finishReason)
        assertEquals(20, response.usage?.totalTokens)
    }

    @Test
    fun `local openai compatible client allows no auth header`() = runTest {
        val http = RecordingAiHttpTransport(
            response = AiHttpResponse(
                code = 200,
                body = """{"choices":[{"message":{"role":"assistant","content":"local ok"}}]}""",
            ),
        )
        val client = AiChatClientFactory.create(
            AiProviderConfig.OpenAiCompatible(
                baseUrl = "http://10.0.2.2:11434/v1",
                model = "qwen2.5:7b",
                apiKey = null,
            ),
            http,
        )

        val response = client.generate(AiChatRequest.user("ping"))

        assertEquals("http://10.0.2.2:11434/v1/chat/completions", http.lastRequest!!.url)
        assertNull(http.lastRequest!!.headers["api-key"])
        assertNull(http.lastRequest!!.headers["Authorization"])
        assertEquals("local ok", response.text)
    }

    @Test
    fun `gemini client posts generateContent request and parses candidate text`() = runTest {
        val http = RecordingAiHttpTransport(
            response = AiHttpResponse(
                code = 200,
                body = """
                    {
                      "candidates": [
                        {
                          "content": {
                            "role": "model",
                            "parts": [
                              { "text": "记录完成。" },
                              { "text": "今天继续观察。" }
                            ]
                          },
                          "finishReason": "STOP"
                        }
                      ],
                      "usageMetadata": {
                        "promptTokenCount": 9,
                        "candidatesTokenCount": 6,
                        "totalTokenCount": 15
                      }
                    }
                """.trimIndent(),
            ),
        )
        val client = AiChatClientFactory.create(
            AiProviderConfig.Gemini(apiKey = "gemini-key", model = "gemini-2.5-flash"),
            http,
        )

        val response = client.generate(
            AiChatRequest(
                messages = listOf(
                    AiChatMessage.system("只给简短建议。"),
                    AiChatMessage.user("我刚量了血压。"),
                    AiChatMessage.assistant("请记录数值。"),
                    AiChatMessage.user("120/80"),
                ),
                temperature = 0.3,
                maxOutputTokens = 128,
            ),
        )

        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            http.lastRequest!!.url,
        )
        assertEquals("gemini-key", http.lastRequest!!.headers["x-goog-api-key"])
        assertEquals("application/json", http.lastRequest!!.headers["Content-Type"])

        val body = Json.parseToJsonElement(http.lastRequest!!.body).jsonObject
        val systemParts = body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray
        assertEquals("只给简短建议。", systemParts[0].jsonObject["text"]!!.jsonPrimitive.content)
        val contents = body["contents"]!!.jsonArray
        assertEquals("user", contents[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("model", contents[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("120/80", contents[2].jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("0.3", body["generationConfig"]!!.jsonObject["temperature"]!!.jsonPrimitive.content)
        assertEquals("128", body["generationConfig"]!!.jsonObject["maxOutputTokens"]!!.jsonPrimitive.content)

        assertEquals("记录完成。今天继续观察。", response.text)
        assertEquals("STOP", response.finishReason)
        assertEquals(15, response.usage?.totalTokens)
    }

    @Test
    fun `http errors include status and provider body`() = runTest {
        val http = RecordingAiHttpTransport(
            response = AiHttpResponse(code = 401, body = """{"error":"bad key"}"""),
        )
        val client = AiChatClientFactory.create(
            AiProviderConfig.Mimo(apiKey = "bad-key"),
            http,
        )

        val error = runCatching { client.generate(AiChatRequest.user("ping")) }.exceptionOrNull()

        assertTrue(error is AiProviderException)
        assertEquals(401, (error as AiProviderException).statusCode)
        assertTrue(error.message!!.contains("bad key"))
    }

    @Test
    fun `openai compatible client concatenates text content parts`() = runTest {
        val http = RecordingAiHttpTransport(
            response = AiHttpResponse(
                code = 200,
                body = """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": [
                              { "type": "text", "text": "第一条。" },
                              { "type": "text", "text": "第二条。" }
                            ]
                          },
                          "finish_reason": "stop"
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )
        val client = AiChatClientFactory.create(AiProviderConfig.Mimo(apiKey = "key"), http)

        val response = client.generate(AiChatRequest.user("ping"))

        assertEquals("第一条。第二条。", response.text)
    }

    @Test
    fun `openai compatible client surfaces refusal content parts`() = runTest {
        val http = RecordingAiHttpTransport(
            response = AiHttpResponse(
                code = 200,
                body = """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": [
                              { "type": "refusal", "refusal": "我不能提供该建议。" }
                            ]
                          },
                          "finish_reason": "content_filter"
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )
        val client = AiChatClientFactory.create(AiProviderConfig.Mimo(apiKey = "key"), http)

        val response = client.generate(AiChatRequest.user("ping"))

        assertEquals("我不能提供该建议。", response.text)
        assertEquals("content_filter", response.finishReason)
    }

    @Test
    fun `openai compatible client rejects tool call only responses`() = runTest {
        val http = RecordingAiHttpTransport(
            response = AiHttpResponse(
                code = 200,
                body = """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": null,
                            "tool_calls": [
                              { "type": "function", "function": { "name": "search", "arguments": "{}" } }
                            ]
                          },
                          "finish_reason": "tool_calls"
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )
        val client = AiChatClientFactory.create(AiProviderConfig.Mimo(apiKey = "key"), http)

        val error = runCatching { client.generate(AiChatRequest.user("ping")) }.exceptionOrNull()

        assertTrue(error is AiProviderException)
        assertTrue(error!!.message!!.contains("non-text"))
    }

    @Test
    fun `gemini client reports prompt feedback when no candidates are returned`() = runTest {
        val http = RecordingAiHttpTransport(
            response = AiHttpResponse(
                code = 200,
                body = """
                    {
                      "promptFeedback": {
                        "blockReason": "SAFETY"
                      },
                      "usageMetadata": {
                        "promptTokenCount": 9,
                        "totalTokenCount": 9
                      }
                    }
                """.trimIndent(),
            ),
        )
        val client = AiChatClientFactory.create(AiProviderConfig.Gemini(apiKey = "key"), http)

        val error = runCatching { client.generate(AiChatRequest.user("ping")) }.exceptionOrNull()

        assertTrue(error is AiProviderException)
        assertTrue(error!!.message!!.contains("SAFETY"))
    }

    @Test
    fun `gemini client rejects function call only candidate as non text`() = runTest {
        val http = RecordingAiHttpTransport(
            response = AiHttpResponse(
                code = 200,
                body = """
                    {
                      "candidates": [
                        {
                          "content": {
                            "role": "model",
                            "parts": [
                              { "functionCall": { "name": "lookup", "args": {} } }
                            ]
                          },
                          "finishReason": "STOP"
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )
        val client = AiChatClientFactory.create(AiProviderConfig.Gemini(apiKey = "key"), http)

        val error = runCatching { client.generate(AiChatRequest.user("ping")) }.exceptionOrNull()

        assertTrue(error is AiProviderException)
        assertTrue(error!!.message!!.contains("non-text"))
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
