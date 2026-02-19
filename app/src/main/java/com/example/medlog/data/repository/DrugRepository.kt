package com.example.medlog.data.repository

import com.example.medlog.data.model.Drug

interface DrugRepository {
    /** 获取全部药品列表（西药 + 中成药） */
    suspend fun getAllDrugs(): List<Drug>

    /** 按关键词搜索药品（名称 / 分类） */
    suspend fun searchDrugs(query: String): List<Drug>

    /**
     * 模糊 + 语义搜索，并按相关性降序排序返回。
     * 当 query 为空时返回全部药品。
     */
    suspend fun searchDrugsRanked(query: String): List<Drug>

    /** 获取顶级分类列表 */
    suspend fun getCategories(isTcm: Boolean? = null): List<String>
}
