package com.example.medlog.data.repository

import com.example.medlog.data.model.Medication
import kotlinx.coroutines.flow.Flow

/**
 * 药品配置领域的唯一真实来源（SSOT）。
 * 职责：药品 CRUD + 归档 + 库存；不涉及日志（SRP）。
 */
interface MedicationRepository {
    fun getActiveMedications(): Flow<List<Medication>>
    fun getArchivedMedications(): Flow<List<Medication>>
    fun getAllMedications(): Flow<List<Medication>>
    suspend fun getMedicationById(id: Long): Medication?
    suspend fun addMedication(medication: Medication): Long
    suspend fun updateMedication(medication: Medication)
    suspend fun deleteMedication(medication: Medication)
    suspend fun archiveMedication(id: Long)
    suspend fun unarchiveMedication(id: Long)
    suspend fun updateStock(id: Long, newStock: Double)
}
