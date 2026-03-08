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
 * OCR 文本中提取的候选数字（未判定类型）。
 */
data class ExtractedNumber(
    val value: Double,
    /** 可能的血压分量伴随值（如 120/80 中的 80） */
    val pairedValue: Double? = null,
    val rawText: String,
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

/**
 * 健康体征智能识别器：从 OCR 识别到的文本行中提取血压、心率、血糖、体温、体重、血氧等指标。
 *
 * 支持中/英文混合识别，适用于血压计、血糖仪、体温枪等设备屏幕拍照场景。
 */
object HealthMetricParser {

    // ── 血压 ─────────────────────────────────────────────────────────────────
    private val BP_SLASH = Regex(
        """(?:(?:bp|血压|blood\s*pressure)\s*[:：]?\s*)?(\d{2,3})\s*[/／]\s*(\d{2,3})\s*(?:mmHg|mmhg|毫米汞柱)?""",
        RegexOption.IGNORE_CASE,
    )
    private val BP_SYS_DIA = Regex(
        """(?:sys(?:tolic)?|收缩压|高压)\s*[:：]?\s*(\d{2,3})\s+(?:dia(?:stolic)?|舒张压|低压)\s*[:：]?\s*(\d{2,3})""",
        RegexOption.IGNORE_CASE,
    )
    /** 两个 mmHg 值："120mmHg 80mmHg" / "120 毫米汞柱 80 毫米汞柱" */
    private val BP_MMHG_PAIR = Regex(
        """(\d{2,3})\s*(?:mmHg|mmhg|毫米汞柱)\s*[,，/／\s]\s*(\d{2,3})\s*(?:mmHg|mmhg|毫米汞柱)?""",
        RegexOption.IGNORE_CASE,
    )
    /** 带血压关键词 + 空格分隔："血压 120 80" */
    private val BP_KEYWORD_SPACE = Regex(
        """(?:bp|血压|blood\s*pressure)\s*[:：]?\s*(\d{2,3})\s+(\d{2,3})""",
        RegexOption.IGNORE_CASE,
    )
    /** 裸空格分隔（无关键词，需严格验证）："120 80" */
    private val BP_BARE_SPACE = Regex(
        """(?<![/／\d])(\d{2,3})\s+(\d{2,3})(?![/／\d])""",
    )
    /** 逗号分隔："120,80" / "120，80" */
    private val BP_COMMA = Regex(
        """(?:(?:bp|血压|blood\s*pressure)\s*[:：]?\s*)?(\d{2,3})\s*[,，]\s*(\d{2,3})\s*(?:mmHg|mmhg|毫米汞柱)?""",
        RegexOption.IGNORE_CASE,
    )
    /** kPa 单位血压："16.0/10.7 kPa" (1 kPa = 7.5 mmHg) */
    private val BP_KPA = Regex(
        """(\d{1,2}(?:\.\d{1,2})?)\s*[/／]\s*(\d{1,2}(?:\.\d{1,2})?)\s*kPa""",
        RegexOption.IGNORE_CASE,
    )

    // ── 心率 ─────────────────────────────────────────────────────────────────
    private val HR = Regex(
        """(?:(?:hr|heart\s*rate|pulse|pr|pulse\s*rate|心率|脉搏|脈搏|脉率)\s*[:：]?\s*)(\d{2,3})\s*(?:bpm|次/分|次／分)?""",
        RegexOption.IGNORE_CASE,
    )
    private val HR_BPM = Regex(
        """(\d{2,3})\s*bpm""",
        RegexOption.IGNORE_CASE,
    )
    /** 中文单位："72次/分钟" */
    private val HR_CN_UNIT = Regex(
        """(\d{2,3})\s*次[/／每]分钟?""",
    )

    // ── 血糖 ─────────────────────────────────────────────────────────────────
    private val GLU = Regex(
        """(?:(?:glu(?:cose)?|blood\s*sugar|血糖|bs)\s*[:：]?\s*)(\d{1,2}(?:\.\d{1,2})?)\s*(?:mmol/L|mmol／L|mg/dL|mg／dL)?""",
        RegexOption.IGNORE_CASE,
    )
    private val GLU_MMOL = Regex(
        """(\d{1,2}\.\d{1,2})\s*mmol[/／]L""",
        RegexOption.IGNORE_CASE,
    )
    private val GLU_MGDL = Regex(
        """(\d{2,3}(?:\.\d)?)\s*mg[/／]dL""",
        RegexOption.IGNORE_CASE,
    )
    /** 中文单位："5.6毫摩尔/升" */
    private val GLU_CN = Regex(
        """(\d{1,2}(?:\.\d{1,2})?)\s*毫摩尔[/／每]升""",
    )

    // ── 体温 ─────────────────────────────────────────────────────────────────
    private val TEMP = Regex(
        """(?:(?:temp(?:erature)?|体温|體溫)\s*[:：]?\s*)(\d{2,3}(?:\.\d{1,2})?)\s*[°℃℉]?[CF]?""",
        RegexOption.IGNORE_CASE,
    )
    private val TEMP_C = Regex(
        """(\d{2}\.\d{1,2})\s*[°℃]C?""",
    )
    private val TEMP_F = Regex(
        """(\d{2,3}\.\d{1,2})\s*[°℉]?F""",
        RegexOption.IGNORE_CASE,
    )
    /** 中文单位："36.5摄氏度" */
    private val TEMP_CN = Regex(
        """(\d{2}\.\d{1,2})\s*摄氏度""",
    )

    // ── 体重 ─────────────────────────────────────────────────────────────────
    private val WEIGHT = Regex(
        """(?:(?:weight|体重|體重|wt)\s*[:：]?\s*)(\d{2,3}(?:\.\d{1,2})?)\s*(?:kg|千克|lbs?|磅)?""",
        RegexOption.IGNORE_CASE,
    )
    private val WEIGHT_KG = Regex(
        """(\d{2,3}(?:\.\d{1,2})?)\s*kg""",
        RegexOption.IGNORE_CASE,
    )
    private val WEIGHT_LB = Regex(
        """(\d{2,3}(?:\.\d{1,2})?)\s*lbs?""",
        RegexOption.IGNORE_CASE,
    )
    /** 中文单位："65千克" / "65公斤" */
    private val WEIGHT_CN = Regex(
        """(\d{2,3}(?:\.\d{1,2})?)\s*(?:千克|公斤)""",
    )
    /** 中文斤→千克："130斤" (1斤=0.5kg) */
    private val WEIGHT_JIN = Regex(
        """(\d{2,3}(?:\.\d{1,2})?)\s*斤""",
    )

    // ── 血氧 ─────────────────────────────────────────────────────────────────
    private val SPO2 = Regex(
        """(?:(?:sp\s*o\s*2|spo₂|血氧|oxygen\s*saturation)\s*[:：]?\s*)(\d{2,3})\s*%?""",
        RegexOption.IGNORE_CASE,
    )
    /** 纯百分比在血氧范围内："98%" */
    private val SPO2_PERCENT = Regex(
        """(\d{2,3})\s*%""",
    )

    // ── 通用数字提取 ─────────────────────────────────────────────────────────
    private val NUMBER = Regex("""(\d{1,3}(?:\.\d{1,2})?)""")
    private val SLASH_PAIR = Regex("""(\d{2,3})\s*[/／]\s*(\d{2,3})""")

    // ── OCR 文本预处理 ───────────────────────────────────────────────────────

    /** OCR 常见误识别字符：字母→数字 */
    private val LETTER_TO_DIGIT = Regex("""(?<=\d)[OoQD](?=[\d/／.%])|(?<=\d)[OoQD](?=\s|$)|(?<=[/／])[OoQD](?=\d)""")
    private val LETTER_L_TO_1 = Regex("""(?<=\d)[lI|](?=[\d/／.%])|(?<=\d)[lI|](?=\s|$)|(?<=[/／])[lI|](?=\d)""")
    /** 七段数码管常见误识别：段缺失导致的字符混淆 */
    private val SEVEN_SEG_FIXES = listOf(
        // 字母被识别为数字或反过来
        Regex("""(?<=\d)[Bb](?=[\d/／\s]|$)""") to "8",     // B→8
        Regex("""(?<=\d)[Gg](?=[\d/／\s]|$)""") to "9",     // G→9
        Regex("""(?<=[\d/／])[Ss](?=[\d/／\s]|$)""") to "5", // S→5
        Regex("""(?<=\d)[Zz](?=[\d/／\s]|$)""") to "2",   // Z→2
        // 七段管特有的符号误识别
        Regex("""(?<=\d)[_\-](?=\d)""") to "",             // 段间杂划去除
        Regex("""\[""") to "1",                            // [ → 1（七段管 1 有时像 [）
        Regex("""\]""") to "1",                            // ] → 1
    )
    /** OCR 单字符数字前缀合并："1 20" → "120"（不合并 "120 80" 这类多位数对） */
    private val SPACE_MERGE_PREFIX = Regex("""(?<!\d)(\d)\s+(\d)""")
    /** OCR 单字符数字后缀合并："12 0" → "120" */
    private val SPACE_MERGE_SUFFIX = Regex("""(\d)\s+(\d)(?!\d)""")
    /** 日期时间模式（排除提取） */
    private val DATE_TIME = Regex(
        """\d{4}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4}|\d{1,2}:\d{2}(:\d{2})?""",
    )

    /**
     * 从 OCR 文本行列表中提取健康体征指标。
     * 返回去重后的结果（相同类型仅保留第一条匹配）。
     */
    fun parse(texts: List<String>): List<ParsedHealthMetric> =
        parseAll(texts).metrics

    /**
     * OCR 文本预清洗：修正常见 OCR 误识别字符。
     * - O/o/Q/D 在数字上下文中 → 0
     * - l/I/| 在数字上下文中 → 1
     * - 七段数码管常见误识别修正（B→8, S→5, Z→2 等）
     * - 数字间的空格合并（"1 20" → "120"）
     */
    fun cleanOcrText(text: String): String {
        var result = text
        result = LETTER_TO_DIGIT.replace(result, "0")
        result = LETTER_L_TO_1.replace(result, "1")
        // 七段数码管特有的误识别修正
        for ((pattern, replacement) in SEVEN_SEG_FIXES) {
            result = pattern.replace(result, replacement)
        }
        // 循环合并数字间空格（仅合并单字符侧，保留 "120 80" 这类多位数对）
        var prev: String
        do {
            prev = result
            result = SPACE_MERGE_PREFIX.replace(result) { "${it.groupValues[1]}${it.groupValues[2]}" }
            result = SPACE_MERGE_SUFFIX.replace(result) { "${it.groupValues[1]}${it.groupValues[2]}" }
        } while (result != prev)
        return result
    }

    /**
     * 全量解析：返回结构化匹配 + 候选数字 + 原始文本。
     * 自动对输入文本进行 OCR 预清洗。
     */
    fun parseAll(texts: List<String>): OcrParseResult {
        val cleaned = texts.map { cleanOcrText(it) }
        val metrics = mutableListOf<ParsedHealthMetric>()
        val foundTypes = mutableSetOf<HealthType>()
        val joined = cleaned.joinToString(" ")

        for (text in cleaned + listOf(joined)) {
            if (HealthType.BLOOD_PRESSURE !in foundTypes) {
                findBloodPressure(text)?.let {
                    metrics.add(it); foundTypes.add(HealthType.BLOOD_PRESSURE)
                }
            }
            if (HealthType.HEART_RATE !in foundTypes) {
                findHeartRate(text)?.let {
                    metrics.add(it); foundTypes.add(HealthType.HEART_RATE)
                }
            }
            if (HealthType.BLOOD_GLUCOSE !in foundTypes) {
                findBloodGlucose(text)?.let {
                    metrics.add(it); foundTypes.add(HealthType.BLOOD_GLUCOSE)
                }
            }
            if (HealthType.TEMPERATURE !in foundTypes) {
                findTemperature(text)?.let {
                    metrics.add(it); foundTypes.add(HealthType.TEMPERATURE)
                }
            }
            if (HealthType.WEIGHT !in foundTypes) {
                findWeight(text)?.let {
                    metrics.add(it); foundTypes.add(HealthType.WEIGHT)
                }
            }
            if (HealthType.SPO2 !in foundTypes) {
                findSpO2(text)?.let {
                    metrics.add(it); foundTypes.add(HealthType.SPO2)
                }
            }
        }

        // 提取候选数字（排除已被结构化匹配消费的值）
        val consumedValues = metrics.flatMap { m ->
            listOfNotNull(m.value, m.secondaryValue)
        }.toSet()
        val candidates = extractNumbers(cleaned).filter { n ->
            n.value !in consumedValues && (n.pairedValue == null || n.pairedValue !in consumedValues)
        }

        return OcrParseResult(metrics, candidates, texts)
    }

    /**
     * 从 OCR 文本中提取所有合理的数字（无需关键词/单位匹配）。
     * 用于在结构化解析失败时提供候选值供用户选择。
     * 自动排除日期/时间中的数字。
     */
    fun extractNumbers(texts: List<String>): List<ExtractedNumber> {
        val results = mutableListOf<ExtractedNumber>()
        val seen = mutableSetOf<String>()

        for (text in texts) {
            // 去除日期/时间部分，避免误提取
            val cleaned = DATE_TIME.replace(text, " ")
            // 优先匹配 a/b 格式（可能是血压）
            SLASH_PAIR.findAll(cleaned).forEach { m ->
                val key = m.value
                if (key !in seen) {
                    seen.add(key)
                    val a = m.groupValues[1].toDoubleOrNull()
                    val b = m.groupValues[2].toDoubleOrNull()
                    if (a != null && b != null) {
                        results.add(ExtractedNumber(a, b, m.value))
                    }
                }
            }
            // 提取独立数字
            NUMBER.findAll(cleaned).forEach { m ->
                val key = m.value
                if (key !in seen) {
                    seen.add(key)
                    m.groupValues[1].toDoubleOrNull()?.let { v ->
                        results.add(ExtractedNumber(v, rawText = m.value))
                    }
                }
            }
        }
        return results
    }

    /**
     * 根据目标类型的合理范围，判断一个数字是否适合该类型。
     */
    fun isValuePlausible(value: Double, type: HealthType): Boolean = when (type) {
        HealthType.BLOOD_PRESSURE -> value in 50.0..300.0
        HealthType.HEART_RATE     -> value in 20.0..250.0
        HealthType.BLOOD_GLUCOSE  -> value in 1.0..40.0
        HealthType.TEMPERATURE    -> value in 30.0..45.0
        HealthType.WEIGHT         -> value in 10.0..500.0
        HealthType.SPO2           -> value in 50.0..100.0
    }

    private fun findBloodPressure(text: String): ParsedHealthMetric? {
        // 优先级 1：斜杠分隔 "120/80"
        BP_SLASH.find(text)?.let { m ->
            val sys = m.groupValues[1].toDoubleOrNull() ?: return@let
            val dia = m.groupValues[2].toDoubleOrNull() ?: return@let
            if (sys in 50.0..300.0 && dia in 20.0..200.0 && sys > dia) {
                return ParsedHealthMetric(HealthType.BLOOD_PRESSURE, sys, dia, m.value)
            }
        }
        // 优先级 2：mmHg 配对 "120mmHg 80mmHg"
        BP_MMHG_PAIR.find(text)?.let { m ->
            val sys = m.groupValues[1].toDoubleOrNull() ?: return@let
            val dia = m.groupValues[2].toDoubleOrNull() ?: return@let
            if (sys in 50.0..300.0 && dia in 20.0..200.0 && sys > dia) {
                return ParsedHealthMetric(HealthType.BLOOD_PRESSURE, sys, dia, m.value)
            }
        }
        // 优先级 3：SYS/DIA 标签
        BP_SYS_DIA.find(text)?.let { m ->
            val sys = m.groupValues[1].toDoubleOrNull() ?: return@let
            val dia = m.groupValues[2].toDoubleOrNull() ?: return@let
            if (sys in 50.0..300.0 && dia in 20.0..200.0 && sys > dia) {
                return ParsedHealthMetric(HealthType.BLOOD_PRESSURE, sys, dia, m.value)
            }
        }
        // 优先级 4：关键词 + 空格分隔 "血压 120 80"
        BP_KEYWORD_SPACE.find(text)?.let { m ->
            val sys = m.groupValues[1].toDoubleOrNull() ?: return@let
            val dia = m.groupValues[2].toDoubleOrNull() ?: return@let
            if (sys in 50.0..300.0 && dia in 20.0..200.0 && sys > dia) {
                return ParsedHealthMetric(HealthType.BLOOD_PRESSURE, sys, dia, m.value)
            }
        }
        // 优先级 5：逗号分隔 "120,80"
        BP_COMMA.find(text)?.let { m ->
            val sys = m.groupValues[1].toDoubleOrNull() ?: return@let
            val dia = m.groupValues[2].toDoubleOrNull() ?: return@let
            if (sys in 50.0..300.0 && dia in 20.0..200.0 && sys > dia) {
                return ParsedHealthMetric(HealthType.BLOOD_PRESSURE, sys, dia, m.value)
            }
        }
        // 优先级 6：kPa 单位 "16.0/10.7 kPa" (1 kPa = 7.5 mmHg)
        BP_KPA.find(text)?.let { m ->
            val sysKpa = m.groupValues[1].toDoubleOrNull() ?: return@let
            val diaKpa = m.groupValues[2].toDoubleOrNull() ?: return@let
            val sys = sysKpa * 7.5
            val dia = diaKpa * 7.5
            if (sys in 50.0..300.0 && dia in 20.0..200.0 && sys > dia) {
                return ParsedHealthMetric(
                    HealthType.BLOOD_PRESSURE,
                    (sys * 10).toLong() / 10.0,
                    (dia * 10).toLong() / 10.0,
                    m.value,
                )
            }
        }
        // 优先级 7：裸空格分隔 "120 80"（需更严格的脉压差验证）
        BP_BARE_SPACE.find(text)?.let { m ->
            val sys = m.groupValues[1].toDoubleOrNull() ?: return@let
            val dia = m.groupValues[2].toDoubleOrNull() ?: return@let
            val pulsePressure = sys - dia
            if (sys in 70.0..250.0 && dia in 30.0..150.0 && sys > dia && pulsePressure in 15.0..120.0) {
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
        HR_CN_UNIT.find(text)?.let { m ->
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
        GLU_MMOL.find(text)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (v in 1.0..40.0) return ParsedHealthMetric(HealthType.BLOOD_GLUCOSE, v, rawText = m.value)
        }
        GLU_CN.find(text)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (v in 1.0..40.0) return ParsedHealthMetric(HealthType.BLOOD_GLUCOSE, v, rawText = m.value)
        }
        // mg/dL → mmol/L 转换
        GLU_MGDL.find(text)?.let { m ->
            val mgdl = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (mgdl in 18.0..720.0) {
                val mmol = mgdl / 18.0
                return ParsedHealthMetric(
                    HealthType.BLOOD_GLUCOSE,
                    (mmol * 10).toLong() / 10.0, // 保留一位小数
                    rawText = m.value,
                )
            }
        }
        return null
    }

    private fun findTemperature(text: String): ParsedHealthMetric? {
        // 优先匹配摄氏度
        TEMP_C.find(text)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (v in 30.0..45.0) return ParsedHealthMetric(HealthType.TEMPERATURE, v, rawText = m.value)
        }
        TEMP_CN.find(text)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (v in 30.0..45.0) return ParsedHealthMetric(HealthType.TEMPERATURE, v, rawText = m.value)
        }
        // 华氏度 → 摄氏度
        TEMP_F.find(text)?.let { m ->
            val f = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (f in 86.0..113.0) {
                val c = (f - 32) * 5.0 / 9.0
                return ParsedHealthMetric(
                    HealthType.TEMPERATURE,
                    (c * 10).toLong() / 10.0,
                    rawText = m.value,
                )
            }
        }
        TEMP.find(text)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (v in 30.0..45.0) return ParsedHealthMetric(HealthType.TEMPERATURE, v, rawText = m.value)
        }
        return null
    }

    private fun findWeight(text: String): ParsedHealthMetric? {
        WEIGHT_KG.find(text)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (v in 10.0..500.0) return ParsedHealthMetric(HealthType.WEIGHT, v, rawText = m.value)
        }
        WEIGHT_CN.find(text)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (v in 10.0..500.0) return ParsedHealthMetric(HealthType.WEIGHT, v, rawText = m.value)
        }
        // 斤 → kg 转换（1斤 = 0.5kg）
        WEIGHT_JIN.find(text)?.let { m ->
            val jin = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (jin in 20.0..1000.0) {
                val kg = jin * 0.5
                return ParsedHealthMetric(
                    HealthType.WEIGHT,
                    (kg * 10).toLong() / 10.0,
                    rawText = m.value,
                )
            }
        }
        // lbs → kg 转换
        WEIGHT_LB.find(text)?.let { m ->
            val lb = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (lb in 22.0..1100.0) {
                val kg = lb * 0.453592
                return ParsedHealthMetric(
                    HealthType.WEIGHT,
                    (kg * 10).toLong() / 10.0,
                    rawText = m.value,
                )
            }
        }
        WEIGHT.find(text)?.let { m ->
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
        // 纯百分比（无关键词）在 SpO2 合理范围内
        SPO2_PERCENT.find(text)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return@let
            if (v in 80.0..100.0) return ParsedHealthMetric(HealthType.SPO2, v, rawText = m.value)
        }
        return null
    }
}
