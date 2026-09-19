package com.driezy.medlog.data.repository

import com.driezy.medlog.data.local.MedicationDao
import com.driezy.medlog.data.local.TransactionRunner
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.model.MedicationPlanRevision
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationRepositoryImpl @Inject constructor(
    private val medicationDao: MedicationDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) : MedicationRepository {

    override fun getActiveMedications(): Flow<List<Medication>> = medicationDao.getActiveMedications()

    override fun getArchivedMedications(): Flow<List<Medication>> = medicationDao.getArchivedMedications()

    override fun getAllMedications(): Flow<List<Medication>> = medicationDao.getAllMedications()
    override fun observePlanRevisions(): Flow<List<MedicationPlanRevision>> = medicationDao.observePlanRevisions()

    override suspend fun getMedicationById(id: Long): Medication? = medicationDao.getMedicationById(id)

    override suspend fun addMedication(medication: Medication): Long = medicationDao.insertMedication(medication)

    override suspend fun updateMedication(medication: Medication) = medicationDao.updatePlan(medication, clock.millis())

    override suspend fun updateMedications(medications: List<Medication>) = transactions.withTransaction {
        val changedAt = clock.millis()
        medications.forEach { medicationDao.updatePlan(it, changedAt) }
    }

    override suspend fun mergeMedicationsByName(medications: List<Medication>) =
        medicationDao.mergeMedicationsByName(medications)

    override suspend fun replaceActiveMedications(medications: List<Medication>) =
        medicationDao.replaceActiveMedications(medications)

    override suspend fun deleteMedication(medication: Medication) = medicationDao.deleteMedication(medication)

    override suspend fun archiveMedication(id: Long) = setArchived(id, true)

    override suspend fun unarchiveMedication(id: Long) = setArchived(id, false)

    private suspend fun setArchived(id: Long, archived: Boolean) = transactions.withTransaction {
        medicationDao.getMedicationById(id)?.let {
            medicationDao.updatePlan(it.copy(isArchived = archived), clock.millis())
        }
        Unit
    }

    override suspend fun updateStock(id: Long, newStock: Double) = medicationDao.updateStock(id, newStock)

    override suspend fun getActiveOnce(): List<Medication> = medicationDao.getAllMedicationsOnce()
}
