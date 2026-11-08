package com.driezy.medlog.data.local

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream

private const val CATALOG_SCHEMA_VERSION = 2
internal const val DRUG_PATH_SEPARATOR = " > "

@Serializable
internal data class DrugCatalogFile(
    val schemaVersion: Int,
    val taxonomyId: String,
    val western: Map<String, DrugRecord>,
    val tcm: Map<String, DrugRecord>,
)

@Serializable
internal data class DrugRecord(val initial: String, val categories: List<Int>)

@Serializable
internal data class DrugCategoriesFile(
    val schemaVersion: Int,
    val taxonomyId: String,
    val western: List<DrugCategoryNode>,
    val tcm: List<DrugCategoryNode>,
)

@Serializable
internal data class DrugCategoryNode(
    val name: String,
    val parent: Int? = null,
    val labels: Map<String, String> = emptyMap(),
)

@Serializable
internal data class DrugNamesFile(val schemaVersion: Int, val names: Map<String, DrugNames>)

@Serializable
internal data class DrugNames(
    val aliases: List<String> = emptyList(),
    val localized: Map<String, String> = emptyMap(),
    val starter: Boolean = false,
)

internal class DrugCategoryTree(nodes: List<DrugCategoryNode>) {
    private val paths = buildList<String> {
        for (node in nodes) {
            val parent = node.parent?.let { get(it) }
            add(if (parent == null) node.name else parent + DRUG_PATH_SEPARATOR + node.name)
        }
    }
    private val nodesByPath = paths.zip(nodes).toMap()

    fun path(id: Int): String = paths[id]

    fun label(path: String, language: String): String? = nodesByPath[path]?.let { it.labels[language] ?: it.name }

    fun contains(path: String): Boolean = path in nodesByPath
}

internal class DrugTaxonomy(file: DrugCategoriesFile) {
    val id = file.taxonomyId
    val western = DrugCategoryTree(file.western)
    val tcm = DrugCategoryTree(file.tcm)

    fun tree(isTcm: Boolean): DrugCategoryTree = if (isTcm) tcm else western
}

internal object DrugCatalogAssets {
    const val CATALOG = "drug_catalog.json"
    const val CATEGORIES = "drug_categories.json"
    const val NAMES = "drug_names.json"

    private var cachedTaxonomy: DrugTaxonomy? = null
    private var cachedNames: Map<String, DrugNames>? = null

    @Synchronized
    fun taxonomy(context: Context): DrugTaxonomy = cachedTaxonomy
        ?: DrugTaxonomy(read<DrugCategoriesFile>(context, CATEGORIES).also { requireVersion(it.schemaVersion) })
            .also { cachedTaxonomy = it }

    @Synchronized
    fun names(context: Context): Map<String, DrugNames> = cachedNames
        ?: read<DrugNamesFile>(context, NAMES).also { requireVersion(it.schemaVersion) }.names
            .also { cachedNames = it }

    fun catalog(context: Context): DrugCatalogFile =
        read<DrugCatalogFile>(context, CATALOG).also { requireVersion(it.schemaVersion) }

    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified T> decode(input: InputStream): T = Json.decodeFromStream(input)

    private inline fun <reified T> read(context: Context, asset: String): T =
        context.assets.open("json/$asset").buffered().use { decode(it) }

    private fun requireVersion(version: Int) {
        require(version == CATALOG_SCHEMA_VERSION) { "Unsupported drug catalog schema $version" }
    }
}
