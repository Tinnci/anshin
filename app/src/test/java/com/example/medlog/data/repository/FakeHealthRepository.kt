package com.example.medlog.data.repository

import com.example.medlog.data.model.HealthRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeHealthRepository : HealthRepository {

    private val records = MutableStateFlow<List<HealthRecord>>(emptyList())
    private var nextId = 1L

    override fun getAllRecords(): Flow<List<HealthRecord>> =
        records.map { it.sortedByDescending { r -> r.timestamp } }

    override fun getRecordsByType(type: String): Flow<List<HealthRecord>> =
        records.map { list ->
            list.filter { it.type == type }.sortedByDescending { it.timestamp }
        }

    override fun getRecordsInRange(from: Long, to: Long): Flow<List<HealthRecord>> =
        records.map { list ->
            list.filter { it.timestamp in from..to }.sortedByDescending { it.timestamp }
        }

    override fun getRecordsByTypeInRange(type: String, from: Long, to: Long): Flow<List<HealthRecord>> =
        records.map { list ->
            list.filter { it.type == type && it.timestamp in from..to }
                .sortedByDescending { it.timestamp }
        }

    override fun getLatestRecordPerType(): Flow<List<HealthRecord>> =
        records.map { list ->
            list.groupBy { it.type }
                .mapValues { (_, recs) -> recs.maxByOrNull { it.timestamp }!! }
                .values.toList()
        }

    override suspend fun addRecord(record: HealthRecord): Long {
        val id = nextId++
        val saved = record.copy(id = id)
        records.value = records.value + saved
        return id
    }

    override suspend fun updateRecord(record: HealthRecord) {
        records.value = records.value.map { if (it.id == record.id) record else it }
    }

    override suspend fun deleteRecord(record: HealthRecord) {
        records.value = records.value.filter { it.id != record.id }
    }
}
