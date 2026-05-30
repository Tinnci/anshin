package com.driezy.medlog.data.local

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object DrugAliasAssetParser {
    fun parseAliases(text: String, json: Json = Json { ignoreUnknownKeys = true; isLenient = true }): Map<String, List<String>> {
        val root = json.parseToJsonElement(text).jsonObject
        return root.mapValues { (_, value) -> aliasesFrom(value) }
    }

    fun parseAliasToCanonical(
        text: String,
        json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    ): Map<String, String> =
        parseAliases(text, json).flatMap { (canonicalName, aliases) ->
            aliases.map { alias -> alias to canonicalName }
        }.toMap()

    private fun aliasesFrom(value: JsonElement): List<String> {
        val aliases = value.jsonObject["aliases"] as? JsonArray ?: return emptyList()
        return aliases.mapNotNull { alias ->
            (alias as? JsonPrimitive)
                ?.jsonPrimitive
                ?.content
                ?.takeIf { it.isNotBlank() }
        }
    }
}
