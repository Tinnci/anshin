package com.driezy.medlog.domain.health

import com.driezy.medlog.ai.AiStructuredResponse
import com.driezy.medlog.ai.AiStructuredResponseErrorKind
import com.driezy.medlog.ai.AiStructuredResponseStatus
import com.driezy.medlog.data.model.ExtractedNumber
import com.driezy.medlog.data.model.HealthType
import com.driezy.medlog.data.model.OcrParseResult
import com.driezy.medlog.data.model.ParsedHealthMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthAiCachePayloadCodecTest {

    @Test
    fun `image ocr payload round trips normalized metrics without provider json`() {
        val result = OcrParseResult(
            metrics = listOf(
                ParsedHealthMetric(
                    type = HealthType.BLOOD_PRESSURE,
                    value = 128.0,
                    secondaryValue = 82.0,
                    rawText = "128/82",
                    confidence = 0.91f,
                ),
            ),
            candidates = listOf(
                ExtractedNumber(value = 72.0, rawText = "PUL 72", confidence = 0.7f),
            ),
            rawTexts = listOf("128/82", "PUL 72"),
        )

        val json = HealthAiCachePayloadCodec.encodeImageOcr(result)
        val decoded = HealthAiCachePayloadCodec.decodeImageOcr(json)

        assertTrue(json.contains("\"schemaVersion\":1"))
        assertTrue(json.contains("\"kind\":\"IMAGE_OCR\""))
        assertTrue("Provider response keys must not leak into normalized cache", "choices" !in json)
        assertEquals(result, decoded)
    }

    @Test
    fun `image ocr structured payload stores parse metadata without raw provider text`() {
        val result = OcrParseResult(
            metrics = listOf(
                ParsedHealthMetric(
                    type = HealthType.HEART_RATE,
                    value = 76.0,
                    rawText = "HR 76",
                    confidence = 0.75f,
                ),
            ),
            candidates = emptyList(),
            rawTexts = listOf("HR 76"),
        )
        val structured = AiStructuredResponse(
            rawText = "provider raw text should not be cached",
            parsed = result,
            status = AiStructuredResponseStatus.PARTIAL,
            schemaVersion = HealthAiPromptVersions.IMAGE_OCR,
            warnings = listOf("JSON not found; used fallback text parser."),
            errorKind = AiStructuredResponseErrorKind.JSON_NOT_FOUND,
        )

        val json = HealthAiCachePayloadCodec.encodeImageOcrStructured(structured)
        val decoded = HealthAiCachePayloadCodec.decodeImageOcr(json)
        val metadata = HealthAiCachePayloadCodec.decodeMetadata(json)

        assertEquals(result, decoded)
        assertTrue("Raw provider response must not be cached", "provider raw text" !in json)
        assertEquals(AiStructuredResponseStatus.PARTIAL.name, metadata!!.status)
        assertEquals(AiStructuredResponseErrorKind.JSON_NOT_FOUND.name, metadata.errorKind)
        assertEquals(listOf("JSON not found; used fallback text parser."), metadata.warnings)
    }

    @Test
    fun `health insight payload round trips versioned insights`() {
        val insights = listOf(
            HealthInsight(
                id = "bp-high",
                kind = HealthInsightKind.SAFETY,
                severity = HealthInsightSeverity.WARNING,
                title = "血压高于常用目标",
                body = "连续几次偏高时，建议记录测量时间。",
                relatedType = HealthType.BLOOD_PRESSURE,
            ),
        )

        val json = HealthAiCachePayloadCodec.encodeHealthInsights(insights)
        val decoded = HealthAiCachePayloadCodec.decodeHealthInsights(json)

        assertTrue(json.contains("\"kind\":\"HEALTH_INSIGHT\""))
        assertEquals(insights, decoded)
    }

    @Test
    fun `health insight structured payload stores response metadata`() {
        val insights = listOf(
            HealthInsight(
                id = "weight",
                kind = HealthInsightKind.TRACKING,
                severity = HealthInsightSeverity.INFO,
                title = "继续记录",
                body = "保持相同时间记录。",
                relatedType = HealthType.WEIGHT,
            ),
        )
        val structured = AiStructuredResponse(
            rawText = """{"insights":[]}""",
            rawJson = """{"insights":[]}""",
            parsed = insights,
            status = AiStructuredResponseStatus.SUCCESS,
            schemaVersion = HealthAiPromptVersions.HEALTH_INSIGHT,
        )

        val json = HealthAiCachePayloadCodec.encodeHealthInsightsStructured(structured)
        val metadata = HealthAiCachePayloadCodec.decodeMetadata(json)

        assertEquals(insights, HealthAiCachePayloadCodec.decodeHealthInsights(json))
        assertEquals(AiStructuredResponseStatus.SUCCESS.name, metadata!!.status)
        assertEquals(null, metadata.errorKind)
        assertTrue("Raw provider JSON must not be cached", "\"insights\":[]" !in json)
    }

    @Test
    fun `decode ignores unknown fields and drops invalid health types`() {
        val decoded = HealthAiCachePayloadCodec.decodeImageOcr(
            """
            {
              "schemaVersion": 1,
              "kind": "IMAGE_OCR",
              "providerRaw": {"choices": []},
              "metrics": [
                {"type": "UNKNOWN", "value": 1, "rawText": "x"},
                {"type": "SPO2", "value": 97, "rawText": "SpO2 97%", "confidence": 0.8}
              ],
              "candidates": [],
              "rawTexts": ["SpO2 97%"]
            }
            """.trimIndent(),
        )

        assertEquals(1, decoded!!.metrics.size)
        assertEquals(HealthType.SPO2, decoded.metrics.first().type)
    }

    @Test
    fun `decode returns null for incompatible or corrupt payloads`() {
        assertNull(
            HealthAiCachePayloadCodec.decodeImageOcr(
                """{"schemaVersion":1,"kind":"HEALTH_INSIGHT","metrics":[]}""",
            ),
        )
        assertNull(
            HealthAiCachePayloadCodec.decodeHealthInsights(
                """{"schemaVersion":2,"kind":"HEALTH_INSIGHT","insights":[]}""",
            ),
        )
        assertNull(HealthAiCachePayloadCodec.decodeImageOcr("not-json"))
    }
}
