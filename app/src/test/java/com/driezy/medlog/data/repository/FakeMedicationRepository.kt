package com.driezy.medlog.data.repository

import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.model.MedicationPlanRevision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * 纯内存假实现，用于单元测试。
 * 不依赖 Android 框架，可在 JVM 上直接运行。
 */
class FakeMedicationRepository : MedicationRepository {

    private val medicationsState = MutableStateFlow<List<Medication>>(emptyList())
    private var nextId = 1L

    override fun getActiveMedications(): Flow<List<Medication>> =
        medicationsState.map { list -> list.filter { !it.isArchived } }

    override fun getArchivedMedications(): Flow<List<Medication>> =
        medicationsState.map { list -> list.filter { it.isArchived } }

    override fun getAllMedications(): Flow<List<Medication>> = medicationsState
    val planRevisions = MutableStateFlow<List<MedicationPlanRevision>>(emptyList())
    override fun observePlanRevisions(): Flow<List<MedicationPlanRevision>> = planRevisions

    override suspend fun getMedicationById(id: Long): Medication? = medicationsState.value.find { it.id == id }

    override suspend fun addMedication(medication: Medication): Long {
        val id = nextId++
        medicationsState.value = medicationsState.value + medication.copy(id = id)
        return id
    }

    override suspend fun updateMedication(medication: Medication) {
        medicationsState.value = medicationsState.value.map {
            if (it.id == medication.id) medication else it
        }
    }

    override suspend fun updateMedications(medications: List<Medication>) {
        val replacements = medications.associateBy(Medication::id)
        medicationsState.value = medicationsState.value.map { replacements[it.id] ?: it }
    }

    override suspend fun mergeMedicationsByName(medications: List<Medication>) {
        val current = medicationsState.value
        val names = current.filterNot(Medication::isArchived)
            .map { it.name.trim().lowercase() }
            .toMutableSet()
        val additions = medications.mapNotNull { medication ->
            val normalizedName = medication.name.trim().lowercase()
            if (names.add(normalizedName)) medication.copy(id = nextId++) else null
        }
        medicationsState.value = current + additions
    }

    override suspend fun replaceActiveMedications(medications: List<Medication>) {
        val archived = medicationsState.value.filter(Medication::isArchived)
        val replacements = medications.map { it.copy(id = nextId++) }
        medicationsState.value = archived + replacements
    }

    override suspend fun deleteMedication(medication: Medication) {
        medicationsState.value = medicationsState.value.filter { it.id != medication.id }
    }

    override suspend fun archiveMedication(id: Long) {
        medicationsState.value = medicationsState.value.map {
            if (it.id == id) it.copy(isArchived = true) else it
        }
    }

    override suspend fun unarchiveMedication(id: Long) {
        medicationsState.value = medicationsState.value.map {
            if (it.id == id) it.copy(isArchived = false) else it
        }
    }

    override suspend fun updateStock(id: Long, newStock: Double) {
        medicationsState.value = medicationsState.value.map {
            if (it.id == id) it.copy(stock = newStock) else it
        }
    }

    override suspend fun getActiveOnce(): List<Medication> = medicationsState.value.filter { !it.isArchived }
}
