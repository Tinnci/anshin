package com.driezy.medlog.data.repository

import com.driezy.medlog.data.local.DrugDataSource
import com.driezy.medlog.data.model.Drug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrugRepositoryImpl @Inject constructor(private val dataSource: DrugDataSource) : DrugRepository {

    /** 内存缓存，应用生命周期内只加载一次 */
    @Volatile private var cache: List<Drug>? = null
    private val cacheMutex = Mutex()

    override suspend fun getAllDrugs(): List<Drug> = cache ?: cacheMutex.withLock {
        cache ?: dataSource.loadAllDrugs().also { cache = it }
    }

    override suspend fun suggestDrugNames(query: String, language: String, includeTcm: Boolean): List<Drug> =
        withContext(Dispatchers.Default) {
            val text = query.trim().lowercase()
            val drugs = getAllDrugs().filter { includeTcm || !it.isTcm }
            val collator = Collator.getInstance(Locale.forLanguageTag(language))
            if (text.isEmpty()) {
                drugs.filter { it.isSuggestionStarter && (language == "zh" || language in it.localizedNames) }
                    .sortedWith(compareBy(collator) { it.localizedName(language) })
            } else {
                drugs.mapNotNull { drug ->
                    drug.nameSuggestionRank(text, language).takeIf { it > 0 }?.let { drug to it }
                }.sortedWith(
                    compareByDescending<Pair<Drug, Int>> { it.second }
                        .thenByDescending { it.first.hasLocalizedName(language) }
                        .thenBy(collator) { it.first.localizedName(language) },
                ).map { it.first }
            }.take(8)
        }

    override suspend fun getCategories(isTcm: Boolean?): List<String> = getAllDrugs()
        .let { list -> if (isTcm != null) list.filter { it.isTcm == isTcm } else list }
        .map { it.category }
        .distinct()
        .sorted()
}
