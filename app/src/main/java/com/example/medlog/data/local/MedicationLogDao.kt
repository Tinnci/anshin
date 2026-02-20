package com.example.medlog.data.local

import androidx.room.*
import com.example.medlog.data.model.MedicationLog
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationLogDao {

    @Query(
        """
        SELECT * FROM medication_logs
        WHERE scheduledTimeMs BETWEEN :startMs AND :endMs
        ORDER BY scheduledTimeMs ASC
        """
    )
    fun getLogsForDateRange(startMs: Long, endMs: Long): Flow<List<MedicationLog>>

    @Query(
        """
        SELECT * FROM medication_logs
        WHERE medicationId = :medicationId
        ORDER BY scheduledTimeMs DESC
        LIMIT :limit
        """
    )
    fun getLogsForMedication(medicationId: Long, limit: Int = 60): Flow<List<MedicationLog>>

    @Query(
        """
        SELECT * FROM medication_logs
        WHERE medicationId = :medicationId
          AND scheduledTimeMs BETWEEN :startMs AND :endMs
        """
    )
    suspend fun getLogForMedicationAndDate(
        medicationId: Long,
        startMs: Long,
        endMs: Long,
    ): MedicationLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: MedicationLog): Long

    @Update
    suspend fun updateLog(log: MedicationLog)

    @Delete
    suspend fun deleteLog(log: MedicationLog)

    @Query("DELETE FROM medication_logs WHERE medicationId = :medicationId AND scheduledTimeMs BETWEEN :startMs AND :endMs")
    suspend fun deleteLogsForMedicationAndDate(medicationId: Long, startMs: Long, endMs: Long)

    @Query("SELECT COUNT(*) FROM medication_logs WHERE status = 'TAKEN' AND scheduledTimeMs BETWEEN :startMs AND :endMs")
    fun getTakenCountForDateRange(startMs: Long, endMs: Long): Flow<Int>

    /** Widget 专用：一次性查询某天开始后的所有日志 */
    @Query("SELECT * FROM medication_logs WHERE scheduledTimeMs >= :startMs AND scheduledTimeMs < :startMs + 86400000")
    suspend fun getLogsForDateOnce(startMs: Long): List<MedicationLog>
}
