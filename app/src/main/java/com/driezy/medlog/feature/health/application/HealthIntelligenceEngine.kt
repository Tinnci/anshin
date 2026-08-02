package com.driezy.medlog.feature.health.application

import com.driezy.medlog.capability.ai.AiChatMessage
import com.driezy.medlog.capability.ai.AiChatRequest
import com.driezy.medlog.data.model.HealthRecord
import com.driezy.medlog.data.model.HealthRecordSource
import com.driezy.medlog.data.model.HealthType
import java.util.Locale
import kotlin.math.abs

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val LOW_OCR_CONFIDENCE = 0.7f

data class HealthInsightContext(
    val generatedAtMillis: Long,
    val metrics: List<HealthMetricInsight>,
    val bmi: Double?,
    val userHeightCm: Float,
) {
    fun toPromptContext(): String {
        val metricLines = metrics.joinToString(separator = "\n") { metric ->
            buildString {
                append("- ${metric.type.name}: latest=${metric.latestDisplay}")
                metric.avg7d?.let { append(", avg7d=${it.fmt()}") }
                metric.min7d?.let { append(", min7d=${it.fmt()}") }
                metric.max7d?.let { append(", max7d=${it.fmt()}") }
                append(", count7d=${metric.count7d}")
                append(", trend=${metric.trend.name}")
                append(", abnormal=${metric.isAbnormal}")
                append(", sourceMix=${metric.sourceMix.name}")
                append(", manualCount=${metric.manualCount}")
                append(", localOcrCount=${metric.localOcrCount}")
                append(", cloudOcrCount=${metric.cloudOcrCount}")
                append(", lowConfidenceCount=${metric.lowConfidenceCount}")
            }
        }
        return buildString {
            appendLine("generatedAtMillis=$generatedAtMillis")
            appendLine("heightCm=${if (userHeightCm > 0f) userHeightCm.fmt() else "unknown"}")
            appendLine("BMI=${bmi?.fmt() ?: "unknown"}")
            appendLine("metrics:")
            append(metricLines.ifBlank { "- none" })
        }
    }
}

data class HealthMetricInsight(
    val type: HealthType,
    val latestValue: Double,
    val latestSecondaryValue: Double?,
    val latestTimestamp: Long,
    val latestDisplay: String,
    val avg7d: Double?,
    val min7d: Double?,
    val max7d: Double?,
    val count7d: Int,
    val trend: HealthTrend,
    val isAbnormal: Boolean,
    val sourceMix: HealthInsightSourceMix,
    val manualCount: Int,
    val localOcrCount: Int,
    val cloudOcrCount: Int,
    val importCount: Int,
    val lowConfidenceCount: Int,
)

enum class HealthTrend {
    RISING,
    FALLING,
    STABLE,
    INSUFFICIENT,
}

enum class HealthInsightSourceMix {
    UNKNOWN,
    MANUAL_ONLY,
    LOCAL_OCR_ONLY,
    CLOUD_OCR_ONLY,
    IMPORT_ONLY,
    MIXED,
}

data class HealthInsight(
    val id: String,
    val kind: HealthInsightKind,
    val severity: HealthInsightSeverity,
    val title: String,
    val body: String,
    val relatedType: HealthType? = null,
)

enum class HealthInsightKind {
    SAFETY,
    TREND,
    TRACKING,
    ROUTINE,
}

enum class HealthInsightSeverity {
    INFO,
    WARNING,
    URGENT,
}

data class HealthPromptProfile(
    val locale: String = "zh-CN",
    val maxInsights: Int = 4,
    val tone: String = "calm, concise, non-alarming",
)

object HealthIntelligenceEngine {

