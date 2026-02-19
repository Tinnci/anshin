package com.example.medlog.data.repository

import com.example.medlog.data.local.MedicationDao
import com.example.medlog.data.local.MedicationLogDao
import com.example.medlog.data.model.Medication
import com.example.medlog.data.model.MedicationLog
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationRepositoryImpl @Inject constructor(
    private val medicationDao: MedicationDao,
    private val medicationLogDao: MedicationLogDao,
) : MedicationRepository {

    override fun getActiveMedications(): Flow<List<Medication>> =
        medicationDao.getActiveMedications()

    override fun getArchivedMedications(): Flow<List<Medication>> =
        medicationDao.getArchivedMedications()

    override suspend fun getMedicationById(id: Long): Medication? =
        medicationDao.getMedicationById(id)

    override suspend fun addMedication(medication: Medication): Long =
        medicationDao.insertMedication(medication)

    override suspend fun updateMedication(medication: Medication) =
        medicationDao.updateMedication(medication)

    override suspend fun deleteMedication(medication: Medication) =
        medicationDao.deleteMedication(medication)

    override suspend fun archiveMedication(id: Long) = medicationDao.archiveMedication(id)

    override suspend fun unarchiveMedication(id: Long) = medicationDao.unarchiveMedication(id)

    override suspend fun updateStock(id: Long, newStock: Double) =
        medicationDao.updateStock(id, newStock)

    override fun getLogsForDateRange(startMs: Long, endMs: Long): Flow<List<MedicationLog>> =
        medicationLogDao.getLogsForDateRange(startMs, endMs)

    override fun getLogsForMedication(medicationId: Long, limit: Int): Flow<List<MedicationLog>> =
        medicationLogDao.getLogsForMedication(medicationId, limit)

    override suspend fun logMedication(log: MedicationLog): Long =
        medicationLogDao.insertLog(log)

    override suspend fun deleteLog(log: MedicationLog) = medicationLogDao.deleteLog(log)

    override suspend fun deleteLogsForDate(medicationId: Long, startMs: Long, endMs: Long) =
        medicationLogDao.deleteLogsForMedicationAndDate(medicationId, startMs, endMs)

    override fun getTakenCountForDateRange(startMs: Long, endMs: Long): Flow<Int> =
        medicationLogDao.getTakenCountForDateRange(startMs, endMs)
}
