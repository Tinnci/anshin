package com.driezy.medlog.data.repository

import com.driezy.medlog.data.local.AiAnalysisCacheDao
import com.driezy.medlog.data.local.AiUsageEventDao
import com.driezy.medlog.data.model.AiAnalysisCacheEntry
import com.driezy.medlog.data.model.AiAnalysisKind
import com.driezy.medlog.data.model.AiUsageEvent
import com.driezy.medlog.data.model.AiUsageFeature
import com.driezy.medlog.data.model.AiUsageResult
import com.driezy.medlog.data.model.AiUsageSummaryRow
import com.driezy.medlog.data.model.NetworkType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCacheRepositoryTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `cache key includes kind provider model prompt version input hash and locale`() {
        val key = AiCacheKeyBuilder.build(
            kind = AiAnalysisKind.IMAGE_OCR,
            provider = "Gemini",
            model = "gemini-2.5-flash",
            promptVersion = 1,
            inputHash = "abc123",
            locale = "zh-CN",
        )

        assertEquals(
            "kind=IMAGE_OCR|provider=Gemini|model=gemini-2.5-flash|promptVersion=1|inputHash=abc123|locale=zh-CN",
            key,
        )
    }

    @Test
    fun `sha256 returns stable lowercase hex`() {
        assertEquals(
            "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81",
            AiCacheKeyBuilder.sha256(byteArrayOf(1, 2, 3)),
        )
    }

    @Test
    fun `get returns fresh entry and ignores expired entry`() = runTest {
        val cacheDao = FakeAiAnalysisCacheDao()
        val repo = AiCacheRepositoryImpl(cacheDao, FakeAiUsageEventDao())
        val key = "cache-key"
        cacheDao.upsert(
            entry(
                cacheKey = key,
                expiresAt = now + 1_000,
            ),
        )

        assertEquals("{}", repo.getFresh(key, now)!!.responseJson)
        assertNull(repo.getFresh(key, now + 2_000))
    }

    @Test
    fun `put deletes expired rows and enforces per kind hard limit`() = runTest {
        val cacheDao = FakeAiAnalysisCacheDao()
        val repo = AiCacheRepositoryImpl(
            cacheDao = cacheDao,
            usageEventDao = FakeAiUsageEventDao(),
            imageOcrLimit = 2,
            healthInsightLimit = 2,
        )
        cacheDao.upsert(entry(cacheKey = "expired", expiresAt = now - 1))

        repo.put(entry(cacheKey = "one", createdAt = now, expiresAt = now + 1_000), nowMillis = now)
        repo.put(entry(cacheKey = "two", createdAt = now + 1, expiresAt = now + 1_000), nowMillis = now)
        repo.put(entry(cacheKey = "three", createdAt = now + 2, expiresAt = now + 1_000), nowMillis = now)

        assertNull(cacheDao.getFresh("expired", now))
        assertNull(cacheDao.getFresh("one", now))
        assertEquals("{}", cacheDao.getFresh("two", now)!!.responseJson)
        assertEquals("{}", cacheDao.getFresh("three", now)!!.responseJson)
    }

    @Test
    fun `usage event stores short input hash prefix only`() = runTest {
        val usageDao = FakeAiUsageEventDao()
        val repo = AiCacheRepositoryImpl(FakeAiAnalysisCacheDao(), usageDao)

        repo.recordUsage(
            AiUsageEvent(
                feature = AiUsageFeature.IMAGE_OCR,
                provider = "Anthropic",
                model = "claude",
                networkType = NetworkType.WIFI,
                cacheHit = false,
                result = AiUsageResult.SUCCESS,
                inputHashPrefix = AiCacheKeyBuilder.hashPrefix("0123456789abcdef"),
                timestamp = now,
            ),
        )

        val prefix = usageDao.events.single().inputHashPrefix!!
        assertEquals("0123456789ab", prefix)
        assertTrue(prefix.length <= 12)
    }

    @Test
    fun `usage summary groups recent events by feature and result`() = runTest {
        val usageDao = FakeAiUsageEventDao()
        val repo = AiCacheRepositoryImpl(FakeAiAnalysisCacheDao(), usageDao)
        usageDao.events += listOf(
            usageEvent(AiUsageFeature.HEALTH_INSIGHT, AiUsageResult.SUCCESS, cacheHit = false, timestamp = now),
            usageEvent(AiUsageFeature.HEALTH_INSIGHT, AiUsageResult.SUCCESS, cacheHit = true, timestamp = now + 1),
            usageEvent(
                AiUsageFeature.HEALTH_INSIGHT,
                AiUsageResult.ERROR,
                cacheHit = false,
                timestamp = now + 2,
                error = "POLICY_VIOLATION",
            ),
            usageEvent(
                AiUsageFeature.IMAGE_OCR,
                AiUsageResult.ERROR,
                cacheHit = false,
                timestamp = now + 3,
                error = "JSON_INVALID",
            ),
            usageEvent(AiUsageFeature.IMAGE_OCR, AiUsageResult.SUCCESS, cacheHit = true, timestamp = now - 10_000),
        )

        val summary = repo.usageSummary(sinceMillis = now)

        assertEquals(2, summary.size)
        val health = summary.single { it.feature == AiUsageFeature.HEALTH_INSIGHT }
        assertEquals(3, health.totalCount)
        assertEquals(2, health.successCount)
        assertEquals(1, health.errorCount)
        assertEquals(1, health.cacheHitCount)
        assertEquals("POLICY_VIOLATION", health.lastErrorCategory)
        assertEquals(now + 2, health.lastUsedAt)
        val ocr = summary.single { it.feature == AiUsageFeature.IMAGE_OCR }
        assertEquals(1, ocr.totalCount)
        assertEquals(0, ocr.successCount)
        assertEquals(1, ocr.errorCount)
        assertEquals(0, ocr.cacheHitCount)
        assertEquals("JSON_INVALID", ocr.lastErrorCategory)
    }

    private fun entry(
        cacheKey: String,
        kind: AiAnalysisKind = AiAnalysisKind.IMAGE_OCR,
        createdAt: Long = now,
        expiresAt: Long = now + 1_000,
    ) = AiAnalysisCacheEntry(
        cacheKey = cacheKey,
        kind = kind,
        provider = "Gemini",
        model = "gemini",
        promptVersion = 1,
        inputHash = "input",
        locale = "zh-CN",
        responseJson = "{}",
        createdAt = createdAt,
        expiresAt = expiresAt,
    )

    private fun usageEvent(
        feature: AiUsageFeature,
        result: AiUsageResult,
        cacheHit: Boolean,
        timestamp: Long,
        error: String? = null,
    ) = AiUsageEvent(
        timestamp = timestamp,
        feature = feature,
        provider = "Gemini",
        model = "gemini",
        networkType = NetworkType.WIFI,
        cacheHit = cacheHit,
        result = result,
        errorCategory = error,
    )

    private class FakeAiAnalysisCacheDao : AiAnalysisCacheDao {
        private val rows = linkedMapOf<String, AiAnalysisCacheEntry>()

        override suspend fun getFresh(cacheKey: String, nowMillis: Long): AiAnalysisCacheEntry? =
            rows[cacheKey]?.takeIf { it.expiresAt > nowMillis }

        override suspend fun upsert(entry: AiAnalysisCacheEntry) {
            rows[entry.cacheKey] = entry
        }

        override suspend fun deleteExpired(nowMillis: Long) {
            rows.entries.removeIf { it.value.expiresAt <= nowMillis }
        }

        override suspend fun deleteOverflow(kind: AiAnalysisKind, keep: Int) {
            rows.values
                .filter { it.kind == kind }
                .sortedByDescending { it.createdAt }
                .drop(keep)
                .map { it.cacheKey }
                .forEach { rows.remove(it) }
        }

        override suspend fun clearAll() {
            rows.clear()
        }
    }

    private class FakeAiUsageEventDao : AiUsageEventDao {
        val events = mutableListOf<AiUsageEvent>()

        override suspend fun insert(event: AiUsageEvent) {
            events.add(event)
        }

        override suspend fun summarySince(sinceMillis: Long): List<AiUsageSummaryRow> = events
            .filter { it.timestamp >= sinceMillis }
            .groupBy { it.feature }
            .map { (feature, featureEvents) ->
                AiUsageSummaryRow(
                    feature = feature,
                    totalCount = featureEvents.size,
                    successCount = featureEvents.count { it.result == AiUsageResult.SUCCESS },
                    fallbackCount = featureEvents.count { it.result == AiUsageResult.FALLBACK },
                    errorCount = featureEvents.count { it.result == AiUsageResult.ERROR },
                    cacheHitCount = featureEvents.count { it.cacheHit },
                    lastUsedAt = featureEvents.maxOf { it.timestamp },
                    lastErrorCategory = featureEvents
                        .filter { it.errorCategory != null }
                        .maxByOrNull { it.timestamp }
                        ?.errorCategory,
                )
            }
            .sortedBy { it.feature.name }

        override suspend fun deleteOlderThan(cutoffMillis: Long) {
            events.removeIf { it.timestamp < cutoffMillis }
        }

        override suspend fun clearAll() {
            events.clear()
        }
    }
}