    fun buildContext(records: List<HealthRecord>, userHeightCm: Float, nowMillis: Long): HealthInsightContext {
        val sevenDaysAgo = nowMillis - 7 * DAY_MS
        val metrics = records
            .groupBy { HealthType.fromName(it.type) }
            .mapNotNull { (type, typeRecords) ->
                val latest = typeRecords.maxByOrNull { it.timestamp } ?: return@mapNotNull null
                val recent = typeRecords
                    .filter { it.timestamp in sevenDaysAgo..nowMillis }
                    .sortedBy { it.timestamp }
                val sourceRecords = recent.ifEmpty { listOf(latest) }
                val manualCount = sourceRecords.count { it.source == HealthRecordSource.MANUAL }
                val localOcrCount = sourceRecords.count { it.source == HealthRecordSource.LOCAL_OCR }
                val cloudOcrCount = sourceRecords.count { it.source == HealthRecordSource.CLOUD_OCR }
                val importCount = sourceRecords.count { it.source == HealthRecordSource.IMPORT }
                val lowConfidenceCount = sourceRecords.count {
                    it.source in setOf(HealthRecordSource.LOCAL_OCR, HealthRecordSource.CLOUD_OCR) &&
                        (it.sourceConfidence ?: 1f) < LOW_OCR_CONFIDENCE
                }
                HealthMetricInsight(
                    type = type,
                    latestValue = latest.value,
                    latestSecondaryValue = latest.secondaryValue,
                    latestTimestamp = latest.timestamp,
                    latestDisplay = type.formatValue(latest.value, latest.secondaryValue),
                    avg7d = recent.takeIf { it.size >= 2 }?.map { it.value }?.average(),
                    min7d = recent.takeIf { it.isNotEmpty() }?.minOf { it.value },
                    max7d = recent.takeIf { it.isNotEmpty() }?.maxOf { it.value },
                    count7d = recent.size,
                    trend = calculateTrend(type, recent),
                    isAbnormal = isAbnormal(type, latest.value, latest.secondaryValue),
                    sourceMix = sourceMix(
                        manualCount = manualCount,
                        localOcrCount = localOcrCount,
                        cloudOcrCount = cloudOcrCount,
                        importCount = importCount,
                    ),
                    manualCount = manualCount,
                    localOcrCount = localOcrCount,
                    cloudOcrCount = cloudOcrCount,
                    importCount = importCount,
                    lowConfidenceCount = lowConfidenceCount,
                )
            }
            .sortedBy { it.type.ordinal }

        val weight = metrics.firstOrNull { it.type == HealthType.WEIGHT }
        val bmi = if (weight != null && userHeightCm > 0f) {
            HealthType.calculateBmi(weight.latestValue, userHeightCm.toDouble())
        } else {
            null
        }

        return HealthInsightContext(
            generatedAtMillis = nowMillis,
            metrics = metrics,
            bmi = bmi,
            userHeightCm = userHeightCm,
        )
    }

    fun generateLocalInsights(context: HealthInsightContext): List<HealthInsight> {
        if (context.metrics.isEmpty()) {
            return listOf(
                HealthInsight(
                    id = "tracking-start",
                    kind = HealthInsightKind.TRACKING,
                    severity = HealthInsightSeverity.INFO,
                    title = "先建立记录节奏",
                    body = "从血压、体重或血糖中选一个常用指标开始记录，连续 3 次后就能看到趋势。",
                ),
            )
        }

        val insights = buildList {
            context.metrics.firstOrNull { it.type == HealthType.BLOOD_PRESSURE }?.let { addAll(bpInsights(it)) }
            context.metrics.firstOrNull { it.type == HealthType.BLOOD_GLUCOSE }?.let { addAll(glucoseInsights(it)) }
            context.metrics.firstOrNull {
                it.type == HealthType.WEIGHT
            }?.let { addAll(weightInsights(it, context.bmi)) }
            context.metrics.firstOrNull { it.type == HealthType.BODY_FAT }?.let { addAll(bodyFatInsights(it)) }
            context.metrics.firstOrNull { it.type == HealthType.HEART_RATE }?.let { addAll(heartRateInsights(it)) }
            context.metrics.firstOrNull { it.type == HealthType.TEMPERATURE }?.let { addAll(temperatureInsights(it)) }
            context.metrics.firstOrNull { it.type == HealthType.SPO2 }?.let { addAll(spO2Insights(it)) }
            if (context.metrics.any { it.count7d in 1..2 }) {
                add(
                    HealthInsight(
                        id = "tracking-cadence",
                        kind = HealthInsightKind.ROUTINE,
                        severity = HealthInsightSeverity.INFO,
                        title = "数据还不够稳定",
                        body = "尽量在相近时间记录同一指标，例如晨起体重或餐前血糖，趋势会更可靠。",
                    ),
                )
            }
            if (context.metrics.any { it.lowConfidenceCount > 0 }) {
                add(
                    HealthInsight(
                        id = "ocr-low-confidence-review",
                        kind = HealthInsightKind.TRACKING,
                        severity = HealthInsightSeverity.INFO,
                        title = "有些识别记录建议复核",
                        body = "部分趋势包含低置信 OCR 数据；保存前后可以核对一次数值，避免把识别误差当成健康变化。",
                    ),
                )
            }
        }

        return insights
            .distinctBy { it.id }
            .sortedWith(compareBy<HealthInsight> { it.severity.rank }.thenBy { it.kind.ordinal })
            .take(4)
    }

