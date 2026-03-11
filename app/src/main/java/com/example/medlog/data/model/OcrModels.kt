package com.example.medlog.data.model

/**
 * 从 OCR 文本中解析出的健康体征数据。
 */
data class ParsedHealthMetric(
    val type: HealthType,
    val value: Double,
    val secondaryValue: Double? = null,
    /** 匹配到的原始文本片段 */
    val rawText: String,
    /** 识别置信度 0.0~1.0（综合匹配模式、单位关键词、值合理性） */
    val confidence: Float = 0f,
)

/**
 * OCR 文本中提取的候选数字（未判定类型）。
 */
data class ExtractedNumber(
    val value: Double,
    /** 可能的血压分量伴随值（如 120/80 中的 80） */
    val pairedValue: Double? = null,
    val rawText: String,
    /** 候选数字的合理性置信度 0.0~1.0 */
    val confidence: Float = 0f,
)

/**
 * OCR 解析综合结果。
 */
data class OcrParseResult(
    /** 结构化匹配的体征指标（有明确关键词/单位） */
    val metrics: List<ParsedHealthMetric>,
    /** 候选数字（OCR 文本中所有合理数字，去除已被结构化匹配消费的） */
    val candidates: List<ExtractedNumber>,
    /** 原始 OCR 文本行 */
    val rawTexts: List<String>,
)
