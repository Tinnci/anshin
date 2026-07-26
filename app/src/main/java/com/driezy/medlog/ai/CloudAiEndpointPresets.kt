package com.driezy.medlog.ai

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class CloudAiEndpointProtocol {
    OPENAI_COMPATIBLE,
    ANTHROPIC,
}

data class CloudAiEndpointPreset(
    val id: String,
    val name: String,
    val api: String,
    val protocol: CloudAiEndpointProtocol,
)

object CloudAiEndpointPresetCodec {
    fun decode(jsonText: String): List<CloudAiEndpointPreset> {
        val root = Json.parseToJsonElement(jsonText) as? JsonArray ?: return emptyList()
        return root.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.stringValue("id") ?: return@mapNotNull null
            val name = obj.stringValue("name") ?: id
            val api = obj.stringValue("api") ?: return@mapNotNull null
            val protocol = when (obj.stringValue("protocol")) {
                "anthropic" -> CloudAiEndpointProtocol.ANTHROPIC
                else -> CloudAiEndpointProtocol.OPENAI_COMPATIBLE
            }
            CloudAiEndpointPreset(
                id = id,
                name = name,
                api = api.trimEnd('/'),
                protocol = protocol,
            )
        }
    }

    private fun JsonObject.stringValue(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

object CloudAiEndpointPresetLoader {
    fun load(context: Context): List<CloudAiEndpointPreset> = runCatching {
        context.assets.open("json/opencode_ai_endpoints.json").use { input ->
            CloudAiEndpointPresetCodec.decode(
                input.bufferedReader(Charsets.UTF_8).use { it.readText() },
            )
        }
    }.getOrDefault(emptyList())
}
