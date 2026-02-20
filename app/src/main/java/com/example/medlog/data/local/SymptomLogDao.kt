package com.example.medlog.data.local

import androidx.room.*
import com.example.medlog.data.model.SymptomLog
import kotlinx.coroutines.flow.Flow

@Dao
interface SymptomLogDao {

    @Query("SELECT * FROM symptom_logs ORDER BY recordedAt DESC")
    fun getAllLogs(): Flow<List<SymptomLog>>

    @Query("SELECT * FROM symptom_logs WHERE recordedAt BETWEEN :startMs AND :endMs ORDER BY recordedAt DESC")
    fun getLogsForDateRange(startMs: Long, endMs: Long): Flow<List<SymptomLog>>

    @Query("SELECT * FROM symptom_logs WHERE medicationId = :medId ORDER BY recordedAt DESC")
    fun getLogsForMedication(medId: Long): Flow<List<SymptomLog>>

    @Query("SELECT * FROM symptom_logs WHERE id = :id")
    suspend fun getById(id: Long): SymptomLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SymptomLog): Long

    @Update
    suspend fun update(log: SymptomLog)

    @Delete
    suspend fun delete(log: SymptomLog)

    @Query("DELETE FROM symptom_logs WHERE id = :id")
    suspend fun deleteById(id: Long)
}
