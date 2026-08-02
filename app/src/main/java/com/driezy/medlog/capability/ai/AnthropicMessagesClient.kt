package com.driezy.medlog.capability.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AnthropicMessagesClient(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String,
    private val anthropicVersion: String,
    private val transport: AiHttpTransport = UrlConnectionAiHttpTransport(),
) : AiChatClient {

    override suspend fun generate(request: AiChatRequest): AiChatResponse {
        val response = transport.post(
            AiHttpRequest(
                url = endpointUrl(),
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "x-api-key" to apiKey,
                    "anthropic-version" to anthropicVersion,
                ),
                body = json.encodeToString(request.toAnthropicRequest()),
            ),
        )

        if (response.code !in 200..299) {
            throw AiProviderException(
                providerName = PROVIDER_NAME,
                statusCode = response.code,
                message = "$PROVIDER_NAME request failed with HTTP ${response.code}: ${response.body}",
            )
        }

        return parseResponse(response.body)
    }

    private fun endpointUrl(): String {
        val normalized = baseUrl.trimEnd('/')
        return when {
            normalized.endsWith("/messages") -> normalized
            normalized.endsWith("/v1") -> "$normalized/messages"
            else -> "$normalized/v1/messages"
        }
    }

    private fun AiChatRequest.toAnthropicRequest(): AnthropicRequest {
        val system = messages
            .filter { it.role == AiChatRole.SYSTEM || it.role == AiChatRole.DEVELOPER }
            .joinToString(separator = "\n\n") { it.content.ifBlank { it.partsText() } }
            .takeIf { it.isNotBlank() }

        return AnthropicRequest(
            model = model,
            maxTokens = maxOutputTokens ?: DEFAULT_MAX_TOKENS,
            temperature = temperature,
            system = system,
            messages = messages
                .filterNot { it.role == AiChatRole.SYSTEM || it.role == AiChatRole.DEVELOPER }
                .map { message ->
                    AnthropicMessage(
                        role = when (message.role) {
                            AiChatRole.ASSISTANT -> "assistant"
                            else -> "user"
                        },
                        content = message.toAnthropicContent(),
                    )
                },
        )
    }

    private fun AiChatMessage.toAnthropicContent(): AnthropicMessageContent = parts?.let { contentParts ->
        AnthropicMessageContent(
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
                                put("type", "image")
                                put(
                                    "source",
                                    buildJsonObject {
                                        put("type", "base64")
                                        put("media_type", part.mimeType)
                                        put("data", part.base64)
                                    },
                                )
                            },
                        )
                    }
                }
            },
        )
    } ?: AnthropicMessageContent(JsonPrimitive(content))

    private fun AiChatMessage.partsText(): String = parts
        ?.filterIsInstance<AiChatContentPart.Text>()
        ?.joinToString(separator = "\n") { it.text }
        .orEmpty()

    private fun parseResponse(body: String): AiChatResponse {
        val parsed = runCatching { json.decodeFromString<AnthropicResponse>(body) }
            .getOrElse { error ->
                throw AiProviderException(
                    providerName = PROVIDER_NAME,
                    statusCode = null,
                    message = "$PROVIDER_NAME returned an invalid response: ${error.message}",
                    cause = error,
                )
            }

        val text = parsed.content
            .filterIsInstance<AnthropicResponseContent.Text>()
            .joinToString(separator = "") { it.text }
        if (text.isBlank()) {
            throw AiProviderException(
                providerName = PROVIDER_NAME,
                statusCode = null,
                message = "$PROVIDER_NAME returned a non-text or empty response. stop_reason=${parsed.stopReason}",
            )
        }

        return AiChatResponse(
            text = text,
            finishReason = parsed.stopReason,
            usage = parsed.usage?.toUsage(),
        )
    }

    @Serializable
    private data class AnthropicRequest(
        val model: String,
        @SerialName("max_tokens")
        val maxTokens: Int,
        val messages: List<AnthropicMessage>,
        val system: String? = null,
        val temperature: Double? = null,
    )

    @Serializable
    private data class AnthropicMessage(val role: String, val content: AnthropicMessageContent)

    @Serializable
    @JvmInline
    private value class AnthropicMessageContent(val value: JsonElement)

    @Serializable
    private data class AnthropicResponse(
        val content: List<AnthropicResponseContent> = emptyList(),
        @SerialName("stop_reason")
        val stopReason: String? = null,
        val usage: AnthropicUsage? = null,
    )

    @Serializable
    private sealed interface AnthropicResponseContent {
        @Serializable
        @SerialName("text")
        data class Text(val text: String, val type: String = "text") : AnthropicResponseContent

        @Serializable
        @SerialName("tool_use")
        data class ToolUse(val id: String? = null, val name: String? = null, val type: String = "tool_use") :
            AnthropicResponseContent
    }

    @Serializable
    private data class AnthropicUsage(
        @SerialName("input_tokens")
        val inputTokens: Int? = null,
        @SerialName("output_tokens")
        val outputTokens: Int? = null,
    ) {
        fun toUsage(): AiTokenUsage = AiTokenUsage(
            promptTokens = inputTokens,
            completionTokens = outputTokens,
            totalTokens = listOfNotNull(inputTokens, outputTokens).takeIf { it.isNotEmpty() }?.sum(),
        )
    }

    private companion object {
        const val PROVIDER_NAME = "Anthropic"
        const val DEFAULT_MAX_TOKENS = 1024

        val json = Json {
            encodeDefaults = false
            explicitNulls = false
            ignoreUnknownKeys = true
            classDiscriminator = "type"
        }
    }
}
