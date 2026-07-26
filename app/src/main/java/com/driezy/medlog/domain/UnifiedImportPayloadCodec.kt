package com.driezy.medlog.domain

import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.OpenAiCompatibleCloudAuthMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.Base64

sealed interface UnifiedImportPayload {
    data class MedicationPlan(val plan: PlanExport) : UnifiedImportPayload
    data class CloudAiApiKey(val key: CloudAiApiKeyImport) : UnifiedImportPayload
    data class Unknown(val reason: String) : UnifiedImportPayload
}

data class CloudAiApiKeyImport(
    val provider: CloudAiProvider,
    val apiKey: String,
    val baseUrl: String? = null,
    val model: String? = null,
    val providerName: String? = null,
    val openAiAuthMode: OpenAiCompatibleCloudAuthMode = OpenAiCompatibleCloudAuthMode.BEARER,
)

object UnifiedImportPayloadCodec {
    const val API_KEY_SCHEME = "anshin:key:v1:"
    private const val NVIDIA_NIM_BASE_URL = "https://integrate.api.nvidia.com/v1"
    private const val NVIDIA_NIM_PROVIDER_NAME = "NVIDIA NIM APIs"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    fun decode(raw: String): UnifiedImportPayload {
        val input = raw.trim()
        if (input.isBlank()) {
            return UnifiedImportPayload.Unknown("Empty input")
        }

        when (val plan = PlanExportCodec.decodeWithDiagnostics(input)) {
            is PlanExportDecodeResult.Success -> return UnifiedImportPayload.MedicationPlan(plan.plan)
            is PlanExportDecodeResult.Failure -> Unit
        }

        decodeStandardApiKey(input)?.let {
            return UnifiedImportPayload.CloudAiApiKey(it)
        }
        decodeEnvApiKey(input)?.let {
            return UnifiedImportPayload.CloudAiApiKey(it)
        }

        return UnifiedImportPayload.Unknown("Unsupported import payload")
    }

    private fun decodeStandardApiKey(input: String): CloudAiApiKeyImport? {
        if (!input.startsWith(API_KEY_SCHEME)) return null
        val payload = runCatching {
            val decoded = Base64.getUrlDecoder().decode(input.removePrefix(API_KEY_SCHEME))
            json.decodeFromString<ApiKeyEnvelope>(decoded.toString(Charsets.UTF_8))
        }.getOrNull() ?: return null

        if (payload.version != 1 || payload.app != "anshin" || payload.kind != "cloud_ai_key") return null
        val apiKey = payload.apiKey?.trim().takeUnless { it.isNullOrBlank() } ?: return null
        return cloudAiKeyImport(
            providerToken = payload.provider,
            apiKey = apiKey,
            baseUrl = payload.baseUrl,
            model = payload.model,
            providerName = payload.providerName,
            authModeToken = payload.authMode,
        )
    }

    private fun decodeEnvApiKey(input: String): CloudAiApiKeyImport? {
        val env = parseKeyValueLines(input)
        if (env.isEmpty()) return null

        env["NVIDIA_API_KEY"]?.let { apiKey ->
            return cloudAiKeyImport(
                providerToken = "nvidia-nim",
                apiKey = apiKey,
                baseUrl = env["NVIDIA_BASE_URL"] ?: env["NVIDIA_API_BASE"],
                model = env["NVIDIA_MODEL"],
                providerName = env["NVIDIA_PROVIDER_NAME"],
                authModeToken = env["NVIDIA_AUTH_MODE"],
            )
        }

        env["ANTHROPIC_API_KEY"]?.let { apiKey ->
            return cloudAiKeyImport(
                providerToken = "anthropic",
                apiKey = apiKey,
                baseUrl = env["ANTHROPIC_BASE_URL"],
                model = env["ANTHROPIC_MODEL"],
                providerName = env["ANTHROPIC_PROVIDER_NAME"],
                authModeToken = env["ANTHROPIC_AUTH_MODE"],
            )
        }

        env["GEMINI_API_KEY"]?.let { apiKey ->
            return cloudAiKeyImport(
                providerToken = "gemini",
                apiKey = apiKey,
                model = env["GEMINI_MODEL"],
            )
        }

        env["MIMO_API_KEY"]?.let { apiKey ->
            return cloudAiKeyImport(
                providerToken = "mimo",
                apiKey = apiKey,
                baseUrl = env["MIMO_BASE_URL"],
                model = env["MIMO_MODEL"],
            )
        }

        val openAiKey = env["OPENAI_API_KEY"] ?: env["API_KEY"] ?: return null
        return cloudAiKeyImport(
            providerToken = env["OPENAI_PROVIDER"] ?: env["PROVIDER"] ?: "openai-compatible",
            apiKey = openAiKey,
            baseUrl = env["OPENAI_BASE_URL"] ?: env["BASE_URL"],
            model = env["OPENAI_MODEL"] ?: env["MODEL"],
            providerName = env["OPENAI_PROVIDER_NAME"] ?: env["PROVIDER_NAME"],
            authModeToken = env["OPENAI_AUTH_MODE"] ?: env["AUTH_MODE"],
        )
    }

