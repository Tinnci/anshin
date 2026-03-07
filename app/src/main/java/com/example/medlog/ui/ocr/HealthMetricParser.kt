package com.example.medlog.ui.ocr

import com.example.medlog.data.model.HealthType

/**
 * 从 OCR 文本中解析出的健康体征数据。
 */
data class ParsedHealthMetric(
    val type: HealthType,
    val value: Double,
    val secondaryValue: Double? = null,
    /** 匹配到的原始文本片段 */
    val rawText: String,
)

/**
 * 健康体征智能识别器：从 OCR 识别到的文本行中提取血压、心率、血糖、体温、体重、血氧等指标。
 *
 * 支持中/英文混合识别，适用于血压计、血糖仪、体温枪等设备屏幕拍照场景。
 */
object HealthMetricParser {

    // ── 血压 ─────────────────────────────────────────────────────────────────
    // 匹配 "120/80"、"120／80"、"120/80mmHg"、"BP120/80" 等
    private val BP_SLASH = Regex(
        """(?:(?:bp|血压|blood\s*pressure)\s*[:：]?\s*)?(\d{2,3})\s*[/／]\s*(\d{2,3})\s*(?:mmHg|mmhg)?""",
        RegexOption.IGNORE_CASE,
    )
    // 匹配 "SYS 120 DIA 80" 或 "收缩压120 舒张压80"
    private val BP_SYS_DIA = Regex(
        """(?:sys(?:tolic)?|收缩压)\s*[:：]?\s*(\d{2,3})\s+(?:dia(?:stolic)?|舒张压)\s*[:：]?\s*(\d{2,3})""",
        RegexOption.IGNORE_CASE,
    )

    // ── 心率 ─────────────────────────────────────────────────────────────────
    private val HR = Regex(
        """(?:(?:hr|heart\s*rate|pulse|心率|脉搏|脈搏)\s*[:：]?\s*)(\d{2,3})\s*(?:bpm|次/分|次／分)?""",
        RegexOption.IGNORE_CASE,
    )
    // 独立的 "72bpm" 或 "72 bpm"
    private val HR_BPM = Regex(
        """(\d{2,3})\s*bpm""",
        RegexOption.IGNORE_CASE,
    )

    // ── 血糖 ─────────────────────────────────────────────────────────────────
    private val GLU = Regex(
        """(?:(?:glu(?:cose)?|blood\s*sugar|血糖|bs)\s*[:：]?\s*)(\d{1,2}(?:\.\d{1,2})?)\s*(?:mmol/L|mmol／L)?""",
        RegexOption.IGNORE_CASE,
    )
    // 独立的 "5.6mmol/L" 或 "5.6 mmol/L"
    private val GLU_UNIT = Regex(
        """(\d{1,2}\.\d{1,2})\s*mmol[/／]L""",
        RegexOption.IGNORE_CASE,
    )

    // ── 体温 ─────────────────────────────────────────────────────────────────
    private val TEMP = Regex(
        """(?:(?:temp(?:erature)?|体温|體溫)\s*[:：]?\s*)(\d{2}(?:\.\d{1,2})?)\s*[°℃]?C?""",
        RegexOption.IGNORE_CASE,
    )
    // 独立的 "36.5°C" 或 "36.5℃"
    private val TEMP_UNIT = Regex(
        """(\d{2}\.\d{1,2})\s*[°℃]C?""",
    )

    // ── 体重 ─────────────────────────────────────────────────────────────────
    private val WEIGHT = Regex(
        """(?:(?:weight|体重|體重|wt)\s*[:：]?\s*)(\d{2,3}(?:\.\d{1,2})?)\s*(?:kg|千克)?""",
        RegexOption.IGNORE_CASE,
    )
    // 独立的 "65kg" 或 "65.5 kg"
    private val WEIGHT_UNIT = Regex(
        """(\d{2,3}(?:\.\d{1,2})?)\s*kg""",
        RegexOption.IGNORE_CASE,
    )

