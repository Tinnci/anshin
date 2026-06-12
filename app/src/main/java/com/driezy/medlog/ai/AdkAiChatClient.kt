package com.driezy.medlog.ai

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FinishReason
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.UsageMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

class AdkAiChatClient(
    val model: Model,
) : AiChatClient {
    override suspend fun generate(request: AiChatRequest): AiChatResponse {
        val responses = model.generateContent(request.toAdkRequest(model), false).toList()
        val text = responses.joinToString(separator = "") { response ->
            response.content?.parts.orEmpty().joinToString(separator = "") { it.text.orEmpty() }
        }
        if (text.isBlank()) {
            throw AiProviderException(
                providerName = model.name,
                statusCode = null,
                message = "${model.name} returned a non-text or empty ADK response.",
            )
        }
        val last = responses.lastOrNull()
        return AiChatResponse(
            text = text,
            finishReason = last?.modelVersion ?: last?.finishReason?.name,
            usage = last?.usageMetadata?.toAiUsage(),
        )
    }
}

class ProtocolBackedAdkModel(
    override val name: String,
    private val delegate: AiChatClient,
) : Model {
    override fun generateContent(
        request: LlmRequest,
        stream: Boolean,
    ): Flow<LlmResponse> = flow {
        val response = delegate.generate(request.toAiChatRequest())
        emit(
            LlmResponse(
                content = Content(
                    role = Role.MODEL,
                    parts = listOf(Part(text = response.text)),
                ),
                usageMetadata = response.usage?.toAdkUsage(),
                finishReason = response.finishReason?.toAdkFinishReason(),
                modelVersion = response.finishReason,
            ),
        )
    }
}

private fun AiChatRequest.toAdkRequest(model: Model): LlmRequest {
    val systemInstruction = messages
        .filter { it.role == AiChatRole.SYSTEM || it.role == AiChatRole.DEVELOPER }
        .map { it.toAdkContent(Role.SYSTEM) }
        .reduceOrNull { current, next ->
            current.copy(parts = current.parts + next.parts)
        }
    return LlmRequest(
        model = model,
        contents = messages
            .filterNot { it.role == AiChatRole.SYSTEM || it.role == AiChatRole.DEVELOPER }
            .map { it.toAdkContent() },
        config = GenerateContentConfig(
            systemInstruction = systemInstruction,
            temperature = temperature?.toFloat(),
            maxOutputTokens = maxOutputTokens,
        ),
    )
}

private fun AiChatMessage.toAdkContent(roleOverride: String? = null): Content =
    Content(
        role = roleOverride ?: when (role) {
            AiChatRole.ASSISTANT -> Role.MODEL
            AiChatRole.SYSTEM, AiChatRole.DEVELOPER -> Role.SYSTEM
            else -> Role.USER
        },
        parts = parts?.map { it.toAdkPart() } ?: listOf(Part(text = content)),
    )

private fun AiChatContentPart.toAdkPart(): Part =
    when (this) {
        is AiChatContentPart.Text -> Part(text = text)
        is AiChatContentPart.ImageBytes -> Part(
            inlineData = Blob(
                mimeType = mimeType,
                data = bytes,
            ),
        )
    }

private fun LlmRequest.toAiChatRequest(): AiChatRequest =
    AiChatRequest(
        messages = listOfNotNull(config.systemInstruction?.toAiChatMessage(AiChatRole.SYSTEM)) +
            contents.map { it.toAiChatMessage() },
        temperature = config.temperature?.toStableDouble(),
        maxOutputTokens = config.maxOutputTokens,
    )

private fun Content.toAiChatMessage(roleOverride: AiChatRole? = null): AiChatMessage =
    run {
        val role = roleOverride ?: when (role) {
            Role.MODEL -> AiChatRole.ASSISTANT
            Role.SYSTEM -> AiChatRole.SYSTEM
            else -> AiChatRole.USER
        }
        val text = parts.mapNotNull { it.text }.joinToString(separator = "\n")
        val hasNonTextPart = parts.any { it.text == null }
        if (hasNonTextPart) {
            AiChatMessage(
                role = role,
                parts = parts.mapNotNull { it.toAiChatPart() }.takeIf { it.isNotEmpty() },
            )
        } else {
            AiChatMessage(
                role = role,
                content = text,
            )
        }
    }

private fun Part.toAiChatPart(): AiChatContentPart? =
    when (val textPart = text) {
        null -> {
            val dataPart = inlineData ?: return null
            val bytes = dataPart.data ?: return null
            val mimeType = dataPart.mimeType ?: return null
            AiChatContentPart.ImageBytes(
                bytes = bytes,
                mimeType = mimeType,
            )
        }
        else -> AiChatContentPart.Text(textPart)
    }

private fun AiTokenUsage.toAdkUsage(): UsageMetadata =
    UsageMetadata(
        promptTokenCount = promptTokens,
        candidatesTokenCount = completionTokens,
        totalTokenCount = totalTokens,
    )

private fun UsageMetadata.toAiUsage(): AiTokenUsage =
    AiTokenUsage(
        promptTokens = promptTokenCount,
        completionTokens = candidatesTokenCount,
        totalTokens = totalTokenCount,
    )

private fun String.toAdkFinishReason(): FinishReason =
    runCatching { FinishReason.valueOf(uppercase()) }.getOrDefault(FinishReason.OTHER)

private fun Float.toStableDouble(): Double =
    kotlin.math.round(toDouble() * 1_000_000.0) / 1_000_000.0
