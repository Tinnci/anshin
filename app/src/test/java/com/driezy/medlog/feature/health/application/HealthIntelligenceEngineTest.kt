package com.driezy.medlog.feature.health.application

import com.driezy.medlog.capability.ai.AiChatRole
import com.driezy.medlog.data.model.HealthRecord
import com.driezy.medlog.data.model.HealthRecordSource
import com.driezy.medlog.data.model.HealthType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthIntelligenceEngineTest {

    private val now = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `buildContext aggregates recent values without exposing notes`() {
        val records = listOf(
            record(HealthType.WEIGHT, 70.0, now - day * 3, notes = "home scale serial 123"),
            record(HealthType.WEIGHT, 71.0, now - day),
            record(HealthType.BODY_FAT, 24.0, now - day),
            record(HealthType.BLOOD_PRESSURE, 128.0, now, secondary = 82.0),
        )

        val context = HealthIntelligenceEngine.buildContext(
            records = records,
            userHeightCm = 170f,
            nowMillis = now,
        )

        assertEquals(3, context.metrics.size)
        val weight = context.metric(HealthType.WEIGHT)!!
        assertEquals(71.0, weight.latestValue, 0.01)
        assertEquals(70.5, weight.avg7d!!, 0.01)
        assertEquals(24.6, context.bmi!!, 0.1)
        assertFalse(context.toPromptContext().contains("serial"))
    }

    @Test
    fun `suggestions prioritize crisis blood pressure and high glucose safely`() {
        val context = HealthIntelligenceEngine.buildContext(
            records = listOf(
                record(HealthType.BLOOD_PRESSURE, 183.0, now, secondary = 122.0),
                record(HealthType.BLOOD_GLUCOSE, 15.2, now - 2_000),
            ),
            userHeightCm = 0f,
            nowMillis = now,
        )

        val insights = HealthIntelligenceEngine.generateLocalInsights(context)

        assertEquals(HealthInsightSeverity.URGENT, insights.first().severity)
        assertTrue(insights.first().title.contains("血压"))
        assertTrue(insights.first().body.contains("就医") || insights.first().body.contains("急诊"))
        assertTrue(
            insights.any {
                it.relatedType == HealthType.BLOOD_GLUCOSE &&
                    it.severity == HealthInsightSeverity.WARNING
            },
        )
        assertTrue(insights.all { "诊断" !in it.body })
    }

    @Test
    fun `weight and body fat generate practical trend insight`() {
        val context = HealthIntelligenceEngine.buildContext(
            records = listOf(
                record(HealthType.WEIGHT, 72.0, now - day * 6),
                record(HealthType.WEIGHT, 73.0, now - day * 3),
                record(HealthType.WEIGHT, 74.0, now),
                record(HealthType.BODY_FAT, 29.0, now),
            ),
            userHeightCm = 168f,
            nowMillis = now,
        )

        val insights = HealthIntelligenceEngine.generateLocalInsights(context)

        assertTrue(insights.any { it.relatedType == HealthType.WEIGHT && it.title.contains("上升") })
        assertTrue(insights.any { it.relatedType == HealthType.BODY_FAT })
        assertTrue(insights.all { it.body.length <= 120 })
    }

    @Test
    fun `empty context recommends low friction tracking instead of chat`() {
        val context = HealthIntelligenceEngine.buildContext(
            records = emptyList(),
            userHeightCm = 0f,
            nowMillis = now,
        )

        val insights = HealthIntelligenceEngine.generateLocalInsights(context)

        assertEquals(1, insights.size)
        assertEquals(HealthInsightKind.TRACKING, insights.first().kind)
        assertTrue(insights.first().body.contains("记录"))
    }

    @Test
    fun `all health types produce actionable insight when values need attention`() {
        val scenarios = mapOf(
            HealthType.BLOOD_PRESSURE to listOf(record(HealthType.BLOOD_PRESSURE, 145.0, now, secondary = 95.0)),
            HealthType.BLOOD_GLUCOSE to listOf(record(HealthType.BLOOD_GLUCOSE, 15.2, now)),
            HealthType.WEIGHT to listOf(
                record(HealthType.WEIGHT, 70.0, now - day * 6),
                record(HealthType.WEIGHT, 71.0, now - day * 3),
                record(HealthType.WEIGHT, 72.0, now),
            ),
            HealthType.BODY_FAT to listOf(record(HealthType.BODY_FAT, 31.0, now)),
            HealthType.HEART_RATE to listOf(record(HealthType.HEART_RATE, 118.0, now)),
            HealthType.TEMPERATURE to listOf(record(HealthType.TEMPERATURE, 38.2, now)),
            HealthType.SPO2 to listOf(record(HealthType.SPO2, 92.0, now)),
        )

        scenarios.forEach { (type, records) ->
            val context = HealthIntelligenceEngine.buildContext(records, userHeightCm = 170f, nowMillis = now)
            val insights = HealthIntelligenceEngine.generateLocalInsights(context)

            assertTrue(
                "${type.name} should produce a related insight",
                insights.any { it.relatedType == type },
            )
            assertTrue(
                "${type.name} insight should contain practical body text",
                insights.first { it.relatedType == type }.body.length in 12..120,
            )
        }
    }

    @Test
    fun `prompt builder creates strict structured health request`() {
        val context = HealthIntelligenceEngine.buildContext(
            records = listOf(
                record(HealthType.BLOOD_PRESSURE, 136.0, now, secondary = 88.0),
                record(HealthType.WEIGHT, 71.0, now - day),
            ),
            userHeightCm = 170f,
            nowMillis = now,
        )

        val request = HealthInsightPromptBuilder.buildRequest(
            context = context,
            profile = HealthPromptProfile(locale = "zh-CN", maxInsights = 4),
        )

        assertEquals(3, request.messages.size)
        assertEquals(AiChatRole.SYSTEM, request.messages[0].role)
        assertTrue(request.messages[0].content.contains("不是医生"))
        assertTrue(request.messages[1].content.contains("JSON"))
        assertTrue(request.messages[2].content.contains("BLOOD_PRESSURE"))
        assertTrue(request.messages[2].content.contains("BMI"))
        assertFalse(request.messages[2].content.contains("notes"))
        assertEquals(0.2, request.temperature!!, 0.01)
        assertEquals(700, request.maxOutputTokens)
    }

    @Test
    fun `buildContext aggregates record provenance and low confidence OCR`() {
        val context = HealthIntelligenceEngine.buildContext(
            records = listOf(
                record(HealthType.SPO2, 96.0, now - day, source = HealthRecordSource.MANUAL),
                record(HealthType.SPO2, 94.0, now - 1_000, source = HealthRecordSource.CLOUD_OCR, confidence = 0.62f),
                record(HealthType.SPO2, 95.0, now, source = HealthRecordSource.LOCAL_OCR, confidence = 0.88f),
            ),
            userHeightCm = 0f,
            nowMillis = now,
        )

        val spo2 = context.metric(HealthType.SPO2)!!

        assertEquals(HealthInsightSourceMix.MIXED, spo2.sourceMix)
        assertEquals(1, spo2.manualCount)
        assertEquals(1, spo2.localOcrCount)
        assertEquals(1, spo2.cloudOcrCount)
        assertEquals(1, spo2.lowConfidenceCount)
        assertTrue(context.toPromptContext().contains("sourceMix=MIXED"))
        assertTrue(context.toPromptContext().contains("lowConfidenceCount=1"))
    }

    private fun record(
        type: HealthType,
        value: Double,
        timestamp: Long,
        secondary: Double? = null,
        notes: String = "",
        source: HealthRecordSource = HealthRecordSource.MANUAL,
        confidence: Float? = null,
    ): HealthRecord = HealthRecord(
        type = type.name,
        value = value,
        secondaryValue = secondary,
        timestamp = timestamp,
        notes = notes,
        source = source,
        sourceConfidence = confidence,
    )

    private fun HealthInsightContext.metric(type: HealthType): HealthMetricInsight? =
        metrics.firstOrNull { it.type == type }
}
