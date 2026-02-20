package com.example.medlog.data.repository

import com.example.medlog.data.local.SymptomLogDao
import com.example.medlog.data.model.SymptomLog
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SymptomRepositoryImpl @Inject constructor(
    private val dao: SymptomLogDao,
) : SymptomRepository {

    override fun getAllLogs(): Flow<List<SymptomLog>> = dao.getAllLogs()

    override fun getLogsForDateRange(startMs: Long, endMs: Long): Flow<List<SymptomLog>> =
        dao.getLogsForDateRange(startMs, endMs)

    override fun getLogsForMedication(medId: Long): Flow<List<SymptomLog>> =
        dao.getLogsForMedication(medId)

    override suspend fun insert(log: SymptomLog): Long = dao.insert(log)

    override suspend fun update(log: SymptomLog) = dao.update(log)

    override suspend fun delete(log: SymptomLog) = dao.delete(log)

    override suspend fun deleteById(id: Long) = dao.deleteById(id)
}
