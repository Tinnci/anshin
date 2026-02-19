package com.example.medlog.data.repository

import com.example.medlog.data.model.Medication
import com.example.medlog.data.model.MedicationLog
import kotlinx.coroutines.flow.Flow

interface MedicationRepository {
    // ── Medications ──────────────────────────────────────────────
    fun getActiveMedications(): Flow<List<Medication>>
    fun getArchivedMedications(): Flow<List<Medication>>
    suspend fun getMedicationById(id: Long): Medication?
    suspend fun addMedication(medication: Medication): Long
    suspend fun updateMedication(medication: Medication)
    suspend fun deleteMedication(medication: Medication)
    suspend fun archiveMedication(id: Long)
    suspend fun unarchiveMedication(id: Long)
    suspend fun updateStock(id: Long, newStock: Double)

    // ── Logs ──────────────────────────────────────────────────────
    fun getLogsForDateRange(startMs: Long, endMs: Long): Flow<List<MedicationLog>>
    fun getLogsForMedication(medicationId: Long, limit: Int = 60): Flow<List<MedicationLog>>
    suspend fun logMedication(log: MedicationLog): Long
    suspend fun deleteLog(log: MedicationLog)
    suspend fun deleteLogsForDate(medicationId: Long, startMs: Long, endMs: Long)
    fun getTakenCountForDateRange(startMs: Long, endMs: Long): Flow<Int>
}