    // ── 血氧 ─────────────────────────────────────────────────────────────────
    private val SPO2 = Regex(
        """(?:(?:sp\s*o\s*2|spo₂|血氧|oxygen\s*saturation)\s*[:：]?\s*)(\d{2,3})\s*%?""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 从 OCR 文本行列表中提取健康体征指标。
     * 返回去重后的结果（相同类型仅保留第一条匹配）。
     */
    fun parse(texts: List<String>): List<ParsedHealthMetric> {
        val results = mutableListOf<ParsedHealthMetric>()
        val foundTypes = mutableSetOf<HealthType>()
        val joined = texts.joinToString(" ")

        // 对合并文本做全量匹配，提升跨行指标检出率
        for (text in texts + listOf(joined)) {
            // 血压
            if (HealthType.BLOOD_PRESSURE !in foundTypes) {
                findBloodPressure(text)?.let {
                    results.add(it)
                    foundTypes.add(HealthType.BLOOD_PRESSURE)
                }
            }
            // 心率
            if (HealthType.HEART_RATE !in foundTypes) {
                findHeartRate(text)?.let {
                    results.add(it)
                    foundTypes.add(HealthType.HEART_RATE)
                }
            }
            // 血糖
            if (HealthType.BLOOD_GLUCOSE !in foundTypes) {
                findBloodGlucose(text)?.let {
                    results.add(it)
                    foundTypes.add(HealthType.BLOOD_GLUCOSE)
                }
            }
            // 体温
            if (HealthType.TEMPERATURE !in foundTypes) {
                findTemperature(text)?.let {
                    results.add(it)
                    foundTypes.add(HealthType.TEMPERATURE)
                }
            }
            // 体重
            if (HealthType.WEIGHT !in foundTypes) {
                findWeight(text)?.let {
                    results.add(it)
                    foundTypes.add(HealthType.WEIGHT)
                }
            }
            // 血氧
            if (HealthType.SPO2 !in foundTypes) {
                findSpO2(text)?.let {
                    results.add(it)
                    foundTypes.add(HealthType.SPO2)
                }
            }
        }
        return results
    }

    private fun findBloodPressure(text: String): ParsedHealthMetric? {
        BP_SLASH.find(text)?.let { m ->
            val sys = m.groupValues[1].toDoubleOrNull() ?: return@let
            val dia = m.groupValues[2].toDoubleOrNull() ?: return@let
            if (sys in 50.0..300.0 && dia in 20.0..200.0 && sys > dia) {
                return ParsedHealthMetric(HealthType.BLOOD_PRESSURE, sys, dia, m.value)
            }
        }
        BP_SYS_DIA.find(text)?.let { m ->
            val sys = m.groupValues[1].toDoubleOrNull() ?: return@let
            val dia = m.groupValues[2].toDoubleOrNull() ?: return@let
            if (sys in 50.0..300.0 && dia in 20.0..200.0 && sys > dia) {
                return ParsedHealthMetric(HealthType.BLOOD_PRESSURE, sys, dia, m.value)
            }
        }
        return null
    }

    private fun findHeartRate(text: String): ParsedHealthMetric? {
        HR.find(text)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (v in 20.0..250.0) return ParsedHealthMetric(HealthType.HEART_RATE, v, rawText = m.value)
        }
        HR_BPM.find(text)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (v in 20.0..250.0) return ParsedHealthMetric(HealthType.HEART_RATE, v, rawText = m.value)
        }
        return null
    }

    private fun findBloodGlucose(text: String): ParsedHealthMetric? {
        GLU.find(text)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (v in 1.0..40.0) return ParsedHealthMetric(HealthType.BLOOD_GLUCOSE, v, rawText = m.value)
        }
        GLU_UNIT.find(text)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (v in 1.0..40.0) return ParsedHealthMetric(HealthType.BLOOD_GLUCOSE, v, rawText = m.value)
        }
        return null
    }

    private fun findTemperature(text: String): ParsedHealthMetric? {
        TEMP.find(text)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (v in 30.0..45.0) return ParsedHealthMetric(HealthType.TEMPERATURE, v, rawText = m.value)
        }
        TEMP_UNIT.find(text)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (v in 30.0..45.0) return ParsedHealthMetric(HealthType.TEMPERATURE, v, rawText = m.value)
        }
        return null
    }

    private fun findWeight(text: String): ParsedHealthMetric? {
        WEIGHT.find(text)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (v in 10.0..500.0) return ParsedHealthMetric(HealthType.WEIGHT, v, rawText = m.value)
        }
        WEIGHT_UNIT.find(text)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (v in 10.0..500.0) return ParsedHealthMetric(HealthType.WEIGHT, v, rawText = m.value)
        }
        return null
    }

    private fun findSpO2(text: String): ParsedHealthMetric? {
        SPO2.find(text)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (v in 50.0..100.0) return ParsedHealthMetric(HealthType.SPO2, v, rawText = m.value)
        }
        return null
    }
}
