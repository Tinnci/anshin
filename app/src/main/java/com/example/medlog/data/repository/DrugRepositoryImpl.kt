package com.example.medlog.data.repository

import com.example.medlog.data.local.DrugDataSource
import com.example.medlog.data.model.Drug
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrugRepositoryImpl @Inject constructor(
    private val dataSource: DrugDataSource,
) : DrugRepository {

    /** 内存缓存，应用生命周期内只加载一次 */
    @Volatile private var cache: List<Drug>? = null
    private val cacheMutex = Mutex()

    override suspend fun getAllDrugs(): List<Drug> =
        cache ?: cacheMutex.withLock {
            cache ?: dataSource.loadAllDrugs().also { cache = it }
        }

    override suspend fun searchDrugs(query: String): List<Drug> {
        val all = getAllDrugs()
        return if (query.isBlank()) all
        else all.filter { it.matches(query) }
    }

    override suspend fun searchDrugsRanked(query: String): List<Drug> {
        val all = getAllDrugs()
        if (query.isBlank()) return all
        return all
            .map { drug -> drug to drug.relevanceScore(query) }
            .filter { (_, score) -> score > 0f }
            .sortedByDescending { (_, score) -> score }
            .map { (drug, _) -> drug }
    }

    override suspend fun getCategories(isTcm: Boolean?): List<String> =
        getAllDrugs()
            .let { list -> if (isTcm != null) list.filter { it.isTcm == isTcm } else list }
            .map { it.category }
            .distinct()
            .sorted()
}
