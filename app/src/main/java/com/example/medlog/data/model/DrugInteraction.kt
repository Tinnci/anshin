package com.example.medlog.data.model

// ─── 严重程度 ─────────────────────────────────────────────────────────────────

enum class InteractionSeverity {
    HIGH,       // 禁止/重大风险 → 红色
    MODERATE,   // 需监测 → 橙色
    LOW,        // 注意 → 黄色
}

// ─── 单条相互作用检测结果 ─────────────────────────────────────────────────────

data class DrugInteraction(
    val drugA: String,          // 药品 A 名称
    val drugB: String,          // 药品 B 名称
    val severity: InteractionSeverity,
    val description: String,    // 中文说明
    val advice: String,         // 建议措施
)

// ─── 匹配规则：通过路径关键词 或 药名关键词匹配一类药 ────────────────────────

data class InteractionRule(
    /** 分组 A 的匹配条件：药名或 fullPath 包含以下任一关键词即符合 */
    val groupA: List<String>,
    /** 分组 B 的匹配条件 */
    val groupB: List<String>,
    val severity: InteractionSeverity,
    val description: String,
    val advice: String,
    /** 描述 A 类药物的通用名，用于展示 */
    val labelA: String,
    /** 描述 B 类药物的通用名，用于展示 */
    val labelB: String,
)
