package com.example.medlog.data.repository

import com.example.medlog.data.local.MedicationLogDao
import com.example.medlog.data.model.MedicationLog
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepositoryImpl @Inject constructor(
    private val logDao: MedicationLogDao,
) : LogRepository {

    override fun getLogsForDateRange(startMs: Long, endMs: Long): Flow<List<MedicationLog>> =
        logDao.getLogsForDateRange(startMs, endMs)

    override fun getLogsForMedication(medicationId: Long, limit: Int): Flow<List<MedicationLog>> =
        logDao.getLogsForMedication(medicationId, limit)

    override suspend fun getLogForMedicationAndDate(
        medicationId: Long,
        startMs: Long,
        endMs: Long,
    ): MedicationLog? = logDao.getLogForMedicationAndDate(medicationId, startMs, endMs)

    override suspend fun insertLog(log: MedicationLog): Long =
        logDao.insertLog(log)

    override suspend fun updateLog(log: MedicationLog) =
        logDao.updateLog(log)

    override suspend fun deleteLog(log: MedicationLog) =
        logDao.deleteLog(log)

    override suspend fun deleteLogsForDate(medicationId: Long, startMs: Long, endMs: Long) =
        logDao.deleteLogsForMedicationAndDate(medicationId, startMs, endMs)

    override fun getTakenCountForDateRange(startMs: Long, endMs: Long): Flow<Int> =
        logDao.getTakenCountForDateRange(startMs, endMs)

    override suspend fun getLogsForRangeOnce(startMs: Long, endMs: Long): List<MedicationLog> =
        logDao.getLogsForRangeOnce(startMs, endMs)
}
