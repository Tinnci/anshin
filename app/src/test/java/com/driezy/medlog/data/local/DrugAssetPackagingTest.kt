package com.driezy.medlog.data.local

import java.io.File
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
        val drugsJson = File(projectRoot, "app/src/main/assets/json/drugs_clean.json").readText()

        assertTrue(drugsJson.contains("\"阿托伐他汀\": [\n    \"心血管系统 > 血脂调节剂"))
        assertTrue(drugsJson.contains("\"乙胺丁醇\": [\n    \"系统用抗感染药 > 抗分支杆菌药"))
    }

    @Test
    fun `drug alias asset covers common brand generic and chemical names`() {
        val aliasesJson = File(projectRoot, "app/src/main/assets/json/drug_aliases_clean.json").readText()

        assertTrue(aliasesJson.contains("\"阿司匹林\""))
        assertTrue(aliasesJson.contains("\"拜阿司匹灵\""))
        assertTrue(aliasesJson.contains("\"acetylsalicylic acid\""))
        assertTrue(aliasesJson.contains("\"对乙酰氨基酚\""))
        assertTrue(aliasesJson.contains("\"扑热息痛\""))
        assertTrue(aliasesJson.contains("\"acetaminophen\""))
    }
}
