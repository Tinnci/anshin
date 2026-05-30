package com.driezy.medlog.interaction

class DrugAliasNormalizer(
    aliasToCanonical: Map<String, String> = emptyMap(),
) {
    private val normalizedAliases = aliasToCanonical
        .mapKeys { (alias, _) -> alias.normalizeDrugKey() }
        .filterKeys { it.isNotBlank() }

    fun searchText(name: String, fullPath: String = "", category: String = ""): String {
        val canonical = canonicalName(name)
        return listOf(name, canonical, fullPath, category)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .lowercase()
    }

    private fun canonicalName(name: String): String =
        normalizedAliases[name.normalizeDrugKey()].orEmpty()
}

private fun String.normalizeDrugKey(): String =
    trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
