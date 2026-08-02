package com.driezy.medlog.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.driezy.medlog.data.model.AiAnalysisCacheEntry
import com.driezy.medlog.data.model.AiAnalysisKind

@Dao
interface AiAnalysisCacheDao {

    @Query("SELECT * FROM ai_analysis_cache WHERE cacheKey = :cacheKey AND expiresAt > :nowMillis LIMIT 1")
    suspend fun getFresh(cacheKey: String, nowMillis: Long): AiAnalysisCacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AiAnalysisCacheEntry)

    @Query("DELETE FROM ai_analysis_cache WHERE expiresAt <= :nowMillis")
    suspend fun deleteExpired(nowMillis: Long)

    @Query(
        """
        DELETE FROM ai_analysis_cache
        WHERE kind = :kind
        AND cacheKey NOT IN (
            SELECT cacheKey FROM ai_analysis_cache
            WHERE kind = :kind
            ORDER BY createdAt DESC
            LIMIT :keep
        )
        """,
    )
    suspend fun deleteOverflow(kind: AiAnalysisKind, keep: Int)

    @Query("DELETE FROM ai_analysis_cache")
    suspend fun clearAll()
}
