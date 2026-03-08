package com.example.medlog.ui.ocr

import com.example.medlog.data.model.HealthType
import org.junit.Assert.*
import org.junit.Test

class HealthMetricParserTest {

    // ── 血压 ──────────────────────────────────────────────────────

    @Test
    fun `parse slash blood pressure`() {
        val result = HealthMetricParser.parse(listOf("120/80"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result[0].type)
        assertEquals(120.0, result[0].value, 0.01)
        assertEquals(80.0, result[0].secondaryValue!!, 0.01)
    }

    @Test
    fun `parse blood pressure with unit`() {
        val result = HealthMetricParser.parse(listOf("120/80mmHg"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result[0].type)
    }

    @Test
    fun `parse blood pressure with label`() {
        val result = HealthMetricParser.parse(listOf("血压 135/85"))
        assertEquals(1, result.size)
        assertEquals(135.0, result[0].value, 0.01)
        assertEquals(85.0, result[0].secondaryValue!!, 0.01)
    }

    @Test
    fun `parse SYS DIA format`() {
        val result = HealthMetricParser.parse(listOf("SYS 130 DIA 85"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result[0].type)
        assertEquals(130.0, result[0].value, 0.01)
        assertEquals(85.0, result[0].secondaryValue!!, 0.01)
    }

    @Test
    fun `parse chinese SYS DIA format`() {
        val result = HealthMetricParser.parse(listOf("收缩压120 舒张压80"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result[0].type)
    }

    @Test
    fun `reject invalid blood pressure`() {
        // sys must be > dia
        val result = HealthMetricParser.parse(listOf("60/120"))
        assertTrue(result.none { it.type == HealthType.BLOOD_PRESSURE })
    }

    // ── 心率 ──────────────────────────────────────────────────────

    @Test
    fun `parse heart rate with label`() {
        val result = HealthMetricParser.parse(listOf("心率 72"))
        assertEquals(1, result.size)
        assertEquals(HealthType.HEART_RATE, result[0].type)
        assertEquals(72.0, result[0].value, 0.01)
    }

    @Test
    fun `parse heart rate bpm`() {
        val result = HealthMetricParser.parse(listOf("75bpm"))
        assertEquals(1, result.size)
        assertEquals(HealthType.HEART_RATE, result[0].type)
        assertEquals(75.0, result[0].value, 0.01)
    }

    @Test
    fun `parse pulse label`() {
        val result = HealthMetricParser.parse(listOf("Pulse 68"))
        assertEquals(1, result.size)
        assertEquals(HealthType.HEART_RATE, result[0].type)
    }

    // ── 血糖 ──────────────────────────────────────────────────────

    @Test
    fun `parse blood glucose with label`() {
        val result = HealthMetricParser.parse(listOf("血糖 5.6"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_GLUCOSE, result[0].type)
        assertEquals(5.6, result[0].value, 0.01)
    }

    @Test
    fun `parse blood glucose with unit`() {
        val result = HealthMetricParser.parse(listOf("5.8mmol/L"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_GLUCOSE, result[0].type)
    }

    // ── 体温 ──────────────────────────────────────────────────────

    @Test
    fun `parse temperature with label`() {
        val result = HealthMetricParser.parse(listOf("体温 36.5"))
        assertEquals(1, result.size)
        assertEquals(HealthType.TEMPERATURE, result[0].type)
        assertEquals(36.5, result[0].value, 0.01)
    }

    @Test
    fun `parse temperature with degree symbol`() {
        val result = HealthMetricParser.parse(listOf("36.8℃"))
        assertEquals(1, result.size)
        assertEquals(HealthType.TEMPERATURE, result[0].type)
    }

    @Test
    fun `parse temperature with degree C`() {
        val result = HealthMetricParser.parse(listOf("37.1°C"))
        assertEquals(1, result.size)
        assertEquals(HealthType.TEMPERATURE, result[0].type)
    }

    // ── 体重 ──────────────────────────────────────────────────────

    @Test
    fun `parse weight with unit`() {
        val result = HealthMetricParser.parse(listOf("65.5kg"))
        assertEquals(1, result.size)
        assertEquals(HealthType.WEIGHT, result[0].type)
        assertEquals(65.5, result[0].value, 0.01)
    }

    @Test
    fun `parse weight with label`() {
        val result = HealthMetricParser.parse(listOf("体重 70"))
        assertEquals(1, result.size)
        assertEquals(HealthType.WEIGHT, result[0].type)
    }

    // ── 血氧 ──────────────────────────────────────────────────────

    @Test
    fun `parse spo2`() {
        val result = HealthMetricParser.parse(listOf("SpO2 98%"))
        assertEquals(1, result.size)
        assertEquals(HealthType.SPO2, result[0].type)
        assertEquals(98.0, result[0].value, 0.01)
    }

    @Test
    fun `parse spo2 chinese`() {
        val result = HealthMetricParser.parse(listOf("血氧 97"))
        assertEquals(1, result.size)
        assertEquals(HealthType.SPO2, result[0].type)
    }

    // ── 多指标组合 ────────────────────────────────────────────────

    @Test
    fun `parse multiple metrics from device screen`() {
        val texts = listOf(
            "SYS 135",
            "DIA 88",
            "Pulse 72",
            "mmHg",
        )
        val result = HealthMetricParser.parse(texts)
        // 心率（Pulse 72）和血压通过 joined text 检出
        assertTrue(result.any { it.type == HealthType.HEART_RATE })
        assertTrue(result.any { it.type == HealthType.BLOOD_PRESSURE })
    }

    @Test
    fun `parse multi-line blood pressure monitor output`() {
        val texts = listOf(
            "135/88mmHg",
            "心率 72bpm",
        )
        val result = HealthMetricParser.parse(texts)
        assertEquals(2, result.size)
        assertTrue(result.any { it.type == HealthType.BLOOD_PRESSURE })
        assertTrue(result.any { it.type == HealthType.HEART_RATE })
    }

    @Test
    fun `deduplicates same type`() {
        val texts = listOf("120/80", "130/90")
        val result = HealthMetricParser.parse(texts)
        // only first match is kept
        assertEquals(1, result.count { it.type == HealthType.BLOOD_PRESSURE })
        assertEquals(120.0, result[0].value, 0.01)
    }

    @Test
    fun `empty input returns empty list`() {
        val result = HealthMetricParser.parse(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `irrelevant text returns empty list`() {
        val result = HealthMetricParser.parse(listOf("阿莫西林胶囊", "每日三次"))
        assertTrue(result.isEmpty())
    }

    // ── mg/dL 血糖 ──────────────────────────────────────────────

    @Test
    fun `parse blood glucose in mg per dL`() {
        val result = HealthMetricParser.parse(listOf("126 mg/dL"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_GLUCOSE, result[0].type)
        assertEquals(7.0, result[0].value, 0.1)  // 126/18 = 7.0
    }

    // ── 华氏度体温 ──────────────────────────────────────────────

    @Test
    fun `parse temperature in Fahrenheit`() {
        val result = HealthMetricParser.parse(listOf("98.6°F"))
        assertEquals(1, result.size)
        assertEquals(HealthType.TEMPERATURE, result[0].type)
        assertEquals(37.0, result[0].value, 0.1)  // (98.6-32)*5/9 ≈ 37.0
    }

    // ── lbs 体重 ────────────────────────────────────────────────

    @Test
    fun `parse weight in lbs`() {
        val result = HealthMetricParser.parse(listOf("154 lbs"))
        assertEquals(1, result.size)
        assertEquals(HealthType.WEIGHT, result[0].type)
        assertEquals(69.8, result[0].value, 0.5)  // 154*0.453592 ≈ 69.8
    }

    // ── parseAll 三层结果 ──────────────────────────────────────

    @Test
    fun `parseAll returns structured metrics and candidates`() {
        val result = HealthMetricParser.parseAll(listOf("120/80mmHg", "心率72", "5.6", "36"))
        assertTrue(result.metrics.isNotEmpty())
        // 5.6 和 36 应出现在候选数字中（未被结构化匹配完全覆盖）
        assertTrue(result.candidates.isNotEmpty())
        assertEquals(4, result.rawTexts.size)
    }

    // ── extractNumbers ─────────────────────────────────────────

    @Test
    fun `extractNumbers finds standalone numbers`() {
        val numbers = HealthMetricParser.extractNumbers(listOf("135/88", "72", "5.6"))
        assertTrue(numbers.any { it.value == 135.0 && it.pairedValue == 88.0 })
        assertTrue(numbers.any { it.value == 72.0 && it.pairedValue == null })
        assertTrue(numbers.any { it.value == 5.6 })
    }

    // ── isValuePlausible ────────────────────────────────────────

    @Test
    fun `isValuePlausible validates ranges`() {
        assertTrue(HealthMetricParser.isValuePlausible(120.0, HealthType.BLOOD_PRESSURE))
        assertFalse(HealthMetricParser.isValuePlausible(5.0, HealthType.BLOOD_PRESSURE))
        assertTrue(HealthMetricParser.isValuePlausible(5.6, HealthType.BLOOD_GLUCOSE))
        assertTrue(HealthMetricParser.isValuePlausible(36.5, HealthType.TEMPERATURE))
        assertFalse(HealthMetricParser.isValuePlausible(200.0, HealthType.TEMPERATURE))
    }

    // ── cleanOcrText OCR 预清洗 ─────────────────────────────────

    @Test
    fun `cleanOcrText replaces O with 0 in numeric context`() {
        assertEquals("120/80", HealthMetricParser.cleanOcrText("12O/8O"))
    }

    @Test
    fun `cleanOcrText replaces l with 1 in numeric context`() {
        assertEquals("110", HealthMetricParser.cleanOcrText("1l0"))
    }

    @Test
    fun `cleanOcrText merges spaces in numbers`() {
        assertEquals("120/80", HealthMetricParser.cleanOcrText("1 20/8 0"))
    }

    @Test
    fun `cleanOcrText merges multiple spaces in numbers`() {
        assertEquals("120", HealthMetricParser.cleanOcrText("1 2 0"))
    }

    @Test
    fun `cleanOcrText preserves normal text`() {
        assertEquals("血压 120/80", HealthMetricParser.cleanOcrText("血压 120/80"))
    }

    @Test
    fun `parse handles OCR misrecognition O as 0`() {
        val result = HealthMetricParser.parse(listOf("12O/8O"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result[0].type)
        assertEquals(120.0, result[0].value, 0.01)
        assertEquals(80.0, result[0].secondaryValue!!, 0.01)
    }

    // ── 七段数码管误识别修正 ────────────────────────────────────

    @Test
    fun `cleanOcrText fixes B to 8 in 7-segment context`() {
        assertEquals("128/80", HealthMetricParser.cleanOcrText("12B/80"))
    }

    @Test
    fun `cleanOcrText fixes S to 5 in 7-segment context`() {
        assertEquals("125/85", HealthMetricParser.cleanOcrText("12S/8S"))
    }

    @Test
    fun `cleanOcrText fixes Z to 2 in 7-segment context`() {
        assertEquals("122/82", HealthMetricParser.cleanOcrText("12Z/8Z"))
    }

    @Test
    fun `cleanOcrText fixes bracket to 1`() {
        assertEquals("1", HealthMetricParser.cleanOcrText("["))
        assertEquals("1", HealthMetricParser.cleanOcrText("]"))
    }

    @Test
    fun `parse 7-segment blood pressure with B misrecognition`() {
        val result = HealthMetricParser.parse(listOf("13B/8B"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result[0].type)
        assertEquals(138.0, result[0].value, 0.01)
        assertEquals(88.0, result[0].secondaryValue!!, 0.01)
    }

    @Test
    fun `parse handles spaced numbers`() {
        val result = HealthMetricParser.parse(listOf("1 35/8 5"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result[0].type)
        assertEquals(135.0, result[0].value, 0.01)
    }

    // ── 空格分隔血压（新增） ──────────────────────────────────

    @Test
    fun `parse space-separated blood pressure`() {
        val result = HealthMetricParser.parse(listOf("120 71"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result[0].type)
        assertEquals(120.0, result[0].value, 0.01)
        assertEquals(71.0, result[0].secondaryValue!!, 0.01)
    }

    @Test
    fun `parse space-separated blood pressure 120 80`() {
        val result = HealthMetricParser.parse(listOf("120 80"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result[0].type)
        assertEquals(120.0, result[0].value, 0.01)
        assertEquals(80.0, result[0].secondaryValue!!, 0.01)
    }

    @Test
    fun `parse blood pressure with keyword and space`() {
        val result = HealthMetricParser.parse(listOf("血压 120 80"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result[0].type)
        assertEquals(120.0, result[0].value, 0.01)
        assertEquals(80.0, result[0].secondaryValue!!, 0.01)
    }

    @Test
    fun `parse mmHg pair blood pressure`() {
        val result = HealthMetricParser.parse(listOf("120mmHg 80mmHg"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result[0].type)
        assertEquals(120.0, result[0].value, 0.01)
        assertEquals(80.0, result[0].secondaryValue!!, 0.01)
    }

    @Test
    fun `parse mmHg pair with spaces`() {
        val result = HealthMetricParser.parse(listOf("120 mmHg 80 mmHg"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result[0].type)
    }

    @Test
    fun `parse chinese mmHg unit`() {
        val result = HealthMetricParser.parse(listOf("120毫米汞柱 80毫米汞柱"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result[0].type)
        assertEquals(120.0, result[0].value, 0.01)
        assertEquals(80.0, result[0].secondaryValue!!, 0.01)
    }

    @Test
    fun `parse separate mmHg lines joined`() {
        val result = HealthMetricParser.parse(listOf("120 mmHg", "80 mmHg"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result[0].type)
    }

    @Test
    fun `reject bare space pair with invalid pulse pressure`() {
        // 120 and 115 - pulse pressure too small (5 < 15)
        val result = HealthMetricParser.parse(listOf("120 115"))
        assertTrue(result.none { it.type == HealthType.BLOOD_PRESSURE })
    }

    @Test
    fun `parse comma-separated blood pressure`() {
        val result = HealthMetricParser.parse(listOf("120,80"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result[0].type)
        assertEquals(120.0, result[0].value, 0.01)
        assertEquals(80.0, result[0].secondaryValue!!, 0.01)
    }

    @Test
    fun `parse chinese comma-separated blood pressure`() {
        val result = HealthMetricParser.parse(listOf("血压 120，80"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result[0].type)
    }

    @Test
    fun `parse kPa blood pressure`() {
        val result = HealthMetricParser.parse(listOf("16.0/10.7 kPa"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result[0].type)
        assertEquals(120.0, result[0].value, 0.5)  // 16.0 * 7.5 = 120
        assertEquals(80.2, result[0].secondaryValue!!, 0.5)  // 10.7 * 7.5 ≈ 80.2
    }

    @Test
    fun `parse chinese high low pressure`() {
        val result = HealthMetricParser.parse(listOf("高压130 低压85"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result[0].type)
        assertEquals(130.0, result[0].value, 0.01)
        assertEquals(85.0, result[0].secondaryValue!!, 0.01)
    }

    @Test
    fun `parse PR pulse rate`() {
        val result = HealthMetricParser.parse(listOf("PR 68"))
        assertEquals(1, result.size)
        assertEquals(HealthType.HEART_RATE, result[0].type)
        assertEquals(68.0, result[0].value, 0.01)
    }

    @Test
    fun `parse mailv heart rate`() {
        val result = HealthMetricParser.parse(listOf("脉率 75"))
        assertEquals(1, result.size)
        assertEquals(HealthType.HEART_RATE, result[0].type)
        assertEquals(75.0, result[0].value, 0.01)
    }

    @Test
    fun `parse pure percentage as spo2`() {
        val result = HealthMetricParser.parse(listOf("98%"))
        assertEquals(1, result.size)
        assertEquals(HealthType.SPO2, result[0].type)
        assertEquals(98.0, result[0].value, 0.01)
    }

    @Test
    fun `reject percentage outside spo2 range`() {
        // 50% is too low for bare percentage SpO2 detection (needs keyword for <80)
        val result = HealthMetricParser.parse(listOf("50%"))
        assertTrue(result.none { it.type == HealthType.SPO2 })
    }

    // ── 中文单位（新增） ────────────────────────────────────────

    @Test
    fun `parse weight in chinese kg unit`() {
        val result = HealthMetricParser.parse(listOf("65千克"))
        assertEquals(1, result.size)
        assertEquals(HealthType.WEIGHT, result[0].type)
        assertEquals(65.0, result[0].value, 0.01)
    }

    @Test
    fun `parse weight in gongjin`() {
        val result = HealthMetricParser.parse(listOf("70公斤"))
        assertEquals(1, result.size)
        assertEquals(HealthType.WEIGHT, result[0].type)
        assertEquals(70.0, result[0].value, 0.01)
    }

    @Test
    fun `parse weight in jin converts to kg`() {
        val result = HealthMetricParser.parse(listOf("130斤"))
        assertEquals(1, result.size)
        assertEquals(HealthType.WEIGHT, result[0].type)
        assertEquals(65.0, result[0].value, 0.01)
    }

    @Test
    fun `parse heart rate chinese unit`() {
        val result = HealthMetricParser.parse(listOf("72次/分钟"))
        assertEquals(1, result.size)
        assertEquals(HealthType.HEART_RATE, result[0].type)
        assertEquals(72.0, result[0].value, 0.01)
    }

    @Test
    fun `parse heart rate chinese unit short`() {
        val result = HealthMetricParser.parse(listOf("80次/分"))
        assertEquals(1, result.size)
        assertEquals(HealthType.HEART_RATE, result[0].type)
        assertEquals(80.0, result[0].value, 0.01)
    }

    @Test
    fun `parse temperature in sheshidu`() {
        val result = HealthMetricParser.parse(listOf("36.5摄氏度"))
        assertEquals(1, result.size)
        assertEquals(HealthType.TEMPERATURE, result[0].type)
        assertEquals(36.5, result[0].value, 0.01)
    }

    @Test
    fun `parse blood glucose chinese unit`() {
        val result = HealthMetricParser.parse(listOf("5.6毫摩尔/升"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_GLUCOSE, result[0].type)
        assertEquals(5.6, result[0].value, 0.01)
    }

    @Test
    fun `parse blood pressure with chinese mmHg in slash format`() {
        val result = HealthMetricParser.parse(listOf("120/80毫米汞柱"))
        assertEquals(1, result.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result[0].type)
    }

    // ── 空格合并修复验证 ────────────────────────────────────────

    @Test
    fun `cleanOcrText preserves multi-digit space separation`() {
        // "120 80" should NOT be merged (both are multi-digit)
        assertEquals("120 80", HealthMetricParser.cleanOcrText("120 80"))
    }

    @Test
    fun `cleanOcrText preserves three-digit space two-digit`() {
        assertEquals("135 88", HealthMetricParser.cleanOcrText("135 88"))
    }

    @Test
    fun `cleanOcrText still merges single digit prefix`() {
        assertEquals("120", HealthMetricParser.cleanOcrText("1 20"))
    }

    @Test
    fun `cleanOcrText still merges single digit suffix`() {
        assertEquals("120", HealthMetricParser.cleanOcrText("12 0"))
    }

    // ── 日期过滤 ────────────────────────────────────────────────

    @Test
    fun `extractNumbers filters out date-like patterns`() {
        val numbers = HealthMetricParser.extractNumbers(listOf("2024-01-15 120/80"))
        // 120/80 should be extracted, but date components (2024, 01, 15) should not
        assertTrue(numbers.any { it.value == 120.0 && it.pairedValue == 80.0 })
        assertFalse(numbers.any { it.value == 2024.0 })
        assertFalse(numbers.any { it.value == 15.0 })
    }

    @Test
    fun `extractNumbers filters out time patterns`() {
        val numbers = HealthMetricParser.extractNumbers(listOf("14:30 心率72"))
        assertFalse(numbers.any { it.value == 14.0 })
        assertFalse(numbers.any { it.value == 30.0 })
        assertTrue(numbers.any { it.value == 72.0 })
    }
}
