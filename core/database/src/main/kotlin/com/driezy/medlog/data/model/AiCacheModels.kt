package com.driezy.medlog.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AiAnalysisKind {
    IMAGE_OCR,
    HEALTH_INSIGHT,
}

enum class AiUsageFeature {
    IMAGE_OCR,
    HEALTH_INSIGHT,
}

enum class AiUsageResult {
    SUCCESS,
    FALLBACK,
    ERROR,
}

enum class NetworkType {
    WIFI,
    CELLULAR,
    OFFLINE_UNKNOWN,
}

@Entity(
    tableName = "ai_analysis_cache",
    indices = [
        Index("kind"),
        Index("expiresAt"),
        Index(value = ["kind", "createdAt"]),
    ],
)
data class AiAnalysisCacheEntry(
    @PrimaryKey
    val cacheKey: String,
    val kind: AiAnalysisKind,
    val provider: String,
    val model: String,
    val promptVersion: Int,
    val inputHash: String,
    val locale: String,
    val responseJson: String,
    val createdAt: Long,
    val expiresAt: Long,
)

@Entity(
    tableName = "ai_usage_events",
    indices = [
        Index("timestamp"),
        Index("feature"),
        Index("result"),
    ],
)
data class AiUsageEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val feature: AiUsageFeature,
    val provider: String,
    val model: String,
    val networkType: NetworkType,
    val cacheHit: Boolean,
    val result: AiUsageResult,
    val errorCategory: String? = null,
    val inputHashPrefix: String? = null,
    val latencyMs: Long? = null,
)

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
