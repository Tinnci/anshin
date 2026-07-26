package com.driezy.medlog.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class OpenAiCompatibleChatClient(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String?,
    private val authMode: OpenAiAuthMode,
    private val providerName: String,
    private val maxOutputTokensParameter: OpenAiMaxOutputTokensParameter = OpenAiMaxOutputTokensParameter.MAX_TOKENS,
    private val transport: AiHttpTransport = UrlConnectionAiHttpTransport(),
) : AiChatClient {

    override suspend fun generate(request: AiChatRequest): AiChatResponse {
        val response = transport.post(
            AiHttpRequest(
                url = endpointUrl(),
                headers = headers(),
                body = json.encodeToString(request.toOpenAiRequest()),
            ),
        )

        if (response.code !in 200..299) {
            throw AiProviderException(
                providerName = providerName,
                statusCode = response.code,
                message = "$providerName request failed with HTTP ${response.code}: ${response.body}",
            )
        }

        return parseResponse(response.body)
    }

    private fun headers(): Map<String, String> = buildMap {
        put("Content-Type", "application/json")
        apiKey?.takeIf { it.isNotBlank() }?.let { key ->
            when (authMode) {
                OpenAiAuthMode.API_KEY_HEADER -> put("api-key", key)
                OpenAiAuthMode.BEARER -> put("Authorization", "Bearer $key")
            }
        }
    }

    private fun endpointUrl(): String {
        val normalized = baseUrl.trimEnd('/')
        return if (normalized.endsWith("/chat/completions")) {
            normalized
        } else {
            "$normalized/chat/completions"
        }
    }

    private fun AiChatRequest.toOpenAiRequest(): OpenAiChatRequest = OpenAiChatRequest(
        model = model,
        messages = messages.map {
            OpenAiChatMessage(
                role = it.role.wireName,
                content = it.toOpenAiContent(),
            )
        },
        temperature = temperature,
        maxTokens = maxOutputTokens.takeIf {
            maxOutputTokensParameter == OpenAiMaxOutputTokensParameter.MAX_TOKENS
        },
        maxCompletionTokens = maxOutputTokens.takeIf {
            maxOutputTokensParameter == OpenAiMaxOutputTokensParameter.MAX_COMPLETION_TOKENS
        },
    )

    private fun AiChatMessage.toOpenAiContent(): JsonElement = parts?.let { contentParts ->
        buildJsonArray {
            contentParts.forEach { part ->
                when (part) {
                    is AiChatContentPart.Text -> add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", part.text)
                        },
                    )
                    is AiChatContentPart.ImageBytes -> add(
                        buildJsonObject {
                            put("type", "image_url")
                            put(
                                "image_url",
                                buildJsonObject {
                                    put("url", part.dataUrl)
                                },
                            )
                        },
                    )
                }
            }
        }
    } ?: JsonPrimitive(content)

    private fun parseResponse(body: String): AiChatResponse {
        val parsed = runCatching { json.decodeFromString<OpenAiChatResponse>(body) }
            .getOrElse { error ->
                throw AiProviderException(
                    providerName = providerName,
                    statusCode = null,
                    message = "$providerName returned an invalid response: ${error.message}",
                    cause = error,
                )
            }
        val choice = parsed.choices.firstOrNull()
            ?: throw AiProviderException(
                providerName = providerName,
                statusCode = null,
                message = "$providerName returned no choices.",
            )
        val text = choice.message.extractText()
        if (text.isBlank()) {
            throw AiProviderException(
                providerName = providerName,
                statusCode = null,
                message = "$providerName returned a non-text or empty response. finish_reason=${choice.finishReason}",
            )
        }
        return AiChatResponse(
            text = text,
            finishReason = choice.finishReason,
            usage = parsed.usage?.toUsage(),
        )
    }

    private fun OpenAiResponseMessage.extractText(): String = when (val value = content) {
        JsonNull -> ""
        is JsonPrimitive -> value.content
        is JsonArray -> value.joinToString(separator = "") { part ->
            val obj = part as? JsonObject ?: return@joinToString ""
            when ((obj["type"] as? JsonPrimitive)?.content) {
                "text" -> obj["text"]?.jsonPrimitive?.content.orEmpty()
                "refusal" -> obj["refusal"]?.jsonPrimitive?.content.orEmpty()
                else -> ""
            }
        }
        else -> ""
    }

    @Serializable
    private data class OpenAiChatRequest(
        val model: String,
        val messages: List<OpenAiChatMessage>,
        val temperature: Double? = null,
        @SerialName("max_tokens")
        val maxTokens: Int? = null,
        @SerialName("max_completion_tokens")
        val maxCompletionTokens: Int? = null,
    )

    @Serializable
    private data class OpenAiChatMessage(val role: String, val content: JsonElement)

    @Serializable
    private data class OpenAiChatResponse(
        val choices: List<OpenAiChoice> = emptyList(),
        val usage: OpenAiUsage? = null,
    )

    @Serializable
    private data class OpenAiChoice(
        val message: OpenAiResponseMessage,
        @SerialName("finish_reason")
        val finishReason: String? = null,
    )

    @Serializable
    private data class OpenAiResponseMessage(
        val role: String? = null,
        val content: JsonElement? = null,
        @SerialName("tool_calls")
        val toolCalls: JsonElement? = null,
        @SerialName("function_call")
        val functionCall: JsonElement? = null,
    )

    @Serializable
    private data class OpenAiUsage(
        @SerialName("prompt_tokens")
        val promptTokens: Int? = null,
        @SerialName("completion_tokens")
        val completionTokens: Int? = null,
        @SerialName("total_tokens")
        val totalTokens: Int? = null,
    ) {
        fun toUsage(): AiTokenUsage = AiTokenUsage(
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
        )
    }

    private companion object {
        val json = Json {
            encodeDefaults = false
            explicitNulls = false
            ignoreUnknownKeys = true
        }
    }
}

enum class OpenAiMaxOutputTokensParameter {
    MAX_TOKENS,
    MAX_COMPLETION_TOKENS,
}
