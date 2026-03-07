package com.example.medlog.data.repository

import com.example.medlog.data.model.SymptomLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSymptomRepository : SymptomRepository {

    private val logs = MutableStateFlow<List<SymptomLog>>(emptyList())
    private var nextId = 1L

    override fun getAllLogs(): Flow<List<SymptomLog>> =
        logs.map { it.sortedByDescending { l -> l.recordedAt } }

    override fun getLogsForDateRange(startMs: Long, endMs: Long): Flow<List<SymptomLog>> =
        logs.map { list ->
            list.filter { it.recordedAt in startMs..endMs }
                .sortedByDescending { it.recordedAt }
        }

    override fun getLogsForMedication(medId: Long): Flow<List<SymptomLog>> =
        logs.map { list ->
            list.filter { it.medicationId == medId }
                .sortedByDescending { it.recordedAt }
        }

    override suspend fun insert(log: SymptomLog): Long {
        val id = nextId++
        val saved = log.copy(id = id)
        logs.value = logs.value + saved
        return id
    }

    override suspend fun update(log: SymptomLog) {
        logs.value = logs.value.map { if (it.id == log.id) log else it }
    }

    override suspend fun delete(log: SymptomLog) {
        logs.value = logs.value.filter { it.id != log.id }
    }

    override suspend fun deleteById(id: Long) {
        logs.value = logs.value.filter { it.id != id }
    }
}
