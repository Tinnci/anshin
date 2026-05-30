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
        assertFalse(File(assetJsonDir, "drugs.json").exists())
        assertFalse(File(assetJsonDir, "tcm_drugs_flat.json").exists())
    }

    @Test
    fun `drug normalization script reads raw inputs outside apk assets`() {
        val script = File(projectRoot, "scripts/analyze_drugs.py").readText()

        assertTrue(script.contains("\"scripts\", \"data\""))
        assertTrue(script.contains("RAW_BASE"))
        assertTrue(script.contains("ASSET_BASE"))
    }
}