    private fun bpInsights(metric: HealthMetricInsight): List<HealthInsight> {
        val sys = metric.latestValue
        val dia = metric.latestSecondaryValue ?: return emptyList()
        return when {
            sys >= 180 || dia >= 120 -> listOf(
                HealthInsight(
                    id = "bp-crisis",
                    kind = HealthInsightKind.SAFETY,
                    severity = HealthInsightSeverity.URGENT,
                    title = "血压读数偏高",
                    body = "先安静复测一次；如果仍接近或超过 180/120 mmHg，或伴随胸痛、气促、剧烈头痛，请立即就医或急诊。",
                    relatedType = HealthType.BLOOD_PRESSURE,
                ),
            )
            sys >= 140 || dia >= 90 -> listOf(
                HealthInsight(
                    id = "bp-high",
                    kind = HealthInsightKind.SAFETY,
                    severity = HealthInsightSeverity.WARNING,
                    title = "血压高于常用目标",
                    body = "连续几次偏高时，建议记录测量时间、状态和用药情况，并在复诊时给医生查看。",
                    relatedType = HealthType.BLOOD_PRESSURE,
                ),
            )
            metric.trend == HealthTrend.RISING -> listOf(
                HealthInsight(
                    id = "bp-rising",
                    kind = HealthInsightKind.TREND,
                    severity = HealthInsightSeverity.INFO,
                    title = "血压最近有上升趋势",
                    body = "留意睡眠、盐分、压力和漏服药情况；继续按相同时间段记录，方便判断是否持续。",
                    relatedType = HealthType.BLOOD_PRESSURE,
                ),
            )
            else -> emptyList()
        }
    }

    private fun glucoseInsights(metric: HealthMetricInsight): List<HealthInsight> = when {
        metric.latestValue >= 13.9 -> listOf(
            HealthInsight(
                id = "glucose-high",
                kind = HealthInsightKind.SAFETY,
                severity = HealthInsightSeverity.WARNING,
                title = "血糖读数偏高",
                body = "请标记空腹或餐后状态；若多次明显偏高、出现不适，或医生给过个体化阈值，请按医嘱处理。",
                relatedType = HealthType.BLOOD_GLUCOSE,
            ),
        )
        metric.latestValue < 3.9 -> listOf(
            HealthInsight(
                id = "glucose-low",
                kind = HealthInsightKind.SAFETY,
                severity = HealthInsightSeverity.WARNING,
                title = "血糖读数偏低",
                body = "如有心慌、出汗、手抖等低血糖表现，请按医生建议及时处理，并记录诱因。",
                relatedType = HealthType.BLOOD_GLUCOSE,
            ),
        )
        metric.trend == HealthTrend.RISING -> listOf(
            HealthInsight(
                id = "glucose-rising",
                kind = HealthInsightKind.TREND,
                severity = HealthInsightSeverity.INFO,
                title = "血糖最近有上升趋势",
                body = "建议区分空腹、餐后和运动后记录，避免把不同场景混在同一趋势里解读。",
                relatedType = HealthType.BLOOD_GLUCOSE,
            ),
        )
        else -> emptyList()
    }

