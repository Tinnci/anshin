package com.driezy.medlog.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GeminiGenerateContentClient(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String,
    private val transport: AiHttpTransport = UrlConnectionAiHttpTransport(),
) : AiChatClient {

    override suspend fun generate(request: AiChatRequest): AiChatResponse {
        val response = transport.post(
            AiHttpRequest(
                url = endpointUrl(),
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "x-goog-api-key" to apiKey,
                ),
                body = json.encodeToString(request.toGeminiRequest()),
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
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val modelPath = if (model.startsWith("models/")) model else "models/$model"
        return "$normalizedBaseUrl/$modelPath:generateContent"
    }

    private fun AiChatRequest.toGeminiRequest(): GeminiRequest {
        val systemText = messages
            .filter { it.role == AiChatRole.SYSTEM || it.role == AiChatRole.DEVELOPER }
            .joinToString(separator = "\n\n") { it.content }
            .takeIf { it.isNotBlank() }

        return GeminiRequest(
            contents = messages
                .filterNot { it.role == AiChatRole.SYSTEM || it.role == AiChatRole.DEVELOPER }
                .map { message ->
                    GeminiContent(
                        role = when (message.role) {
                            AiChatRole.ASSISTANT -> "model"
                            else -> "user"
                        },
                        parts = message.toGeminiParts(),
                    )
                },
            systemInstruction = systemText?.let {
                GeminiSystemInstruction(parts = listOf(GeminiPart(text = it)))
            },
            generationConfig = if (temperature != null || maxOutputTokens != null) {
                GeminiGenerationConfig(
                    temperature = temperature,
                    maxOutputTokens = maxOutputTokens,
                )
            } else {
                null
            },
        )
    }

    private fun AiChatMessage.toGeminiParts(): List<GeminiPart> =
        parts?.map { part ->
            when (part) {
                is AiChatContentPart.Text -> GeminiPart(text = part.text)
                is AiChatContentPart.ImageBytes -> GeminiPart(
                    inlineData = GeminiInlineData(
                        mimeType = part.mimeType,
                        data = part.base64,
                    ),
                )
            }
        } ?: listOf(GeminiPart(text = content))

    private fun parseResponse(body: String): AiChatResponse {
        val parsed = runCatching { json.decodeFromString<GeminiResponse>(body) }
            .getOrElse { error ->
                throw AiProviderException(
                    providerName = PROVIDER_NAME,
                    statusCode = null,
                    message = "$PROVIDER_NAME returned an invalid response: ${error.message}",
                    cause = error,
                )
            }
        val candidate = parsed.candidates.firstOrNull()
            ?: throw AiProviderException(
                providerName = PROVIDER_NAME,
                statusCode = null,
                message = "$PROVIDER_NAME returned no candidates. promptFeedback=${parsed.promptFeedback?.blockReason ?: "unknown"}",
            )
        val text = candidate.content
            ?.parts
            .orEmpty()
            .mapNotNull { it.text }
            .joinToString(separator = "")
        if (text.isBlank()) {
            throw AiProviderException(
                providerName = PROVIDER_NAME,
                statusCode = null,
                message = "$PROVIDER_NAME returned a non-text or empty response. finishReason=${candidate.finishReason}",
            )
        }
        return AiChatResponse(
            text = text,
            finishReason = candidate.finishReason,
            usage = parsed.usageMetadata?.toUsage(),
        )
    }

    @Serializable
    private data class GeminiRequest(
        val contents: List<GeminiContent>,
        val systemInstruction: GeminiSystemInstruction? = null,
        val generationConfig: GeminiGenerationConfig? = null,
    )

    @Serializable
    private data class GeminiContent(
        val role: String? = null,
        val parts: List<GeminiPart> = emptyList(),
    )

    @Serializable
    private data class GeminiSystemInstruction(
        val parts: List<GeminiPart>,
    )

    @Serializable
    private data class GeminiPart(
        val text: String? = null,
        val functionCall: GeminiFunctionCall? = null,
        val inlineData: GeminiInlineData? = null,
    )

    @Serializable
    private data class GeminiInlineData(
        val mimeType: String,
        val data: String,
    )

    @Serializable
    private data class GeminiGenerationConfig(
        val temperature: Double? = null,
        val maxOutputTokens: Int? = null,
    )

    @Serializable
    private data class GeminiResponse(
        val candidates: List<GeminiCandidate> = emptyList(),
        val usageMetadata: GeminiUsage? = null,
        val promptFeedback: GeminiPromptFeedback? = null,
    )

    @Serializable
    private data class GeminiCandidate(
        val content: GeminiContent? = null,
        val finishReason: String? = null,
    )

    @Serializable
    private data class GeminiFunctionCall(
        val name: String? = null,
    )

    @Serializable
    private data class GeminiPromptFeedback(
        val blockReason: String? = null,
    )

    @Serializable
    private data class GeminiUsage(
        val promptTokenCount: Int? = null,
        val candidatesTokenCount: Int? = null,
        val totalTokenCount: Int? = null,
    ) {
        fun toUsage(): AiTokenUsage =
            AiTokenUsage(
                promptTokens = promptTokenCount,
                completionTokens = candidatesTokenCount,
                totalTokens = totalTokenCount,
            )
    }

    private companion object {
        const val PROVIDER_NAME = "Gemini"

        val json = Json {
            encodeDefaults = false
            explicitNulls = false
            ignoreUnknownKeys = true
        }
    }
}
