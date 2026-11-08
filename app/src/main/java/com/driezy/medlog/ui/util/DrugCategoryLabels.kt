package com.driezy.medlog.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.driezy.medlog.data.local.DRUG_PATH_SEPARATOR
import com.driezy.medlog.data.local.DrugCatalogAssets
import com.driezy.medlog.data.local.DrugTaxonomy

internal class DrugCategoryLabels(private val taxonomy: DrugTaxonomy?, private val language: String) {
    fun label(path: String, isTcm: Boolean? = null): String {
        val translated = if (isTcm == null) {
            taxonomy?.western?.label(path, language) ?: taxonomy?.tcm?.label(path, language)
        } else {
            taxonomy?.tree(isTcm)?.label(path, language)
        }
        return translated ?: path
    }

    fun matchesCategoryPath(path: String, isTcm: Boolean, query: String): Boolean {
        val tree = taxonomy?.tree(isTcm) ?: return false
        var node = path
        while (node.isNotEmpty()) {
            if (tree.label(node, language)?.contains(query, ignoreCase = true) == true) return true
            node = node.substringBeforeLast(DRUG_PATH_SEPARATOR, "")
        }
        return false
    }

    fun medicationPath(path: String, isTcm: Boolean): String {
        val parts = path.split(DRUG_PATH_SEPARATOR)
        val tree = taxonomy?.tree(isTcm) ?: return path
        if (parts.size < 2 || language == "zh" || !tree.contains(parts.first())) return path
        return parts.dropLast(1).indices.joinToString(DRUG_PATH_SEPARATOR) { index ->
            label(parts.take(index + 1).joinToString(DRUG_PATH_SEPARATOR), isTcm)
        } + DRUG_PATH_SEPARATOR + parts.last()
    }
}

@Composable
internal fun rememberDrugCategoryLabels(): DrugCategoryLabels {
    val context = LocalContext.current.applicationContext
    val language = medicationNameLanguage()
    val taxonomy = remember(context) { runCatching { DrugCatalogAssets.taxonomy(context) }.getOrNull() }
    return remember(taxonomy, language) { DrugCategoryLabels(taxonomy, language) }
}
