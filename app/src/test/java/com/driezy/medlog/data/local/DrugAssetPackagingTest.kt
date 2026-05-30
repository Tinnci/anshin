package com.driezy.medlog.data.local

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(File(assetJsonDir, "drug_initials_clean.json").exists())
        assertTrue(File(assetJsonDir, "drug_dataset_manifest.json").exists())
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
        assertTrue(script.contains("write_dataset_manifest"))
        assertTrue(script.contains("build_drug_initials"))
        assertTrue(dataSource.contains("drug_aliases_clean.json"))
        assertTrue(dataSource.contains("drug_initials_clean.json"))
        assertTrue(dataSource.contains("aliases = aliases[name].orEmpty()"))
        assertFalse(dataSource.contains("GB2312"))
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

        assertTrue("Expected at least the initial reviewed alias coverage", aliases.size >= 53)
        assertTrue(aliases.getValue("阿司匹林").containsAll(listOf("拜阿司匹灵", "acetylsalicylic acid")))
        assertTrue(aliases.getValue("对乙酰氨基酚").containsAll(listOf("扑热息痛", "acetaminophen")))
    }

    @Test
    fun `drug dataset manifest records generated asset counts and source hashes`() {
        val manifest = readJsonObject("app/src/main/assets/json/drug_dataset_manifest.json")
        val western = readJsonObject("app/src/main/assets/json/drugs_clean.json")
        val tcm = readJsonObject("app/src/main/assets/json/tcm_drugs_clean.json")
        val aliases = readJsonObject("app/src/main/assets/json/drug_aliases_clean.json")
        val initials = readJsonObject("app/src/main/assets/json/drug_initials_clean.json")

        assertTrue(manifest.getValue("dataVersion").jsonPrimitive.content.isNotBlank())
        assertTrue(manifest.getValue("generatedAt").jsonPrimitive.content.isNotBlank())
        assertTrue(manifest.getValue("generator").jsonPrimitive.content.contains("scripts/analyze_drugs.py"))
        assertEquals(western.size.toString(), manifest.getValue("westernDrugCount").jsonPrimitive.content)
        assertEquals(tcm.size.toString(), manifest.getValue("tcmDrugCount").jsonPrimitive.content)
        assertEquals(aliases.size.toString(), manifest.getValue("reviewedAliasCount").jsonPrimitive.content)
        assertEquals(initials.size.toString(), manifest.getValue("initialCount").jsonPrimitive.content)
        assertTrue(manifest.getValue("sourceHashes").jsonObject.getValue("drugs.json").jsonPrimitive.content.length >= 64)
        assertTrue(manifest.getValue("assetHashes").jsonObject.getValue("drug_initials_clean.json").jsonPrimitive.content.length >= 64)
    }

    @Test
    fun `drug assets resolve known duplicate ownership and build pinyin initials`() {
        val western = readJsonObject("app/src/main/assets/json/drugs_clean.json")
        val tcm = readJsonObject("app/src/main/assets/json/tcm_drugs_clean.json")
        val initials = readJsonObject("app/src/main/assets/json/drug_initials_clean.json")

        assertFalse(western.containsKey("复方樟脑乳膏"))
        assertTrue(tcm.containsKey("复方樟脑乳膏"))
        assertTrue(western.containsKey("复方甘草片"))
        assertFalse(tcm.containsKey("复方甘草片"))
        assertEquals("A", initials.getValue("阿司匹林").jsonPrimitive.content)
        assertEquals("Z", initials.getValue("重组人促卵泡激素").jsonPrimitive.content)
    }

    private fun readJsonObject(path: String) =
        Json.parseToJsonElement(File(projectRoot, path).readText()).jsonObject

    private fun kotlinx.serialization.json.JsonElement.firstPath(): String =
        (this as JsonArray).first().jsonPrimitive.content
}
