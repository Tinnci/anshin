package com.driezy.medlog.data.local

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrugAssetPackagingTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    @Test
    fun `apk assets keep only runtime drug json files`() {
        val assetJsonDir = File(projectRoot, "app/src/main/assets/json")

        assertTrue(File(assetJsonDir, "drugs_clean.json").exists())
        assertTrue(File(assetJsonDir, "tcm_drugs_clean.json").exists())
        assertTrue(File(assetJsonDir, "drug_aliases_clean.json").exists())
        assertFalse(File(assetJsonDir, "drugs.json").exists())
        assertFalse(File(assetJsonDir, "tcm_drugs_flat.json").exists())
    }

    @Test
    fun `drug normalization script reads raw inputs outside apk assets`() {
        val script = File(projectRoot, "scripts/analyze_drugs.py").readText()
        val dataSource = File(projectRoot, "app/src/main/java/com/driezy/medlog/data/local/DrugDataSource.kt").readText()

        assertTrue(script.contains("\"scripts\", \"data\""))
        assertTrue(script.contains("RAW_BASE"))
        assertTrue(script.contains("ASSET_BASE"))
        assertTrue(script.contains("normalize_western_path"))
        assertTrue(script.contains("write_clean_aliases"))
        assertTrue(dataSource.contains("drug_aliases_clean.json"))
        assertTrue(dataSource.contains("tags = aliases[name].orEmpty()"))
    }

    @Test
    fun `western drug assets rehome clear misc category records`() {
        val drugs = readJsonObject("app/src/main/assets/json/drugs_clean.json")

        assertTrue(drugs.getValue("阿托伐他汀").firstPath().startsWith("心血管系统 > 血脂调节剂"))
        assertTrue(drugs.getValue("乙胺丁醇").firstPath().startsWith("系统用抗感染药 > 抗分支杆菌药"))
    }

    @Test
    fun `drug alias asset covers common brand generic and chemical names`() {
        val aliases = DrugAliasAssetParser.parseAliases(
            File(projectRoot, "app/src/main/assets/json/drug_aliases_clean.json").readText(),
        )

        assertEquals(53, aliases.size)
        assertTrue(aliases.getValue("阿司匹林").containsAll(listOf("拜阿司匹灵", "acetylsalicylic acid")))
        assertTrue(aliases.getValue("对乙酰氨基酚").containsAll(listOf("扑热息痛", "acetaminophen")))
    }

    private fun readJsonObject(path: String) =
        Json.parseToJsonElement(File(projectRoot, path).readText()).jsonObject

    private fun kotlinx.serialization.json.JsonElement.firstPath(): String =
        (this as JsonArray).first().jsonPrimitive.content
}
