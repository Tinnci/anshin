package com.example.medlog.data.local

import android.content.Context
import com.example.medlog.data.model.Drug
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
 * 支持 drugs.json（西药）和 tcm_drugs_flat.json（中成药）
 */
@Singleton
class DrugDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun loadAllDrugs(): List<Drug> = withContext(Dispatchers.IO) {
        val western = parseJsonDrugs("json/drugs_clean.json", isTcm = false)
        val tcm = parseJsonDrugs("json/tcm_drugs_clean.json", isTcm = true)
        (western + tcm).sortedWith(compareBy({ it.initial }, { it.name }))
    }

    private fun parseJsonDrugs(assetPath: String, isTcm: Boolean): List<Drug> = try {
        val text = context.assets.open(assetPath).bufferedReader().readText()
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
                initial = computeInitial(name),
                isCompound = isCompound,
            )
        }
        result
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * 计算拼音首字母：
     * - ASCII 字母 → 大写
     * - 常用中文首字按 GB2312 区间近似映射到 A-Z
     * - 其他 → '#'
     */
    private fun computeInitial(name: String): String {
        if (name.isEmpty()) return "#"
        val c = name[0]
        if (c in 'a'..'z') return c.uppercaseChar().toString()
        if (c in 'A'..'Z') return c.toString()
        if (c.code < 0x4E00 || c.code > 0x9FA5) return "#"
        // GB2312 区间映射（近似拼音首字母）
        val code = c.code
        return when {
            code < 0x554A -> "A"
            code < 0x5C1B -> "B"
            code < 0x6015 -> "C"
            code < 0x61A7 -> "D"
            code < 0x63D3 -> "E"
            code < 0x6617 -> "F"
            code < 0x6747 -> "G"
            code < 0x6B2D -> "H"
            code < 0x6D84 -> "J"
            code < 0x7057 -> "K"
            code < 0x725C -> "L"
            code < 0x7528 -> "M"
            code < 0x7838 -> "N"
            code < 0x7E31 -> "O"
            code < 0x81D9 -> "P"
            code < 0x8426 -> "Q"
            code < 0x8704 -> "R"
            code < 0x8C28 -> "S"
            code < 0x8EA0 -> "T"
            code < 0x9128 -> "W"
            code < 0x9294 -> "X"
            code < 0x96AF -> "Y"
            code < 0x9B31 -> "Z"
            else           -> "#"
        }
    }
}
