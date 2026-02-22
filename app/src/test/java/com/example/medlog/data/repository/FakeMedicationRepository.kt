package com.example.medlog.data.repository

import com.example.medlog.data.model.Medication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * 纯内存假实现，用于单元测试。
 * 不依赖 Android 框架，可在 JVM 上直接运行。
 */
class FakeMedicationRepository : MedicationRepository {

    private val _medications = MutableStateFlow<List<Medication>>(emptyList())
    private var nextId = 1L

    override fun getActiveMedications(): Flow<List<Medication>> =
        _medications.map { list -> list.filter { !it.isArchived } }

    override fun getArchivedMedications(): Flow<List<Medication>> =
        _medications.map { list -> list.filter { it.isArchived } }

    override fun getAllMedications(): Flow<List<Medication>> = _medications

    override suspend fun getMedicationById(id: Long): Medication? =
        _medications.value.find { it.id == id }

    override suspend fun addMedication(medication: Medication): Long {
        val id = nextId++
        _medications.value = _medications.value + medication.copy(id = id)
        return id
    }

    override suspend fun updateMedication(medication: Medication) {
        _medications.value = _medications.value.map {
            if (it.id == medication.id) medication else it
        }
    }

    override suspend fun deleteMedication(medication: Medication) {
        _medications.value = _medications.value.filter { it.id != medication.id }
    }

    override suspend fun archiveMedication(id: Long) {
        _medications.value = _medications.value.map {
            if (it.id == id) it.copy(isArchived = true) else it
        }
    }

    override suspend fun unarchiveMedication(id: Long) {
        _medications.value = _medications.value.map {
            if (it.id == id) it.copy(isArchived = false) else it
        }
    }

    override suspend fun updateStock(id: Long, newStock: Double) {
        _medications.value = _medications.value.map {
            if (it.id == id) it.copy(stock = newStock) else it
        }
    }

    override suspend fun getActiveOnce(): List<Medication> =
        _medications.value.filter { !it.isArchived }
}
