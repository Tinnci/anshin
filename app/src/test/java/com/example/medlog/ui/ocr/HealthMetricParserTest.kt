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
        // 至少应检出心率（Pulse 72），血压可能通过 joined text 检出
        assertTrue(result.any { it.type == HealthType.HEART_RATE })
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
}
