package com.example.medlog.data.repository

import com.example.medlog.data.local.HealthRecordDao
import com.example.medlog.data.model.HealthRecord
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class HealthRepositoryImpl @Inject constructor(
    private val dao: HealthRecordDao,
) : HealthRepository {

    override fun getAllRecords(): Flow<List<HealthRecord>> = dao.getAllRecords()

    override fun getRecordsByType(type: String): Flow<List<HealthRecord>> =
        dao.getRecordsByType(type)

    override fun getRecordsInRange(from: Long, to: Long): Flow<List<HealthRecord>> =
        dao.getRecordsInRange(from, to)

    override fun getRecordsByTypeInRange(type: String, from: Long, to: Long): Flow<List<HealthRecord>> =
        dao.getRecordsByTypeInRange(type, from, to)

    override fun getLatestRecordPerType(): Flow<List<HealthRecord>> =
        dao.getLatestRecordPerType()

    override suspend fun addRecord(record: HealthRecord): Long = dao.insert(record)

    override suspend fun updateRecord(record: HealthRecord) = dao.update(record)

    override suspend fun deleteRecord(record: HealthRecord) = dao.delete(record)
}
