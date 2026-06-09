package com.driezy.medlog.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class CloudAiDiscoveredModel(
    val id: String,
    val displayName: String = id,
    val supportsText: Boolean = true,
    val supportsImageInput: Boolean = false,
)

data class CloudAiModelDiscoveryResult(
    val isConnected: Boolean,
    val providerName: String,
    val models: List<CloudAiDiscoveredModel> = emptyList(),
    val statusCode: Int? = null,
    val errorMessage: String? = null,
) {
    fun selectBestModel(requireImageInput: Boolean): CloudAiDiscoveredModel? {
        val candidates = if (requireImageInput) {
            models.filter { it.supportsImageInput }
        } else {
            models.filter { it.supportsText }
        }
        return candidates.firstOrNull()
    }
}

class CloudAiModelDiscoveryClient(
    private val transport: AiHttpTransport = UrlConnectionAiHttpTransport(
        connectTimeoutMillis = 10_000,
        readTimeoutMillis = 20_000,
    ),
) {
    suspend fun fetch(config: AiProviderConfig): CloudAiModelDiscoveryResult {
        if (config is AiProviderConfig.Mimo) {
            return fetchMimo(config)
        }
        val spec = config.toModelEndpointSpec()
        return runCatching {
            val response = transport.get(
                AiHttpRequest(
                    url = spec.url,
                    headers = spec.headers,
                ),
            )
            if (response.code !in 200..299) {
                CloudAiModelDiscoveryResult(
                    isConnected = false,
                    providerName = spec.providerName,
                    statusCode = response.code,
                    errorMessage = "${spec.providerName} model discovery failed with HTTP ${response.code}: ${response.body}",
                )
            } else {
                CloudAiModelDiscoveryResult(
                    isConnected = true,
                    providerName = spec.providerName,
                    models = parseModels(response.body, spec),
                    statusCode = response.code,
                )
            }
        }.getOrElse { error ->
            CloudAiModelDiscoveryResult(
                isConnected = false,
                providerName = spec.providerName,
                errorMessage = "${spec.providerName} model discovery failed: ${error.message ?: error::class.java.simpleName}",
            )
        }
    }

    private suspend fun fetchMimo(config: AiProviderConfig.Mimo): CloudAiModelDiscoveryResult =
        runCatching {
            val response = transport.post(
                AiHttpRequest(
                    url = config.baseUrl.trimEnd('/') + "/chat/completions",
                    headers = mapOf(
                        "Accept" to "application/json",
                        "Content-Type" to "application/json",
                        "api-key" to config.apiKey,
                    ),
                    body = buildJsonObject {
                        put("model", config.model)
                        put(
                            "messages",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("role", "user")
                                        put("content", "ping")
                                    },
                                )
                            },
                        )
                        put("temperature", 0.0)
                        put("max_completion_tokens", 1)
                        put("stream", false)
                    }.toString(),
                ),
            )
            if (response.code !in 200..299) {
                CloudAiModelDiscoveryResult(
                    isConnected = false,
                    providerName = "MiMo",
                    statusCode = response.code,
                    errorMessage = "MiMo connectivity check failed with HTTP ${response.code}: ${response.body}",
                )
            } else {
                CloudAiModelDiscoveryResult(
                    isConnected = true,
                    providerName = "MiMo",
                    models = mimoModels,
                    statusCode = response.code,
                )
            }
        }.getOrElse { error ->
            CloudAiModelDiscoveryResult(
                isConnected = false,
                providerName = "MiMo",
                errorMessage = "MiMo connectivity check failed: ${error.message ?: error::class.java.simpleName}",
            )
        }

    private fun parseModels(body: String, spec: ModelEndpointSpec): List<CloudAiDiscoveredModel> {
        val root = json.parseToJsonElement(body)
        val entries = when (root) {
            is JsonObject -> {
                val data = root["data"] ?: root["models"]
                when (data) {
                    is JsonArray -> data
                    else -> JsonArray(emptyList())
                }
            }
            is JsonArray -> root
            else -> JsonArray(emptyList())
        }
        return entries.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val rawId = obj.stringValue("id")
                ?: obj.stringValue("name")
                ?: obj.stringValue("model")
                ?: return@mapNotNull null
            val id = rawId.removePrefix("models/")
            val displayName = obj.stringValue("displayName")
                ?: obj.stringValue("display_name")
                ?: id
            val modalities = obj.findStringSet(
                "modalities",
                "input_modalities",
                "inputModalities",
                "supported_modalities",
                "supportedModalities",
            )
            val supportsText = modalities.isEmpty() ||
                modalities.any { it == "text" || it == "language" } ||
                obj.findStringSet("supportedGenerationMethods", "supported_generation_methods").contains("generatecontent")
            val supportsImage = obj.hasImageCapability() ||
                id.looksImageCapable() ||
                (spec.defaultImageSupport && !id.looksTextOnly())
            CloudAiDiscoveredModel(
                id = id,
                displayName = displayName,
                supportsText = supportsText,
                supportsImageInput = supportsImage,
            )
        }
    }

    private fun AiProviderConfig.toModelEndpointSpec(): ModelEndpointSpec =
        when (this) {
            is AiProviderConfig.Mimo -> error("MiMo discovery uses a chat completions probe.")

            is AiProviderConfig.OpenAiCompatible -> ModelEndpointSpec(
                providerName = providerName,
                url = baseUrl.modelsUrl(),
                headers = buildMap {
                    put("Accept", "application/json")
                    apiKey?.takeIf { it.isNotBlank() }?.let { key ->
                        when (authMode) {
                            OpenAiAuthMode.API_KEY_HEADER -> put("api-key", key)
                            OpenAiAuthMode.BEARER -> put("Authorization", "Bearer $key")
                        }
                    }
                },
            )

            is AiProviderConfig.Gemini -> ModelEndpointSpec(
                providerName = "Gemini",
                url = baseUrl.modelsUrl(),
                headers = mapOf(
                    "Accept" to "application/json",
                    "x-goog-api-key" to apiKey,
                ),
                defaultImageSupport = true,
            )

            is AiProviderConfig.Anthropic -> ModelEndpointSpec(
                providerName = "Anthropic",
                url = baseUrl.anthropicModelsUrl(),
                headers = mapOf(
                    "Accept" to "application/json",
                    "x-api-key" to apiKey,
                    "anthropic-version" to anthropicVersion,
                ),
                defaultImageSupport = true,
            )
        }

    private fun String.modelsUrl(): String {
        val normalized = trimEnd('/')
        return if (normalized.endsWith("/models")) normalized else "$normalized/models"
    }

    private fun String.anthropicModelsUrl(): String {
        val normalized = trimEnd('/')
        return when {
            normalized.endsWith("/v1/models") -> normalized
            normalized.endsWith("/v1") -> "$normalized/models"
            else -> "$normalized/v1/models"
        }
    }

    private fun JsonObject.stringValue(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.findStringSet(vararg keys: String): Set<String> =
        keys.flatMap { key -> this[key].stringTokens() }
            .map { it.lowercase() }
            .toSet()

    private fun JsonElement?.stringTokens(): List<String> =
        when (this) {
            is JsonArray -> mapNotNull { it.jsonPrimitive.contentOrNull }
            is JsonPrimitive -> contentOrNull
                ?.split(',', ' ', ';')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            else -> emptyList()
        }

    private fun JsonObject.hasImageCapability(): Boolean {
        val modalityTokens = findStringSet(
            "modalities",
            "input_modalities",
            "inputModalities",
            "supported_modalities",
            "supportedModalities",
        )
        if (modalityTokens.any { it == "image" || it == "vision" || it == "multimodal" }) return true

        val capabilityTokens = findStringSet("capabilities", "capability", "features")
        if (capabilityTokens.any { it == "image" || it == "vision" || it == "multimodal" }) return true

        val capabilities = this["capabilities"] as? JsonObject
        if (capabilities != null) {
            val imageKeys = setOf("image", "images", "vision", "multimodal", "image_input", "imageInput")
            if (capabilities.any { (key, value) ->
                    key in imageKeys && value.booleanValueOrFalse()
                }
            ) {
                return true
            }
        }
        return false
    }

    private fun JsonElement.booleanValueOrFalse(): Boolean =
        when (this) {
            is JsonPrimitive -> booleanOrNull ?: contentOrNull.equals("true", ignoreCase = true)
            is JsonNull -> false
            else -> false
        }

    private fun String.looksImageCapable(): Boolean {
        val normalized = lowercase()
        return normalized.startsWith("gpt-4") ||
            normalized.startsWith("gpt-4o") ||
            normalized.startsWith("o3") ||
            normalized.startsWith("o4") ||
            normalized.startsWith("claude-3") ||
            normalized.startsWith("claude-sonnet") ||
            normalized.startsWith("claude-opus") ||
            normalized.startsWith("gemini") && "embedding" !in normalized ||
            normalized.startsWith("mimo") ||
            "vision" in normalized ||
            "image" in normalized ||
            "multimodal" in normalized ||
            "omni" in normalized ||
            "llava" in normalized ||
            "qwen-vl" in normalized ||
            "qwen2-vl" in normalized ||
            "qwen2.5-vl" in normalized ||
            "vl-" in normalized ||
            "-vl" in normalized ||
            "vila" in normalized ||
            "pixtral" in normalized
    }

    private fun String.looksTextOnly(): Boolean {
        val normalized = lowercase()
        return "embedding" in normalized ||
            "rerank" in normalized ||
            "whisper" in normalized ||
            "tts" in normalized ||
            "audio" in normalized
    }

    private data class ModelEndpointSpec(
        val providerName: String,
        val url: String,
        val headers: Map<String, String>,
        val defaultImageSupport: Boolean = false,
    )

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
        }
        val mimoModels = listOf(
            CloudAiDiscoveredModel(
                id = "mimo-v2.5",
                displayName = "MiMo-V2.5",
                supportsText = true,
                supportsImageInput = true,
            ),
            CloudAiDiscoveredModel(
                id = "mimo-v2.5-pro",
                displayName = "MiMo-V2.5-Pro",
                supportsText = true,
                supportsImageInput = false,
            ),
            CloudAiDiscoveredModel(
                id = "mimo-v2-flash",
                displayName = "MiMo-V2-Flash",
                supportsText = true,
                supportsImageInput = false,
            ),
        )
    }
}
