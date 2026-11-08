package com.driezy.medlog.data.local

import com.driezy.medlog.data.model.Drug
import com.driezy.medlog.data.repository.DrugRepositoryImpl
import com.driezy.medlog.feature.medications.catalog.displayInitial
import com.driezy.medlog.feature.medications.catalog.rankCatalogDrugs
import com.driezy.medlog.ui.util.DrugCategoryLabels
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import java.security.MessageDigest
import java.text.Collator
import java.util.Locale

class DrugAssetPackagingTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }
    private val assets = File(projectRoot, "app/src/main/assets/json")
    private val catalog = readAsset<DrugCatalogFile>(DrugCatalogAssets.CATALOG)
    private val categories = readAsset<DrugCategoriesFile>(DrugCatalogAssets.CATEGORIES)
    private val names = readAsset<DrugNamesFile>(DrugCatalogAssets.NAMES).names
    private val taxonomy = DrugTaxonomy(categories)
    private val drugs by lazy { assembleDrugs(catalog, taxonomy, names).associateBy(Drug::name) }

    @Test
    fun `apk assets keep only normalized runtime drug data`() {
        val catalogAssets = setOf(
            DrugCatalogAssets.CATALOG,
            DrugCatalogAssets.CATEGORIES,
            DrugCatalogAssets.NAMES,
            "drug_dataset_manifest.json",
        )
        val packaged = assets.list().orEmpty().filter { it.startsWith("drug") || it.startsWith("tcm_") }.toSet()
        assertEquals(catalogAssets, packaged)
    }

    @Test
    fun `western drug assets rehome clear misc category records`() {
        assertTrue(drugs.getValue("阿托伐他汀").fullPath.startsWith("心血管系统 > 血脂调节剂"))
        assertTrue(drugs.getValue("乙胺丁醇").fullPath.startsWith("系统用抗感染药 > 抗分支杆菌药"))
    }

    @Test
    fun `drug name index covers common brand generic and chemical names`() {
        assertTrue(names.values.count { it.aliases.isNotEmpty() } >= 53)
        assertTrue(names.getValue("阿司匹林").aliases.containsAll(listOf("拜阿司匹灵", "acetylsalicylic acid")))
        assertTrue(names.getValue("对乙酰氨基酚").aliases.containsAll(listOf("扑热息痛", "acetaminophen")))
    }

    @Test
    fun `localized starters resolve to catalog names and name-only matches`() {
        val starters = names.filterValues { it.starter }
        assertTrue(starters.isNotEmpty())
        starters.forEach { (canonical, entry) ->
            assertTrue(canonical in catalog.western)
            assertEquals(setOf("en", "ja", "ko"), entry.localized.keys)
        }

        val aspirin = drugs.getValue("阿司匹林")
        assertTrue(aspirin.isSuggestionStarter)
        assertEquals("Aspirin", aspirin.localizedName("en"))
        assertEquals("阿司匹林", aspirin.localizedName("zh"))
        assertTrue(aspirin.nameSuggestionRank("아스피", "ko") > 0)
        assertTrue(aspirin.nameSuggestionRank("アスピリン", "en") > 0)
        assertTrue(aspirin.nameSuggestionRank("acetyl", "en") > 0)
        assertEquals(0, aspirin.nameSuggestionRank("cardiovascular", "en"))
    }

    @Test
    fun `category paths remain compatible with saved medication strings`() {
        val aspirin = drugs.getValue("阿司匹林")
        assertTrue(aspirin.allPaths.size > 1)
        assertEquals(aspirin.fullPath, aspirin.allPaths.first())
        assertTrue(aspirin.allPaths.all { it.endsWith(" > 阿司匹林") })

        val labels = DrugCategoryLabels(taxonomy, "en")
        assertEquals("Antihypertensives", labels.label("心血管系统 > 抗高血压药", false))
        assertEquals(
            "Cardiovascular system > Antihypertensives > 氨氯地平",
            labels.medicationPath("心血管系统 > 抗高血压药 > 氨氯地平", false),
        )
        assertEquals("Personal > Custom", labels.medicationPath("Personal > Custom", false))
    }

    @Test
    fun `taxonomy and name records resolve to the catalog and browser has four languages`() {
        assertTrue(names.keys.all { it in catalog.western })
        assertEquals("Amlodipine", drugs.getValue("氨氯地平").localizedName("en"))
        for (nodes in listOf(categories.western, categories.tcm)) {
            assertTrue(nodes.isNotEmpty())
            assertTrue(
                nodes.filter { it.parent == null || nodes[it.parent].parent == null }
                    .all { it.labels.keys.containsAll(setOf("en", "ja", "ko")) },
            )
        }
        assertEquals(catalog.western.size + catalog.tcm.size, drugs.size)
    }

    @Test
    fun `manifest counts and digests match the bundled snapshot`() {
        val manifest = Json.parseToJsonElement(File(assets, "drug_dataset_manifest.json").readText()).jsonObject
        fun field(name: String) = manifest.getValue(name).jsonPrimitive.content

        assertEquals(catalog.schemaVersion.toString(), field("dataVersion"))
        assertTrue(field("generatedAt").isNotBlank())
        assertEquals(catalog.western.size.toString(), field("westernDrugCount"))
        assertEquals(catalog.tcm.size.toString(), field("tcmDrugCount"))
        assertEquals(names.values.count { it.aliases.isNotEmpty() }.toString(), field("reviewedAliasCount"))
        assertEquals(drugs.size.toString(), field("initialCount"))

        val assetHashes = manifest.getValue("assetHashes").jsonObject
        assertEquals(
            setOf(DrugCatalogAssets.CATALOG, DrugCatalogAssets.CATEGORIES, DrugCatalogAssets.NAMES),
            assetHashes.keys,
        )
        for ((asset, expected) in assetHashes) {
            val hash = MessageDigest.getInstance("SHA-256").digest(File(assets, asset).readBytes())
                .joinToString("") { "%02x".format(it) }
            assertEquals(expected.jsonPrimitive.content, hash)
        }
    }

    @Test
    fun `drug assets resolve known duplicate ownership and build pinyin initials`() {
        assertFalse(catalog.western.containsKey("复方樟脑乳膏"))
        assertTrue(catalog.tcm.containsKey("复方樟脑乳膏"))
        assertTrue(catalog.western.containsKey("复方甘草片"))
        assertFalse(catalog.tcm.containsKey("复方甘草片"))
        assertEquals("A", drugs.getValue("阿司匹林").initial)
        assertEquals("Z", drugs.getValue("重组人促卵泡激素").initial)
    }

    @Test
    fun `selected language ranks exact display names ahead of alternate names`() {
        val preferred = Drug("其他药", "分类", localizedNames = mapOf("ja" to "テスト"))
        val alternate = Drug("测试", "分类", aliases = listOf("テスト"))
        val ranked = rankCatalogDrugs(
            listOf(alternate, preferred),
            "テスト",
            "ja",
            DrugCategoryLabels(taxonomy, "ja"),
            Collator.getInstance(Locale.JAPANESE),
        )

        assertEquals(listOf(preferred, alternate), ranked.map { it.drug })
        assertTrue(ranked.all { it.directMatch })
        assertEquals("て", preferred.displayInitial("ja"))
        assertEquals("#", alternate.displayInitial("ja"))
        assertEquals("テスト", alternate.alternateNameMatching("テスト", "ja"))
    }

    @Test
    fun `catalog search finds translated category paths`() {
        val amlodipine = drugs.getValue("氨氯地平")
        val results = rankCatalogDrugs(
            listOf(amlodipine),
            "Calcium channel blockers",
            "en",
            DrugCategoryLabels(taxonomy, "en"),
            Collator.getInstance(Locale.ENGLISH),
        )

        assertEquals(amlodipine, results.single().drug)
        assertTrue(results.single().directMatch)
    }

    @Test
    fun `TCM visibility excludes catalog suggestions without removing source records`() = runTest {
        val conventional = drugs.getValue("阿司匹林")
        val traditional = drugs.values.first { it.isTcm }
        val dataSource = mock<DrugDataSource> {
            onBlocking { loadAllDrugs() } doReturn listOf(conventional, traditional)
        }
        val repository = DrugRepositoryImpl(dataSource)

        assertTrue(repository.suggestDrugNames(traditional.name, "en", includeTcm = false).isEmpty())
        assertEquals(traditional, repository.suggestDrugNames(traditional.name, "en", includeTcm = true).single())
        assertEquals(conventional, repository.suggestDrugNames("aspirin", "en", includeTcm = false).single())
        assertTrue(traditional in repository.getAllDrugs())
    }

    private inline fun <reified T> readAsset(asset: String): T =
        File(assets, asset).inputStream().buffered().use { DrugCatalogAssets.decode(it) }
}
