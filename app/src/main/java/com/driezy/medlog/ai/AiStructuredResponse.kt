package com.driezy.medlog.ai

data class AiStructuredResponse<T>(
    val rawText: String,
    val rawJson: String? = null,
    val parsed: T? = null,
    val status: AiStructuredResponseStatus,
    val provider: String? = null,
    val model: String? = null,
    val schemaVersion: Int,
    val warnings: List<String> = emptyList(),
    val errorKind: AiStructuredResponseErrorKind? = null,
    val finishReason: String? = null,
    val usage: AiTokenUsage? = null,
)

enum class AiStructuredResponseStatus {
    SUCCESS,
    PARTIAL,
    FAILED,
}

enum class AiStructuredResponseErrorKind {
    EMPTY_RESPONSE,
    JSON_NOT_FOUND,
    JSON_INVALID,
    SCHEMA_INVALID,
    POLICY_VIOLATION,
    PROVIDER_ERROR,
    UNKNOWN,
}
