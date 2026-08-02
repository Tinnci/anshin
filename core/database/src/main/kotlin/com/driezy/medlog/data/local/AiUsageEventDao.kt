package com.driezy.medlog.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.driezy.medlog.data.model.AiUsageEvent
import com.driezy.medlog.data.model.AiUsageSummaryRow

@Dao
interface AiUsageEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AiUsageEvent)

    @Query("DELETE FROM ai_usage_events WHERE timestamp < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    @Query(
        """
        SELECT
            feature AS feature,
            COUNT(*) AS totalCount,
            SUM(CASE WHEN result = 'SUCCESS' THEN 1 ELSE 0 END) AS successCount,
            SUM(CASE WHEN result = 'FALLBACK' THEN 1 ELSE 0 END) AS fallbackCount,
            SUM(CASE WHEN result = 'ERROR' THEN 1 ELSE 0 END) AS errorCount,
            SUM(CASE WHEN cacheHit = 1 THEN 1 ELSE 0 END) AS cacheHitCount,
            MAX(timestamp) AS lastUsedAt,
            (
                SELECT latest.errorCategory
                FROM ai_usage_events AS latest
                WHERE latest.feature = ai_usage_events.feature
                    AND latest.timestamp >= :sinceMillis
                    AND latest.errorCategory IS NOT NULL
                ORDER BY latest.timestamp DESC
                LIMIT 1
            ) AS lastErrorCategory
        FROM ai_usage_events
        WHERE timestamp >= :sinceMillis
        GROUP BY feature
        ORDER BY feature ASC
        """,
    )
    suspend fun summarySince(sinceMillis: Long): List<AiUsageSummaryRow>

    @Query("DELETE FROM ai_usage_events")
    suspend fun clearAll()
}
