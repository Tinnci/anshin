package com.example.medlog.data.model

/**
 * 药品数据库实体（只读，来自 JSON 资产，不存储在 Room DB）
 */
data class Drug(
    val name: String,
    val category: String,       // 顶级分类，如"消化道及代谢"
    val fullPath: String = "",  // 完整分类路径
    val isTcm: Boolean = false,
    val initial: String = "#",  // 拼音首字母 (A-Z or #)
    val tags: List<String> = emptyList(),
    val isCompound: Boolean = false,
) {
    /** 预小写化，用于高性能搜索 */
    val nameLower: String = name.lowercase()
    val categoryLower: String = category.lowercase()

    fun matches(query: String): Boolean {
        if (query.isEmpty()) return true
        val q = query.lowercase()
        return nameLower.contains(q) ||
               categoryLower.contains(q) ||
               fullPath.lowercase().contains(q)
    }
}
