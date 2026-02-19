package com.example.medlog.data.repository

import com.example.medlog.data.local.DrugDataSource
import com.example.medlog.data.model.Drug
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrugRepositoryImpl @Inject constructor(
    private val dataSource: DrugDataSource,
) : DrugRepository {

    /** 内存缓存，应用生命周期内只加载一次 */
    private var cache: List<Drug>? = null

    override suspend fun getAllDrugs(): List<Drug> =
        cache ?: dataSource.loadAllDrugs().also { cache = it }

    override suspend fun searchDrugs(query: String): List<Drug> {
        val all = getAllDrugs()
        return if (query.isBlank()) all
        else all.filter { it.matches(query) }
    }

    override suspend fun getCategories(isTcm: Boolean?): List<String> =
        getAllDrugs()
            .let { list -> if (isTcm != null) list.filter { it.isTcm == isTcm } else list }
            .map { it.category }
            .distinct()
            .sorted()
}
