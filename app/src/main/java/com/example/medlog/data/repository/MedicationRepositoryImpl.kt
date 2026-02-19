package com.example.medlog.data.repository

import com.example.medlog.data.local.MedicationDao
import com.example.medlog.data.model.Medication
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationRepositoryImpl @Inject constructor(
    private val medicationDao: MedicationDao,
) : MedicationRepository {

    override fun getActiveMedications(): Flow<List<Medication>> =
        medicationDao.getActiveMedications()

    override fun getArchivedMedications(): Flow<List<Medication>> =
        medicationDao.getArchivedMedications()

    override fun getAllMedications(): Flow<List<Medication>> =
        medicationDao.getAllMedications()

    override suspend fun getMedicationById(id: Long): Medication? =
        medicationDao.getMedicationById(id)

    override suspend fun addMedication(medication: Medication): Long =
        medicationDao.insertMedication(medication)

    override suspend fun updateMedication(medication: Medication) =
        medicationDao.updateMedication(medication)

    override suspend fun deleteMedication(medication: Medication) =
        medicationDao.deleteMedication(medication)

    override suspend fun archiveMedication(id: Long) =
        medicationDao.archiveMedication(id)

    override suspend fun unarchiveMedication(id: Long) =
        medicationDao.unarchiveMedication(id)

    override suspend fun updateStock(id: Long, newStock: Double) =
        medicationDao.updateStock(id, newStock)
}
