package com.driezy.medlog.data.local

import android.content.Context
import com.driezy.medlog.data.model.Drug
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从 assets/json/ 加载药品数据库 JSON 文件
 * 运行时只打包清理后的 drugs_clean.json（西药）和 tcm_drugs_clean.json（中成药）。
 * 原始数据保留在 scripts/data/，避免进入 APK assets。
 */
@Singleton
class DrugDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun loadAllDrugs(): List<Drug> = withContext(Dispatchers.IO) {
        val aliases = parseDrugAliases("json/drug_aliases_clean.json")
        val initials = parseDrugInitials("json/drug_initials_clean.json")
        val western = parseJsonDrugs("json/drugs_clean.json", isTcm = false, aliases = aliases, initials = initials)
        val tcm = parseJsonDrugs("json/tcm_drugs_clean.json", isTcm = true, initials = initials)
        (western + tcm).sortedWith(compareBy({ it.initial }, { it.name }))
    }

    private fun parseJsonDrugs(
        assetPath: String,
        isTcm: Boolean,
        aliases: Map<String, List<String>> = emptyMap(),
        initials: Map<String, String> = emptyMap(),
    ): List<Drug> = try {
        val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        val root = lenientJson.parseToJsonElement(text).jsonObject
        val result = ArrayList<Drug>(root.size)

        root.entries.forEach { (name, value) ->
            val paths: List<String> = when (value) {
                is JsonPrimitive -> listOf(value.content)
                is JsonArray     -> value.map { it.jsonPrimitive.content }
                else             -> emptyList()
            }
            if (paths.isEmpty()) return@forEach

            val bestPath = paths.first()
            val parts = bestPath.split(" > ")
            val category = parts.firstOrNull() ?: bestPath

            val isCompound = name.contains('/') || name.startsWith("复方") ||
                paths.any { it.contains("复方") && !it.contains("复方除外") }

            result += Drug(
                name = name,
                category = category,
                fullPath = bestPath,
                allPaths = if (paths.size > 1) paths else emptyList(),
                isTcm = isTcm,
                initial = initials[name] ?: fallbackInitial(name),
                aliases = aliases[name].orEmpty(),
                isCompound = isCompound,
            )
        }
        result
    } catch (e: Exception) {
        emptyList()
    }

    private fun parseDrugAliases(assetPath: String): Map<String, List<String>> = try {
        val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        DrugAliasAssetParser.parseAliases(text, lenientJson)
    } catch (e: Exception) {
            emptyMap()
    }

    private fun parseDrugInitials(assetPath: String): Map<String, String> = try {
        val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        lenientJson.parseToJsonElement(text).jsonObject.mapValues { (_, value) ->
            value.jsonPrimitive.content
        }
    } catch (e: Exception) {
        emptyMap()
    }

    private fun fallbackInitial(name: String): String {
        if (name.isEmpty()) return "#"
        val c = name[0]
        if (c in 'a'..'z') return c.uppercaseChar().toString()
        if (c in 'A'..'Z') return c.toString()
        return "#"
    }
}