    private fun cloudAiKeyImport(
        providerToken: String?,
        apiKey: String,
        baseUrl: String? = null,
        model: String? = null,
        providerName: String? = null,
        authModeToken: String? = null,
    ): CloudAiApiKeyImport {
        val normalized = providerToken.normalizeToken()
        val effectiveBaseUrl = baseUrl?.trim().orEmpty()
        val provider = when {
            normalized in setOf("mimo") -> CloudAiProvider.MIMO
            normalized in setOf("gemini", "google", "google-gemini") -> CloudAiProvider.GEMINI
            normalized in setOf("anthropic", "claude") -> CloudAiProvider.ANTHROPIC
            else -> CloudAiProvider.OPENAI_COMPATIBLE
        }

        val isNvidia = normalized in setOf("nvidia", "nvidia-nim", "nim") ||
            effectiveBaseUrl.contains("integrate.api.nvidia.com", ignoreCase = true)

        return CloudAiApiKeyImport(
            provider = provider,
            apiKey = apiKey.trim(),
            baseUrl = when {
                provider == CloudAiProvider.OPENAI_COMPATIBLE && isNvidia && effectiveBaseUrl.isBlank() -> NVIDIA_NIM_BASE_URL
                effectiveBaseUrl.isNotBlank() -> effectiveBaseUrl
                else -> null
            },
            model = model?.trim()?.takeIf { it.isNotBlank() },
            providerName = when {
                provider == CloudAiProvider.OPENAI_COMPATIBLE && isNvidia -> NVIDIA_NIM_PROVIDER_NAME
                providerName?.isNotBlank() == true -> providerName.trim()
                normalized in setOf("openrouter") -> "OpenRouter"
                else -> null
            },
            openAiAuthMode = when (authModeToken.normalizeToken()) {
                "api-key", "api-key-header", "x-api-key" -> OpenAiCompatibleCloudAuthMode.API_KEY_HEADER
                else -> OpenAiCompatibleCloudAuthMode.BEARER
            },
        )
    }

    private fun parseKeyValueLines(input: String): Map<String, String> = input.lineSequence()
        .mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@mapNotNull null
            val normalizedLine = trimmed.removePrefix("export ").trim()
            val equalsIndex = normalizedLine.indexOf('=')
            if (equalsIndex <= 0) return@mapNotNull null
            val key = normalizedLine.substring(0, equalsIndex).trim().uppercase()
            val value = normalizedLine.substring(equalsIndex + 1)
                .substringBefore(" #")
                .trim()
                .trimMatchingQuotes()
            key to value
        }
        .filter { (_, value) -> value.isNotBlank() }
        .toMap()

    private fun String?.normalizeToken(): String = this?.trim()
        ?.lowercase()
        ?.replace('_', '-')
        ?: ""

    private fun String.trimMatchingQuotes(): String {
        if (length < 2) return this
        val first = first()
        val last = last()
        return if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            substring(1, lastIndex)
        } else {
            this
        }
    }
}

@Serializable
private data class ApiKeyEnvelope(
    @SerialName("v") val version: Int = 1,
    @SerialName("app") val app: String = "",
    @SerialName("kind") val kind: String = "",
    @SerialName("provider") val provider: String? = null,
    @SerialName("apiKey") val apiKey: String? = null,
    @SerialName("baseUrl") val baseUrl: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("providerName") val providerName: String? = null,
    @SerialName("authMode") val authMode: String? = null,
)
