package com.example.medlog.data.repository

import com.example.medlog.data.model.SymptomLog
import kotlinx.coroutines.flow.Flow

interface SymptomRepository {
    fun getAllLogs(): Flow<List<SymptomLog>>
    fun getLogsForDateRange(startMs: Long, endMs: Long): Flow<List<SymptomLog>>
    fun getLogsForMedication(medId: Long): Flow<List<SymptomLog>>
    suspend fun insert(log: SymptomLog): Long
    suspend fun update(log: SymptomLog)
    suspend fun delete(log: SymptomLog)
    suspend fun deleteById(id: Long)
}