    private fun weightInsights(metric: HealthMetricInsight, bmi: Double?): List<HealthInsight> = buildList {
        if (metric.trend == HealthTrend.RISING) {
            add(
                HealthInsight(
                    id = "weight-rising",
                    kind = HealthInsightKind.TREND,
                    severity = HealthInsightSeverity.INFO,
                    title = "体重最近有上升趋势",
                    body = "优先确认测量时间是否一致；如果晨起体重持续上升，可结合饮食、运动和水肿情况观察。",
                    relatedType = HealthType.WEIGHT,
                ),
            )
        }
        if (bmi != null && bmi >= 24.0) {
            add(
                HealthInsight(
                    id = "bmi-attention",
                    kind = HealthInsightKind.ROUTINE,
                    severity = HealthInsightSeverity.INFO,
                    title = "BMI 需要留意",
                    body = "当前 BMI 约 ${bmi.fmt()}。它适合做长期趋势参考，建议结合腰围、体脂率和医生建议判断。",
                    relatedType = HealthType.WEIGHT,
                ),
            )
        }
    }

    private fun bodyFatInsights(metric: HealthMetricInsight): List<HealthInsight> = when {
        metric.latestValue >= 28.0 -> listOf(
            HealthInsight(
                id = "body-fat-high",
                kind = HealthInsightKind.ROUTINE,
                severity = HealthInsightSeverity.INFO,
                title = "体脂率可结合体重一起看",
                body = "体脂率偏高时，不建议只看体重变化；持续记录体脂、腰围和运动情况更有参考价值。",
                relatedType = HealthType.BODY_FAT,
            ),
        )
        metric.trend == HealthTrend.RISING -> listOf(
            HealthInsight(
                id = "body-fat-rising",
                kind = HealthInsightKind.TREND,
                severity = HealthInsightSeverity.INFO,
                title = "体脂率最近有上升趋势",
                body = "尽量使用同一设备、同一时间段测量；短期波动可能受饮水和运动影响。",
                relatedType = HealthType.BODY_FAT,
            ),
        )
        else -> emptyList()
    }

    private fun heartRateInsights(metric: HealthMetricInsight): List<HealthInsight> = when {
        metric.latestValue >= 110.0 -> listOf(
            HealthInsight(
                id = "heart-rate-high",
                kind = HealthInsightKind.SAFETY,
                severity = HealthInsightSeverity.WARNING,
                title = "心率读数偏快",
                body = "先在安静状态下复测；若持续偏快，或伴随胸闷、气短、头晕，请及时咨询医生。",
                relatedType = HealthType.HEART_RATE,
            ),
        )
        metric.latestValue < 50.0 -> listOf(
            HealthInsight(
                id = "heart-rate-low",
                kind = HealthInsightKind.SAFETY,
                severity = HealthInsightSeverity.WARNING,
                title = "心率读数偏慢",
                body = "如果不是运动员或睡眠状态读数，且伴随乏力、头晕或不适，建议尽快咨询医生。",
                relatedType = HealthType.HEART_RATE,
            ),
        )
        metric.trend == HealthTrend.RISING -> listOf(
            HealthInsight(
                id = "heart-rate-rising",
                kind = HealthInsightKind.TREND,
                severity = HealthInsightSeverity.INFO,
                title = "心率最近有上升趋势",
                body = "留意咖啡因、睡眠、压力、发热和运动时间；尽量在静息状态记录便于比较。",
                relatedType = HealthType.HEART_RATE,
            ),
        )
        else -> emptyList()
    }

    private fun temperatureInsights(metric: HealthMetricInsight): List<HealthInsight> = when {
        metric.latestValue >= 38.0 -> listOf(
            HealthInsight(
                id = "temperature-fever",
                kind = HealthInsightKind.SAFETY,
                severity = HealthInsightSeverity.WARNING,
                title = "体温提示发热",
                body = "建议间隔一段时间复测并记录症状；若高热不退、呼吸困难或精神状态差，请及时就医。",
                relatedType = HealthType.TEMPERATURE,
            ),
        )
        metric.latestValue < 35.5 -> listOf(
            HealthInsight(
                id = "temperature-low",
                kind = HealthInsightKind.SAFETY,
                severity = HealthInsightSeverity.WARNING,
                title = "体温读数偏低",
                body = "先确认测量方式和设备；若复测仍低且伴随寒战、意识不清或明显不适，请及时就医。",
                relatedType = HealthType.TEMPERATURE,
            ),
        )
        else -> emptyList()
    }

