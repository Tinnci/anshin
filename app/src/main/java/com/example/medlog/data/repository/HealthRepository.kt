package com.example.medlog.data.repository

import com.example.medlog.data.model.HealthRecord
import kotlinx.coroutines.flow.Flow

interface HealthRepository {
    fun getAllRecords(): Flow<List<HealthRecord>>
    fun getRecordsByType(type: String): Flow<List<HealthRecord>>
    fun getRecordsInRange(from: Long, to: Long): Flow<List<HealthRecord>>
    fun getRecordsByTypeInRange(type: String, from: Long, to: Long): Flow<List<HealthRecord>>
    fun getLatestRecordPerType(): Flow<List<HealthRecord>>
    suspend fun addRecord(record: HealthRecord): Long
    suspend fun updateRecord(record: HealthRecord)
    suspend fun deleteRecord(record: HealthRecord)
}
