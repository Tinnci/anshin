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

    fun generateLocalInsights(context: HealthInsightContext, locale: String = "zh-CN"): List<HealthInsight> {
        val rawInsights = if (context.metrics.isEmpty()) {
            listOf(
                HealthInsight(
                    id = "tracking-start",
                    kind = HealthInsightKind.TRACKING,
                    severity = HealthInsightSeverity.INFO,
                    title = "先建立记录节奏",
                    body = "从血压、体重或血糖中选一个常用指标开始记录，连续 3 次后就能看到趋势。",
                ),
            )
        } else {
            buildList {
                context.metrics.firstOrNull { it.type == HealthType.BLOOD_PRESSURE }?.let { addAll(bpInsights(it)) }
                context.metrics.firstOrNull { it.type == HealthType.BLOOD_GLUCOSE }?.let { addAll(glucoseInsights(it)) }
                context.metrics.firstOrNull {
                    it.type == HealthType.WEIGHT
                }?.let { addAll(weightInsights(it, context.bmi)) }
                context.metrics.firstOrNull { it.type == HealthType.BODY_FAT }?.let { addAll(bodyFatInsights(it)) }
                context.metrics.firstOrNull { it.type == HealthType.HEART_RATE }?.let { addAll(heartRateInsights(it)) }
                context.metrics.firstOrNull {
                    it.type == HealthType.TEMPERATURE
                }?.let { addAll(temperatureInsights(it)) }
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
        }

        return rawInsights
            .distinctBy { it.id }
            .sortedWith(compareBy<HealthInsight> { it.severity.rank }.thenBy { it.kind.ordinal })
            .take(4)
            .map { it.localize(locale, context.bmi) }
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

    private fun HealthInsight.localize(locale: String, bmi: Double?): HealthInsight {
        val lang = locale.lowercase(Locale.ROOT)
        return when {
            lang.startsWith("en") -> localizeEn(bmi)
            lang.startsWith("ja") -> localizeJa(bmi)
            lang.startsWith("ko") -> localizeKo(bmi)
            else -> this
        }
    }

    private fun HealthInsight.localizeEn(bmi: Double?): HealthInsight {
        val bmiStr = (bmi ?: 24.0).fmt()
        val (localizedTitle, localizedBody) = when (id) {
            "tracking-start" ->
                "Build a tracking routine" to
                    "Pick one key metric like blood pressure, weight, or glucose. Log 3 readings to start seeing trends."
            "tracking-cadence" ->
                "Keep measurement timing consistent" to
                    "Log the same metric at a similar time each day, such as morning weight or fasting glucose."
            "ocr-low-confidence-review" ->
                "Review low-confidence OCR readings" to
                    "Some trends include low-confidence OCR data; double-check values so scan errors don't skew trends."
            "bp-crisis" ->
                "Very high blood pressure reading" to
                    "Rest quietly and recheck. If still near 180/120 mmHg or with chest pain or shortness of breath, seek urgent care."
            "bp-high" ->
                "Blood pressure above target" to
                    "When readings stay elevated, note measurement timing and medications to share with your doctor."
            "bp-rising" ->
                "Blood pressure trending up" to
                    "Watch sleep, salt intake, stress, and missed doses; keep logging at consistent times to track changes."
            "glucose-high" ->
                "High blood glucose reading" to
                    "Tag fasting or post-meal context; if readings stay high or you feel unwell, follow your care plan."
            "glucose-low" ->
                "Low blood glucose reading" to
                    "If you feel shaky, sweaty, or dizzy, follow your doctor's low-glucose advice and note possible triggers."
            "glucose-rising" ->
                "Blood glucose trending up" to
                    "Separate fasting, post-meal, and post-exercise logs so different contexts aren't mixed in one trend."
            "weight-rising" ->
                "Weight trending up" to
                    "Check whether measurements are taken at the same time; compare morning weight with diet and activity."
            "bmi-attention" ->
                "BMI worth watching" to
                    "Current BMI is ~$bmiStr. Use it as a long-term reference alongside waist size, body fat, and medical advice."
            "body-fat-high" ->
                "Review body fat alongside weight" to
                    "When body fat is elevated, track body fat, waist circumference, and activity together rather than weight alone."
            "body-fat-rising" ->
                "Body fat trending up" to
                    "Measure on the same device at similar times; short-term shifts often reflect hydration or exercise."
            "heart-rate-high" ->
                "Elevated heart rate reading" to
                    "Rest quietly and recheck; if it stays fast or comes with dizziness or shortness of breath, consult a doctor."
            "heart-rate-low" ->
                "Low heart rate reading" to
                    "If not taken during sleep or athletic training and you feel fatigued or dizzy, consult your doctor promptly."
            "heart-rate-rising" ->
                "Heart rate trending up" to
                    "Note caffeine, sleep, stress, fever, or recent exercise; log resting readings for clearer comparisons."
            "temperature-fever" ->
                "Temperature suggests fever" to
                    "Recheck after an interval and log symptoms; seek care if high fever persists or breathing feels difficult."
            "temperature-low" ->
                "Low body temperature reading" to
                    "Confirm sensor placement and recheck; seek medical care if still low with chills or confusion."
            "spo2-urgent-low" ->
                "Critically low blood oxygen" to
                    "Warm your fingers, reposition the sensor, and recheck; if still below 90% or short of breath, seek urgent care."
            "spo2-low" ->
                "Low blood oxygen reading" to
                    "Recheck at rest and log any symptoms; consult a doctor if it stays below your usual baseline."
            else -> return this
        }
        return copy(title = localizedTitle, body = localizedBody)
    }

    private fun HealthInsight.localizeJa(bmi: Double?): HealthInsight {
        val bmiStr = (bmi ?: 24.0).fmt()
        val (localizedTitle, localizedBody) = when (id) {
            "tracking-start" ->
                "まずは記録のリズムを作る" to
                    "血圧・体重・血糖から1つ選び、3回続けて記録すると変化の傾向が見えてきます。"
            "tracking-cadence" ->
                "測定タイミングを揃える" to
                    "起床後の体重や食前血糖など、毎日近い時間帯に記録すると傾向がより正確になります。"
            "ocr-low-confidence-review" ->
                "一部のOCR読取値を確認" to
                    "低信頼度のOCRデータが含まれています。保存前後に数値を見直すと誤認識を防げます。"
            "bp-crisis" ->
                "血圧が非常に高い値です" to
                    "安静にして再測定してください。180/120 mmHg前後が続く場合や胸痛・息切れがある場合は直ちに受診してください。"
            "bp-high" ->
                "血圧が目標値より高め" to
                    "高めが続く場合は測定時刻・体調・服薬状況を記録し、診察時に医師へ共有してください。"
            "bp-rising" ->
                "血圧に上昇傾向があります" to
                    "睡眠・塩分・ストレス・飲み忘れに注意し、同じ時間帯で記録を続けて変化を確認しましょう。"
            "glucose-high" ->
                "血糖値が高めです" to
                    "空腹時か食後かを記録し、高値が続く場合や体調不良時は医師の指示に従ってください。"
            "glucose-low" ->
                "血糖値が低めです" to
                    "動悸・発汗・手の震えなどの低血糖症状がある場合は医師の指示に従い、原因もメモしましょう。"
            "glucose-rising" ->
                "血糖値に上昇傾向があります" to
                    "空腹時・食後・運動後を区別して記録すると、条件の違いによるばらつきを防げます。"
            "weight-rising" ->
                "体重に上昇傾向があります" to
                    "測定時刻が揃っているか確認し、朝の体重が続く場合は食事・運動・むくみも合わせて観察しましょう。"
            "bmi-attention" ->
                "BMIの推移に注意" to
                    "現在のBMIは約$bmiStr です。長期の目安として腹囲や体脂肪率、医師の助言と合わせて確認しましょう。"
            "body-fat-high" ->
                "体脂肪率は体重と合わせて確認" to
                    "体脂肪率が高めの時は体重だけでなく、体脂肪・腹囲・運動習慣を継続して記録すると参考になります。"
            "body-fat-rising" ->
                "体脂肪率に上昇傾向があります" to
                    "同じ機器・同じ時間帯で測定しましょう。短期的な変動は水分量や運動の影響を受けることがあります。"
            "heart-rate-high" ->
                "心拍数が速めです" to
                    "安静にして再測定してください。速い状態が続く場合や息切れ・めまいがある場合は医師に相談してください。"
            "heart-rate-low" ->
                "心拍数が遅めです" to
                    "睡眠中や運動習慣によるものでなく、だるさやめまいを伴う場合は早めに医師へ相談してください。"
            "heart-rate-rising" ->
                "心拍数に上昇傾向があります" to
                    "カフェイン・睡眠・ストレス・発熱・運動の影響に留意し、安静時に記録して比較しましょう。"
            "temperature-fever" ->
                "発熱の可能性があります" to
                    "時間を空けて再測定し症状を記録してください。高熱や息苦しさがある場合は早めに受診してください。"
            "temperature-low" ->
                "体温が低めです" to
                    "測定方法を確認して再測定し、悪寒や意識のぼんやり感を伴う場合は速やかに受診してください。"
            "spo2-urgent-low" ->
                "血中酸素が著しく低下" to
                    "指先を温めて装着し直し再測定してください。90%未満が続く場合や息苦しさがある場合は直ちに受診してください。"
            "spo2-low" ->
                "血中酸素がやや低めです" to
                    "安静時に再測定して症状を記録し、普段より低い状態が続く場合は医師に相談してください。"
            else -> return this
        }
        return copy(title = localizedTitle, body = localizedBody)
    }

    private fun HealthInsight.localizeKo(bmi: Double?): HealthInsight {
        val bmiStr = (bmi ?: 24.0).fmt()
        val (localizedTitle, localizedBody) = when (id) {
            "tracking-start" ->
                "측정 기록 습관 만들기" to
                    "혈압, 체중, 혈당 중 자주 확인하는 지표를 골라 3번 연속 기록하면 추세를 볼 수 있습니다."
            "tracking-cadence" ->
                "측정 시간대를 일정하게 유지" to
                    "기상 직후 체중이나 식전 혈당처럼 비슷한 시간대에 기록하면 추세가 훨씬 정확해집니다."
            "ocr-low-confidence-review" ->
                "일부 OCR 인식 결과 확인 권장" to
                    "낮은 신뢰도의 OCR 데이터가 포함되어 있습니다. 저장 전후 값을 한 번 확인해 인식 오차를 줄이세요."
            "bp-crisis" ->
                "혈압 수치가 매우 높습니다" to
                    "안정을 취한 뒤 다시 측정하세요. 180/120 mmHg 이상이거나 흉통·호흡곤란이 있으면 즉시 진료를 받으세요."
            "bp-high" ->
                "혈압이 목표치보다 높습니다" to
                    "높은 수치가 반복되면 측정 시각, 상태, 복약 여부를 기록해 진료 시 의료진에게 보여주세요."
            "bp-rising" ->
                "최근 혈압 상승 추세" to
                    "수면, 염분, 스트레스, 복약 누락을 점검하고 같은 시간대에 꾸준히 기록해 변화를 살피세요."
            "glucose-high" ->
                "혈당 수치가 높습니다" to
                    "공복·식후 상태를 함께 기록하고, 높은 수치가 반복되거나 불편감이 있으면 의료진 지침을 따르세요."
            "glucose-low" ->
                "혈당 수치가 낮습니다" to
                    "두근거림, 식은땀, 손떨림 등 저혈당 증상이 있으면 의료진 권고에 따라 대처하고 원인을 기록하세요."
            "glucose-rising" ->
                "최근 혈당 상승 추세" to
                    "공복, 식후, 운동 후 기록을 구분해 서로 다른 상황의 수치가 한 추세에 섞이지 않게 하세요."
            "weight-rising" ->
                "최근 체중 상승 추세" to
                    "측정 시각이 일정한지 확인하고, 아침 체중이 계속 오르면 식단·운동·부종 여부를 함께 살피세요."
            "bmi-attention" ->
                "BMI 추세 확인 필요" to
                    "현재 BMI는 약 $bmiStr 입니다. 장기 추세 참고용으로 허리둘레, 체지방률, 의료진 조언과 함께 보세요."
            "body-fat-high" ->
                "체지방률은 체중과 함께 확인" to
                    "체지방률이 높을 때는 체중만 보기보다 체지방, 허리둘레, 운동 기록을 함께 남기는 것이 좋습니다."
            "body-fat-rising" ->
                "최근 체지방률 상승 추세" to
                    "같은 기기와 비슷한 시간대에 측정하세요. 단기 변동은 수분 섭취나 운동 영향을 받을 수 있습니다."
            "heart-rate-high" ->
                "심박수가 빠르게 측정됨" to
                    "안정 상태에서 다시 측정하세요. 계속 빠르거나 답답함·호흡곤란·어지럼증이 있으면 진료받으세요."
            "heart-rate-low" ->
                "심박수가 느리게 측정됨" to
                    "수면 중이나 운동선수 기준이 아닌데 피로감·어지럼증이 동반되면 빠르게 의료진과 상담하세요."
            "heart-rate-rising" ->
                "최근 심박수 상승 추세" to
                    "카페인, 수면, 스트레스, 발열, 운동 시간을 살피고 비교를 위해 안정 상태에서 기록하세요."
            "temperature-fever" ->
                "체온이 발열을 시사합니다" to
                    "시간을 두고 다시 측정하며 증상을 기록하세요. 고열이 지속되거나 호흡이 힘들면 즉시 진료받으세요."
            "temperature-low" ->
                "체온 수치가 낮습니다" to
                    "측정 부위와 기기를 확인해 다시 재고, 오한이나 의식 저하가 동반되면 즉시 진료를 받으세요."
            "spo2-urgent-low" ->
                "혈중 산소포화도 매우 낮음" to
                    "손가락을 따뜻하게 하고 다시 측정하세요. 계속 90% 미만이거나 숨이 차면 즉시 응급 진료를 받으세요."
            "spo2-low" ->
                "혈중 산소포화도가 낮습니다" to
                    "안정 상태에서 다시 측정하고 증상을 기록하세요. 평소보다 낮은 상태가 지속되면 의사와 상담하세요."
            else -> return this
        }
        return copy(title = localizedTitle, body = localizedBody)
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
