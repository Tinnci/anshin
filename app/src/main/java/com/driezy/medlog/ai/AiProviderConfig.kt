package com.driezy.medlog.ai

sealed interface AiProviderConfig {
    data class Mimo(
        val apiKey: String,
        val model: String = "mimo-v2.5-pro",
        val baseUrl: String = "https://api.xiaomimimo.com/v1",
    ) : AiProviderConfig

    data class OpenAiCompatible(
        val baseUrl: String,
        val model: String,
        val apiKey: String? = null,
        val authMode: OpenAiAuthMode = OpenAiAuthMode.BEARER,
        val providerName: String = "OpenAI-compatible",
    ) : AiProviderConfig

    data class Gemini(
        val apiKey: String,
        val model: String = "gemini-2.5-flash",
        val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    ) : AiProviderConfig

    data class Anthropic(
        val apiKey: String,
        val model: String = "claude-sonnet-4-20250514",
        val baseUrl: String = "https://api.anthropic.com",
        val anthropicVersion: String = "2023-06-01",
    ) : AiProviderConfig
}

enum class OpenAiAuthMode {
    API_KEY_HEADER,
    BEARER,
}

object AiChatClientFactory {
    fun create(
        config: AiProviderConfig,
        transport: AiHttpTransport = UrlConnectionAiHttpTransport(),
    ): AiChatClient =
        AdkAiChatClient(
            model = ProtocolBackedAdkModel(
                name = config.modelName,
                delegate = createProtocolClient(config, transport),
            ),
        )

    private val AiProviderConfig.modelName: String
        get() = when (this) {
            is AiProviderConfig.Mimo -> model
            is AiProviderConfig.OpenAiCompatible -> model
            is AiProviderConfig.Gemini -> model
            is AiProviderConfig.Anthropic -> model
        }

    private fun createProtocolClient(
        config: AiProviderConfig,
        transport: AiHttpTransport,
    ): AiChatClient =
        when (config) {
            is AiProviderConfig.Mimo ->
                OpenAiCompatibleChatClient(
                    baseUrl = config.baseUrl,
                    model = config.model,
                    apiKey = config.apiKey,
                    authMode = OpenAiAuthMode.API_KEY_HEADER,
                    providerName = "MiMo",
                    maxOutputTokensParameter = OpenAiMaxOutputTokensParameter.MAX_COMPLETION_TOKENS,
                    transport = transport,
                )

            is AiProviderConfig.OpenAiCompatible ->
                OpenAiCompatibleChatClient(
                    baseUrl = config.baseUrl,
                    model = config.model,
                    apiKey = config.apiKey,
                    authMode = config.authMode,
                    providerName = config.providerName,
                    transport = transport,
                )

            is AiProviderConfig.Gemini ->
                GeminiGenerateContentClient(
                    apiKey = config.apiKey,
                    model = config.model,
                    baseUrl = config.baseUrl,
                    transport = transport,
                )

            is AiProviderConfig.Anthropic ->
                AnthropicMessagesClient(
                    apiKey = config.apiKey,
                    model = config.model,
                    baseUrl = config.baseUrl,
                    anthropicVersion = config.anthropicVersion,
                    transport = transport,
                )
        }
}
