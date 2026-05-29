package com.driezy.medlog.domain.health

import com.driezy.medlog.ai.AiChatClient
import com.driezy.medlog.ai.AiChatRequest
import com.driezy.medlog.ai.AiChatResponse
import com.driezy.medlog.ai.AiProviderException
import com.driezy.medlog.ai.AiStructuredResponseErrorKind
import com.driezy.medlog.ai.AiStructuredResponseStatus
import com.driezy.medlog.data.model.AiAnalysisCacheEntry
import com.driezy.medlog.data.model.AiAnalysisKind
import com.driezy.medlog.data.model.AiUsageEvent
import com.driezy.medlog.data.model.HealthRecord
import com.driezy.medlog.data.model.HealthType
import com.driezy.medlog.data.model.AiUsageResult
import com.driezy.medlog.data.repository.AiCacheKeyBuilder
import com.driezy.medlog.data.repository.AiCacheRepository
import com.driezy.medlog.data.repository.AiUsageSummaryRow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthAiPipelineCacheTest {

    private val now = 1_700_000_000_000L
    private val identity = HealthAiModelIdentity(provider = "Gemini", model = "gemini-2.5-flash")

    @Test
    fun `image analyzer stores normalized result and reuses fresh cache`() = runTest {
        val cache = FakeAiCacheRepository()
        val ai = RecordingAiChatClient(
            AiChatResponse(
                text = """
                    {"metrics":[{"type":"SPO2","value":97,"rawText":"SpO2 97%","confidence":0.88}]}
                """.trimIndent(),
            ),
        )
        val analyzer = CachedHealthImageAnalyzer(
            aiChatClient = ai,
            cacheRepository = cache,
            identity = identity,
        )
        val request = HealthImageAnalysisRequest(
            imageBytes = byteArrayOf(1, 2, 3),
            mimeType = "image/jpeg",
            locale = "zh-CN",
        )

        val first = analyzer.analyze(request, nowMillis = now)
        val second = analyzer.analyze(request, nowMillis = now + 1_000)

        assertEquals(1, ai.requests.size)
        assertEquals(HealthType.SPO2, first.metrics.single().type)
        assertEquals(first, second)
        val entry = cache.entries.values.single()
        assertEquals(AiAnalysisKind.IMAGE_OCR, entry.kind)
        assertEquals(now + 24L * 60 * 60 * 1000, entry.expiresAt)
        assertNotNull(HealthAiCachePayloadCodec.decodeImageOcr(entry.responseJson))
        assertTrue(cache.usageEvents.any { it.cacheHit && it.result == AiUsageResult.SUCCESS })
    }

    @Test
    fun `image analyzer refreshes when cached payload cannot be decoded`() = runTest {
        val cache = FakeAiCacheRepository()
        val imageBytes = byteArrayOf(7, 8, 9)
        val inputHash = AiCacheKeyBuilder.sha256(imageBytes)
        val key = AiCacheKeyBuilder.build(
            kind = AiAnalysisKind.IMAGE_OCR,
            provider = identity.provider,
            model = identity.model,
            promptVersion = HealthAiPromptVersions.IMAGE_OCR,
            inputHash = inputHash,
            locale = "zh-CN",
        )
        cache.entries[key] = entry(
            cacheKey = key,
            kind = AiAnalysisKind.IMAGE_OCR,
            inputHash = inputHash,
            responseJson = "not-json",
        )
        val ai = RecordingAiChatClient(
            AiChatResponse(text = """{"metrics":[{"type":"HEART_RATE","value":72,"rawText":"72"}]}"""),
        )

        val result = CachedHealthImageAnalyzer(ai, cache, identity).analyze(
            HealthImageAnalysisRequest(imageBytes, "image/png", locale = "zh-CN"),
            nowMillis = now,
        )

        assertEquals(1, ai.requests.size)
        assertEquals(HealthType.HEART_RATE, result.metrics.single().type)
        assertNotNull(HealthAiCachePayloadCodec.decodeImageOcr(cache.entries[key]!!.responseJson))
    }

    @Test
    fun `image analyzer records structured error kind when fallback parser succeeds`() = runTest {
        val cache = FakeAiCacheRepository()
        val ai = RecordingAiChatClient(AiChatResponse(text = "屏幕显示血压 135/85，心率 76。"))

        val result = CachedHealthImageAnalyzer(ai, cache, identity).analyze(
            HealthImageAnalysisRequest(byteArrayOf(1), "image/jpeg", locale = "zh-CN"),
            nowMillis = now,
        )

        assertEquals(2, result.metrics.size)
        assertEquals(AiUsageResult.SUCCESS, cache.usageEvents.single().result)
        assertEquals(AiStructuredResponseErrorKind.JSON_NOT_FOUND.name, cache.usageEvents.single().errorCategory)
        assertEquals(
            AiStructuredResponseErrorKind.JSON_NOT_FOUND.name,
            HealthAiCachePayloadCodec.decodeMetadata(cache.entries.values.single().responseJson)!!.errorKind,
        )
    }

    @Test
    fun `image analyzer throws provider exception with structured kind when response is unusable`() = runTest {
        val cache = FakeAiCacheRepository()
        val ai = RecordingAiChatClient(AiChatResponse(text = ""))

        val error = runCatching {
            CachedHealthImageAnalyzer(ai, cache, identity).analyze(
                HealthImageAnalysisRequest(byteArrayOf(1), "image/jpeg", locale = "zh-CN"),
                nowMillis = now,
            )
        }.exceptionOrNull()

        assertTrue(error is AiProviderException)
        assertEquals(AiStructuredResponseErrorKind.EMPTY_RESPONSE, (error as AiProviderException).errorKind)
        assertEquals(AiStructuredResponseErrorKind.EMPTY_RESPONSE.name, cache.usageEvents.single().errorCategory)
    }

    @Test
    fun `health insight generator stores remote insights by context hash and reuses cache`() = runTest {
        val cache = FakeAiCacheRepository()
        val ai = RecordingAiChatClient(
            AiChatResponse(
                text = """
                    ```json
                    {
                      "insights": [
                        {
                          "id": "remote-bp",
                          "kind": "SAFETY",
                          "severity": "WARNING",
                          "title": "血压需要关注",
                          "body": "建议按相同时间复测并记录状态。",
                          "relatedType": "BLOOD_PRESSURE"
                        }
                      ]
                    }
                    ```
                """.trimIndent(),
            ),
        )
        val context = HealthIntelligenceEngine.buildContext(
            records = listOf(HealthRecord(type = HealthType.BLOOD_PRESSURE.name, value = 142.0, secondaryValue = 92.0)),
            userHeightCm = 170f,
            nowMillis = now,
        )
        val generator = CachedHealthInsightGenerator(
            aiChatClient = ai,
            cacheRepository = cache,
            identity = identity,
        )

        val first = generator.generate(context, nowMillis = now)
        val second = generator.generate(context, nowMillis = now + 2_000)

        assertEquals(1, ai.requests.size)
        assertEquals(first, second)
        assertEquals("remote-bp", first.single().id)
        val entry = cache.entries.values.single()
        assertEquals(AiAnalysisKind.HEALTH_INSIGHT, entry.kind)
        assertEquals(now + 12L * 60 * 60 * 1000, entry.expiresAt)
        assertNotNull(HealthAiCachePayloadCodec.decodeHealthInsights(entry.responseJson))
    }

    @Test
    fun `health insight cache key ignores generation timestamp within ttl`() = runTest {
        val cache = FakeAiCacheRepository()
        val ai = RecordingAiChatClient(
            AiChatResponse(
                text = """
                    {"insights":[{"id":"remote-weight","kind":"TREND","severity":"INFO","title":"体重趋势","body":"继续按相同时间记录。","relatedType":"WEIGHT"}]}
                """.trimIndent(),
            ),
        )
        val records = listOf(HealthRecord(type = HealthType.WEIGHT.name, value = 70.0, timestamp = now - 1_000))
        val generator = CachedHealthInsightGenerator(
            aiChatClient = ai,
            cacheRepository = cache,
            identity = identity,
        )

        val first = generator.generate(
            HealthIntelligenceEngine.buildContext(records, userHeightCm = 170f, nowMillis = now),
            nowMillis = now,
        )
        val second = generator.generate(
            HealthIntelligenceEngine.buildContext(records, userHeightCm = 170f, nowMillis = now + 1_000),
            nowMillis = now + 1_000,
        )

        assertEquals(1, ai.requests.size)
        assertEquals(first, second)
    }

    @Test
    fun `health insight parser exposes structured failure for invalid schema`() {
        val structured = HealthInsightResponseParser.parseStructured("""{"insights":[{"kind":"TREND"}]}""")

        assertEquals(AiStructuredResponseStatus.FAILED, structured.status)
        assertEquals(AiStructuredResponseErrorKind.SCHEMA_INVALID, structured.errorKind)
        assertEquals(HealthAiPromptVersions.HEALTH_INSIGHT, structured.schemaVersion)
        assertEquals("""{"insights":[{"kind":"TREND"}]}""", structured.rawJson)
        assertEquals(null, structured.parsed)
        assertTrue(structured.warnings.any { it.contains("schema", ignoreCase = true) })
    }

    @Test
    fun `health insight parser rejects restricted medical advice in whole batch`() {
        val structured = HealthInsightResponseParser.parseStructured(
            """
            {
              "insights": [
                {
                  "id": "unsafe-dose",
                  "kind": "SAFETY",
                  "severity": "WARNING",
                  "title": "血压处理",
                  "body": "建议把降压药剂量加倍，并观察一周。",
                  "relatedType": "BLOOD_PRESSURE"
                },
                {
                  "id": "normal-tracking",
                  "kind": "TRACKING",
                  "severity": "INFO",
                  "title": "继续记录",
                  "body": "继续按相同时间记录。",
                  "relatedType": "BLOOD_PRESSURE"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(AiStructuredResponseStatus.FAILED, structured.status)
        assertEquals(AiStructuredResponseErrorKind.POLICY_VIOLATION, structured.errorKind)
        assertEquals(null, structured.parsed)
        assertTrue(structured.warnings.any { it.contains("restricted", ignoreCase = true) })
    }

    @Test
    fun `health insight parser allows safe medication context tracking language`() {
        val structured = HealthInsightResponseParser.parseStructured(
            """
            {
              "insights": [
                {
                  "id": "safe-medication-context",
                  "kind": "TRACKING",
                  "severity": "INFO",
                  "title": "记录更完整",
                  "body": "建议记录测量时间、状态和用药情况，并按医嘱处理。",
                  "relatedType": "BLOOD_PRESSURE"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(AiStructuredResponseStatus.SUCCESS, structured.status)
        assertEquals("safe-medication-context", structured.parsed!!.single().id)
    }

    @Test
    fun `health insight parser unwraps provider metadata envelopes`() {
        val structured = HealthInsightResponseParser.parseStructured(
            """
            {
              "metadata": { "providerRequestId": "abc" },
              "data": {
                "insights": [
                  {
                    "id": "wrapped-insight",
                    "kind": "TREND",
                    "severity": "INFO",
                    "title": "体重趋势",
                    "body": "继续按相同时间记录。",
                    "relatedType": "WEIGHT"
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        assertEquals(AiStructuredResponseStatus.SUCCESS, structured.status)
        assertEquals("wrapped-insight", structured.parsed!!.single().id)
    }

    @Test
    fun `health insight parser finds insights in array wrapped responses`() {
        val structured = HealthInsightResponseParser.parseStructured(
            """
            [
              { "metadata": { "providerRequestId": "abc" } },
              {
                "insights": [
                  {
                    "id": "array-wrapped-insight",
                    "kind": "TRACKING",
                    "severity": "INFO",
                    "title": "继续记录",
                    "body": "连续记录后趋势会更可靠。",
                    "relatedType": "BLOOD_GLUCOSE"
                  }
                ]
              }
            ]
            """.trimIndent(),
        )

        assertEquals(AiStructuredResponseStatus.SUCCESS, structured.status)
        assertEquals("array-wrapped-insight", structured.parsed!!.single().id)
    }

    @Test
    fun `health insight parser rejects restricted advice across supported locales`() {
        val restrictedBodies = listOf(
            "Increase your blood pressure medication dose tonight.",
            "薬の量を減らして様子を見てください。",
            "혈압약 복용량을 두 배로 늘리세요.",
        )

        restrictedBodies.forEach { body ->
            val structured = HealthInsightResponseParser.parseStructured(
                """
                {
                  "insights": [
                    {
                      "kind": "SAFETY",
                      "severity": "WARNING",
                      "title": "unsafe",
                      "body": "$body",
                      "relatedType": "BLOOD_PRESSURE"
                    }
                  ]
                }
                """.trimIndent(),
            )

            assertEquals(body, AiStructuredResponseStatus.FAILED, structured.status)
            assertEquals(body, AiStructuredResponseErrorKind.POLICY_VIOLATION, structured.errorKind)
            assertEquals(body, null, structured.parsed)
        }
    }

    @Test
    fun `health insight generator records structured error kind for invalid schema`() = runTest {
        val cache = FakeAiCacheRepository()
        val ai = RecordingAiChatClient(AiChatResponse(text = """{"insights":[{"kind":"TREND"}]}"""))
        val context = HealthIntelligenceEngine.buildContext(
            records = listOf(HealthRecord(type = HealthType.WEIGHT.name, value = 70.0, timestamp = now - 1_000)),
            userHeightCm = 170f,
            nowMillis = now,
        )

        val error = runCatching {
            CachedHealthInsightGenerator(ai, cache, identity).generate(context, nowMillis = now)
        }.exceptionOrNull()

        assertNotNull(error)
        assertEquals(AiUsageResult.ERROR, cache.usageEvents.single().result)
        assertEquals(AiStructuredResponseErrorKind.SCHEMA_INVALID.name, cache.usageEvents.single().errorCategory)
    }

    private fun entry(
        cacheKey: String,
        kind: AiAnalysisKind,
        inputHash: String,
        responseJson: String,
    ) = AiAnalysisCacheEntry(
        cacheKey = cacheKey,
        kind = kind,
        provider = identity.provider,
        model = identity.model,
        promptVersion = 1,
        inputHash = inputHash,
        locale = "zh-CN",
        responseJson = responseJson,
        createdAt = now,
        expiresAt = now + 1_000,
    )

    private class RecordingAiChatClient(
        private val response: AiChatResponse,
    ) : AiChatClient {
        val requests = mutableListOf<AiChatRequest>()

        override suspend fun generate(request: AiChatRequest): AiChatResponse {
            requests.add(request)
            return response
        }
    }

    private class FakeAiCacheRepository : AiCacheRepository {
        val entries = linkedMapOf<String, AiAnalysisCacheEntry>()
        val usageEvents = mutableListOf<AiUsageEvent>()

        override suspend fun getFresh(cacheKey: String, nowMillis: Long): AiAnalysisCacheEntry? =
            entries[cacheKey]?.takeIf { it.expiresAt > nowMillis }

        override suspend fun put(entry: AiAnalysisCacheEntry, nowMillis: Long) {
            entries[entry.cacheKey] = entry
        }

        override suspend fun recordUsage(event: AiUsageEvent) {
            usageEvents.add(event)
        }

        override suspend fun usageSummary(sinceMillis: Long): List<AiUsageSummaryRow> =
            emptyList()

        override suspend fun clearAll() {
            entries.clear()
            usageEvents.clear()
        }
    }
}
