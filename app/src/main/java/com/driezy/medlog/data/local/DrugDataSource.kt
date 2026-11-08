package com.driezy.medlog.data.local

import android.content.Context
import android.util.Log
import com.driezy.medlog.data.model.Drug
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrugDataSource @Inject constructor(@param:ApplicationContext private val context: Context) {
    suspend fun loadAllDrugs(): List<Drug> = withContext(Dispatchers.IO) {
        try {
            assembleDrugs(
                DrugCatalogAssets.catalog(context),
                DrugCatalogAssets.taxonomy(context),
                DrugCatalogAssets.names(context),
            )
        } catch (e: Exception) {
            Log.e("DrugDataSource", "Could not load the bundled drug catalog", e)
            emptyList()
        }
    }
}

internal fun assembleDrugs(
    catalog: DrugCatalogFile,
    taxonomy: DrugTaxonomy,
    names: Map<String, DrugNames>,
): List<Drug> = buildList {
    require(catalog.taxonomyId == taxonomy.id) { "Drug catalog and taxonomy snapshots do not match" }
    for (isTcm in listOf(false, true)) {
        val records = if (isTcm) catalog.tcm else catalog.western
        val categories = taxonomy.tree(isTcm)
        for ((name, record) in records) {
            val metadata = if (isTcm) null else names[name]
            val paths = record.categories.map { categories.path(it) + DRUG_PATH_SEPARATOR + name }
            val primaryPath = paths.first()
            add(
                Drug(
                    name = name,
                    category = primaryPath.substringBefore(DRUG_PATH_SEPARATOR),
                    fullPath = primaryPath,
                    allPaths = if (paths.size > 1) paths else emptyList(),
                    isTcm = isTcm,
                    initial = record.initial,
                    aliases = metadata?.aliases.orEmpty(),
                    isCompound = name.contains('/') ||
                        name.startsWith("复方") ||
                        paths.any { it.contains("复方") && !it.contains("复方除外") },
                    localizedNames = metadata?.localized.orEmpty(),
                    isSuggestionStarter = metadata?.starter == true,
                ),
            )
        }
    }
}.sortedWith(compareBy({ it.initial }, { it.name }))