    private fun spO2Insights(metric: HealthMetricInsight): List<HealthInsight> = when {
        metric.latestValue < 90.0 -> listOf(
            HealthInsight(
                id = "spo2-urgent-low",
                kind = HealthInsightKind.SAFETY,
                severity = HealthInsightSeverity.URGENT,
                title = "血氧读数明显偏低",
                body = "请确认手指温暖、设备夹好后复测；若仍低于 90% 或有气促、胸闷，请立即就医。",
                relatedType = HealthType.SPO2,
            ),
        )
        metric.latestValue < 95.0 -> listOf(
            HealthInsight(
                id = "spo2-low",
                kind = HealthInsightKind.SAFETY,
                severity = HealthInsightSeverity.WARNING,
                title = "血氧读数偏低",
                body = "建议静息复测并记录症状；若持续低于平时水平，或伴随呼吸不适，请咨询医生。",
                relatedType = HealthType.SPO2,
            ),
        )
        else -> emptyList()
    }

    private fun calculateTrend(type: HealthType, records: List<HealthRecord>): HealthTrend {
        if (records.size < 3) return HealthTrend.INSUFFICIENT
        val half = records.size / 2
        val early = records.take(half).map { it.value }.average()
        val late = records.drop(half).map { it.value }.average()
        val delta = late - early
        return when {
            delta > type.trendThreshold -> HealthTrend.RISING
            delta < -type.trendThreshold -> HealthTrend.FALLING
            abs(delta) <= type.trendThreshold -> HealthTrend.STABLE
            else -> HealthTrend.INSUFFICIENT
        }
    }

    private fun sourceMix(
        manualCount: Int,
        localOcrCount: Int,
        cloudOcrCount: Int,
        importCount: Int,
    ): HealthInsightSourceMix {
        val nonZero = listOf(
            HealthInsightSourceMix.MANUAL_ONLY to manualCount,
            HealthInsightSourceMix.LOCAL_OCR_ONLY to localOcrCount,
            HealthInsightSourceMix.CLOUD_OCR_ONLY to cloudOcrCount,
            HealthInsightSourceMix.IMPORT_ONLY to importCount,
        ).filter { it.second > 0 }
        return when (nonZero.size) {
            0 -> HealthInsightSourceMix.UNKNOWN
            1 -> nonZero.first().first
            else -> HealthInsightSourceMix.MIXED
        }
    }

    private fun isAbnormal(type: HealthType, value: Double, secondaryValue: Double?): Boolean = when (type) {
        HealthType.BLOOD_PRESSURE ->
            secondaryValue?.let { value !in type.normalMin..type.normalMax || it !in 60.0..80.0 } ?: true
        HealthType.WEIGHT -> false
        else -> !type.isNormal(value)
    }

    private val HealthInsightSeverity.rank: Int
        get() = when (this) {
            HealthInsightSeverity.URGENT -> 0
            HealthInsightSeverity.WARNING -> 1
            HealthInsightSeverity.INFO -> 2
        }
}

object HealthInsightPromptBuilder {
    fun buildRequest(
        context: HealthInsightContext,
        profile: HealthPromptProfile = HealthPromptProfile(),
    ): AiChatRequest = AiChatRequest(
        messages = listOf(
            AiChatMessage.system(
                "你不是医生，不能诊断、开药或替代专业医疗建议。你只能基于用户记录的健康数据生成低风险、简短、可执行的观察建议。遇到危险读数必须建议复测并就医。",
            ),
            AiChatMessage.developer(
                """
                    Locale: ${profile.locale}
                    Tone: ${profile.tone}
                    Return JSON only. Shape:
                    {"insights":[{"kind":"SAFETY|TREND|TRACKING|ROUTINE","severity":"INFO|WARNING|URGENT","title":"...","body":"...","relatedType":"..."}]}
                    Rules:
                    - max ${profile.maxInsights} insights
                    - body <= 90 Chinese chars or equivalent length
                    - hide implementation details and never ask the user to chat
                    - do not mention diagnosis
                    - do not infer facts not present in context
                """.trimIndent(),
            ),
            AiChatMessage.user(context.toPromptContext()),
        ),
        temperature = 0.2,
        maxOutputTokens = 700,
    )
}

private fun Double.fmt(): String = String.format(Locale.US, "%.1f", this)

private fun Float.fmt(): String = String.format(Locale.US, "%.1f", this)
