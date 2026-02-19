package com.example.medlog.data.repository

import com.example.medlog.data.model.MedicationLog
import kotlinx.coroutines.flow.Flow

/**
 * 服药日志领域的唯一真实来源（SSOT）。
 * 职责：增删查日志；不涉及药品配置（SRP）。
 */
interface LogRepository {
    fun getLogsForDateRange(startMs: Long, endMs: Long): Flow<List<MedicationLog>>
    fun getLogsForMedication(medicationId: Long, limit: Int = 60): Flow<List<MedicationLog>>
    suspend fun getLogForMedicationAndDate(medicationId: Long, startMs: Long, endMs: Long): MedicationLog?
    suspend fun insertLog(log: MedicationLog): Long
    suspend fun deleteLog(log: MedicationLog)
    suspend fun deleteLogsForDate(medicationId: Long, startMs: Long, endMs: Long)
    fun getTakenCountForDateRange(startMs: Long, endMs: Long): Flow<Int>
}
