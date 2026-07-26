package com.driezy.medlog.data.repository

import com.driezy.medlog.data.local.AiAnalysisCacheDao
import com.driezy.medlog.data.local.AiUsageEventDao
import com.driezy.medlog.data.model.AiAnalysisCacheEntry
import com.driezy.medlog.data.model.AiAnalysisKind
import com.driezy.medlog.data.model.AiUsageEvent
import com.driezy.medlog.data.model.AiUsageFeature
import java.security.MessageDigest

interface AiCacheRepository {
    suspend fun getFresh(cacheKey: String, nowMillis: Long = System.currentTimeMillis()): AiAnalysisCacheEntry?
    suspend fun put(entry: AiAnalysisCacheEntry, nowMillis: Long = System.currentTimeMillis())
    suspend fun recordUsage(event: AiUsageEvent)
    suspend fun usageSummary(sinceMillis: Long): List<AiUsageSummaryRow>
    suspend fun clearAll()
}

data class AiUsageSummaryRow(
    val feature: AiUsageFeature,
    val totalCount: Int,
    val successCount: Int,
    val fallbackCount: Int,
    val errorCount: Int,
    val cacheHitCount: Int,
    val lastUsedAt: Long,
    val lastErrorCategory: String?,
)

class AiCacheRepositoryImpl(
    private val cacheDao: AiAnalysisCacheDao,
    private val usageEventDao: AiUsageEventDao,
    private val imageOcrLimit: Int = 100,
    private val healthInsightLimit: Int = 50,
) : AiCacheRepository {

    override suspend fun getFresh(cacheKey: String, nowMillis: Long): AiAnalysisCacheEntry? =
        cacheDao.getFresh(cacheKey, nowMillis)

    override suspend fun put(entry: AiAnalysisCacheEntry, nowMillis: Long) {
        cacheDao.deleteExpired(nowMillis)
        cacheDao.upsert(entry)
        cacheDao.deleteOverflow(AiAnalysisKind.IMAGE_OCR, imageOcrLimit)
        cacheDao.deleteOverflow(AiAnalysisKind.HEALTH_INSIGHT, healthInsightLimit)
    }

    override suspend fun recordUsage(event: AiUsageEvent) {
        usageEventDao.insert(
            event.copy(
                inputHashPrefix = event.inputHashPrefix?.let(AiCacheKeyBuilder::hashPrefix),
            ),
        )
    }

    override suspend fun usageSummary(sinceMillis: Long): List<AiUsageSummaryRow> =
        usageEventDao.summarySince(sinceMillis)

    override suspend fun clearAll() {
        cacheDao.clearAll()
        usageEventDao.clearAll()
    }
}

object AiCacheKeyBuilder {
    fun build(
        kind: AiAnalysisKind,
        provider: String,
        model: String,
        promptVersion: Int,
        inputHash: String,
        locale: String,
    ): String = listOf(
        "kind=${kind.name}",
        "provider=$provider",
        "model=$model",
        "promptVersion=$promptVersion",
        "inputHash=$inputHash",
        "locale=$locale",
    ).joinToString("|")

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    fun hashPrefix(hash: String): String = hash.take(12)
}
