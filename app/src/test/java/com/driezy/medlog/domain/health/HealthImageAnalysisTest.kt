package com.driezy.medlog.domain.health

import com.driezy.medlog.ai.AiChatClient
import com.driezy.medlog.ai.AiChatContentPart
import com.driezy.medlog.ai.AiChatRequest
import com.driezy.medlog.ai.AiChatResponse
import com.driezy.medlog.ai.AiStructuredResponseErrorKind
import com.driezy.medlog.ai.AiStructuredResponseStatus
import com.driezy.medlog.data.model.HealthType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthImageAnalysisTest {

    @Test
    fun `analyzer sends image and prompt then parses strict metrics json`() = runTest {
        val ai = RecordingAiChatClient(
            AiChatResponse(
                text = """
                    {
                      "metrics": [
                        {"type":"BLOOD_PRESSURE","value":128,"secondaryValue":82,"rawText":"128/82","confidence":0.91},
                        {"type":"HEART_RATE","value":72,"unit":"bpm","confidence":0.88}
                      ],
                      "texts": ["128/82", "PUL 72"]
                    }
                """.trimIndent(),
            ),
        )
        val analyzer = HealthImageAnalyzer(ai)

        val result = analyzer.analyze(
            HealthImageAnalysisRequest(
                imageBytes = byteArrayOf(1, 2, 3),
                mimeType = "image/jpeg",
            ),
        )

        val userMessage = ai.lastRequest!!.messages.last()
        assertTrue(userMessage.parts!!.any { it is AiChatContentPart.ImageBytes })
        assertEquals(2, result.metrics.size)
        assertEquals(HealthType.BLOOD_PRESSURE, result.metrics[0].type)
        assertEquals(128.0, result.metrics[0].value, 0.01)
        assertEquals(82.0, result.metrics[0].secondaryValue!!, 0.01)
        assertEquals(HealthType.HEART_RATE, result.metrics[1].type)
        assertEquals(listOf("128/82", "PUL 72"), result.rawTexts)
    }

    @Test
    fun `analyzer recovers json from fenced model output and infers type labels`() = runTest {
        val ai = RecordingAiChatClient(
            AiChatResponse(
                text = """
                    识别结果如下：
                    ```json
                    {
                      "readings": [
                        {"label":"血糖","value":"6.8 mmol/L","confidence":0.8},
                        {"label":"体脂率","value":24.5,"unit":"%"}
                      ]
                    }
                    ```
                """.trimIndent(),
            ),
        )

        val result = HealthImageAnalyzer(ai).analyze(
            HealthImageAnalysisRequest(byteArrayOf(1), "image/png"),
        )

        assertEquals(2, result.metrics.size)
        assertEquals(HealthType.BLOOD_GLUCOSE, result.metrics[0].type)
        assertEquals(6.8, result.metrics[0].value, 0.01)
        assertEquals(HealthType.BODY_FAT, result.metrics[1].type)
        assertEquals(24.5, result.metrics[1].value, 0.01)
    }

    @Test
    fun `analyzer falls back to text parser for non json output`() = runTest {
        val ai = RecordingAiChatClient(AiChatResponse(text = "屏幕显示血压 135/85，心率 76。"))

        val result = HealthImageAnalyzer(ai).analyze(
            HealthImageAnalysisRequest(byteArrayOf(1), "image/jpeg"),
        )

        assertEquals(2, result.metrics.size)
        assertTrue(result.metrics.any { it.type == HealthType.BLOOD_PRESSURE })
        assertTrue(result.metrics.any { it.type == HealthType.HEART_RATE })
        assertTrue(result.rawTexts.first().contains("血压"))
    }

    @Test
    fun `analyzer exposes structured response metadata for fallback parsing`() = runTest {
        val ai = RecordingAiChatClient(AiChatResponse(text = "屏幕显示血压 135/85，心率 76。"))

        val structured = HealthImageAnalyzer(ai).analyzeStructured(
            HealthImageAnalysisRequest(byteArrayOf(1), "image/jpeg"),
        )

        assertEquals(AiStructuredResponseStatus.PARTIAL, structured.status)
        assertEquals(AiStructuredResponseErrorKind.JSON_NOT_FOUND, structured.errorKind)
        assertEquals(null, structured.rawJson)
        assertEquals(HealthAiPromptVersions.IMAGE_OCR, structured.schemaVersion)
        assertTrue(structured.warnings.any { it.contains("fallback", ignoreCase = true) })
        assertEquals(2, structured.parsed!!.metrics.size)
    }

    @Test
    fun `analyzer drops implausible and unknown metrics`() = runTest {
        val ai = RecordingAiChatClient(
            AiChatResponse(
                text = """
                    {
                      "metrics": [
                        {"type":"BLOOD_GLUCOSE","value":999,"rawText":"999"},
                        {"type":"UNKNOWN","value":12,"rawText":"unknown"},
                        {"type":"SPO2","value":97,"rawText":"SpO2 97%"}
                      ]
                    }
                """.trimIndent(),
            ),
        )

        val result = HealthImageAnalyzer(ai).analyze(
            HealthImageAnalysisRequest(byteArrayOf(1), "image/jpeg"),
        )

        assertEquals(1, result.metrics.size)
        assertEquals(HealthType.SPO2, result.metrics.first().type)
    }

    private class RecordingAiChatClient(private val response: AiChatResponse) : AiChatClient {
        var lastRequest: AiChatRequest? = null

        override suspend fun generate(request: AiChatRequest): AiChatResponse {
            lastRequest = request
            return response
        }
    }
}
