package com.example.medlog.data.model

/**
 * 药品数据库实体（只读，来自 JSON 资产，不存储在 Room DB）
 */
data class Drug(
    val name: String,
    val category: String,           // 顶级分类，如"消化道及代谢"
    val fullPath: String = "",      // 主分类路径（第一条路径）
    val allPaths: List<String> = emptyList(), // 全部分类路径（复方/多效药可能有多条）
    val isTcm: Boolean = false,
    val initial: String = "#",      // 拼音首字母 (A-Z or #)
    val tags: List<String> = emptyList(),
    val isCompound: Boolean = false,
) {
    /** 预小写化，用于高性能搜索 */
    val nameLower: String = name.lowercase()
    val categoryLower: String = category.lowercase()
    val tagsLower: List<String> = tags.map { it.lowercase() }
    val allPathsLower: List<String> = allPaths.map { it.lowercase() }

    /** 精确/包含匹配（保留向后兼容） */
    fun matches(query: String): Boolean {
        if (query.isEmpty()) return true
        val q = query.lowercase()
        return nameLower.contains(q) ||
               categoryLower.contains(q) ||
               fullPath.lowercase().contains(q) ||
               allPathsLower.any { it.contains(q) } ||
               tagsLower.any { it.contains(q) }
    }

    /**
     * 模糊 + 语义相关性评分（0.0 ~ 1.0）
     *
     * 得分层级（由高到低）：
     *   1.00 - 名称完全相等
     *   0.92 - 名称前缀匹配
     *   0.80 - 名称包含
     *   0.72 - 标签完全相等（语义：如"降糖""高血压"）
     *   0.62 - 标签包含
     *   0.55 - 分类包含
     *   0.45 - 全分类路径包含
     *   0.XX - 名称的 bigram 相似度（模糊匹配）
     *   0.00 - 无匹配
     */
    fun relevanceScore(query: String): Float {
        if (query.isBlank()) return 0f
        val q = query.lowercase().trim()

        if (nameLower == q) return 1.00f
        if (nameLower.startsWith(q)) return 0.92f
        if (nameLower.contains(q)) return 0.80f

        // 语义搜索：按标签匹配（如"降压""降糖""消炎"）
        if (tagsLower.any { it == q }) return 0.72f
        if (tagsLower.any { it.contains(q) }) return 0.62f

        if (categoryLower.contains(q)) return 0.55f
        if (fullPath.lowercase().contains(q)) return 0.45f
        if (allPathsLower.any { it.contains(q) }) return 0.40f

        // 模糊匹配：字符 bigram 相似度
        val bigram = bigramSimilarity(nameLower, q)
        if (bigram >= 0.25f) return bigram * 0.60f

        return 0f
    }
}

/**
 * Sørensen-Dice 系数的字符 bigram 实现
 * 适合中文（中文固定 1-gram → 自动降级为 1-gram）
 */
private fun bigramSimilarity(a: String, b: String): Float {
    if (a.isEmpty() || b.isEmpty()) return 0f
    val n = if (a.any { it.code > 0x00FF }) 1 else 2   // 中文用 unigram，英文用 bigram
    val aSet = a.windowed(n, 1, partialWindows = false).toSet().ifEmpty { setOf(a) }
    val bSet = b.windowed(n, 1, partialWindows = false).toSet().ifEmpty { setOf(b) }
    val intersection = aSet.intersect(bSet).size
    return (2.0f * intersection) / (aSet.size + bSet.size)
}
