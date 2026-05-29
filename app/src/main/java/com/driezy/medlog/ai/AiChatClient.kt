package com.driezy.medlog.ai

import java.util.Base64

interface AiChatClient {
    suspend fun generate(request: AiChatRequest): AiChatResponse
}

data class AiChatRequest(
    val messages: List<AiChatMessage>,
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
) {
    companion object {
        fun user(content: String): AiChatRequest =
            AiChatRequest(messages = listOf(AiChatMessage.user(content)))
    }
}

data class AiChatMessage(
    val role: AiChatRole,
    val content: String = "",
    val parts: List<AiChatContentPart>? = null,
) {
    companion object {
        fun system(content: String): AiChatMessage = AiChatMessage(AiChatRole.SYSTEM, content)

        fun developer(content: String): AiChatMessage = AiChatMessage(AiChatRole.DEVELOPER, content)

        fun user(content: String): AiChatMessage = AiChatMessage(AiChatRole.USER, content)

        fun user(parts: List<AiChatContentPart>): AiChatMessage =
            AiChatMessage(AiChatRole.USER, parts = parts)

        fun assistant(content: String): AiChatMessage = AiChatMessage(AiChatRole.ASSISTANT, content)
    }
}

sealed interface AiChatContentPart {
    data class Text(val text: String) : AiChatContentPart

    data class ImageBytes(
        val bytes: ByteArray,
        val mimeType: String,
    ) : AiChatContentPart {
        init {
            require(mimeType.startsWith("image/")) {
                "Image content part requires an image/* MIME type."
            }
        }

        val base64: String
            get() = Base64.getEncoder().encodeToString(bytes)

        val dataUrl: String
            get() = "data:$mimeType;base64,$base64"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ImageBytes) return false
            if (!bytes.contentEquals(other.bytes)) return false
            return mimeType == other.mimeType
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + mimeType.hashCode()
            return result
        }
    }

    companion object {
        fun text(text: String): AiChatContentPart = Text(text)

        fun imageBytes(bytes: ByteArray, mimeType: String): AiChatContentPart =
            ImageBytes(bytes = bytes, mimeType = mimeType)
    }
}

enum class AiChatRole(val wireName: String) {
    SYSTEM("system"),
    DEVELOPER("developer"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool"),
}

data class AiChatResponse(
    val text: String,
    val finishReason: String? = null,
    val usage: AiTokenUsage? = null,
)

data class AiTokenUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
)

class AiProviderException(
    val providerName: String,
    val statusCode: Int?,
    val errorKind: AiStructuredResponseErrorKind? = null,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
