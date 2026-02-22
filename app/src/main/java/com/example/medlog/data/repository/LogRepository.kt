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
    /** 更新已有日志记录（如修改实际服药时间） */
    suspend fun updateLog(log: MedicationLog)
    suspend fun deleteLog(log: MedicationLog)
    suspend fun deleteLogsForDate(medicationId: Long, startMs: Long, endMs: Long)
    fun getTakenCountForDateRange(startMs: Long, endMs: Long): Flow<Int>
    /** Widget / 一次性读取时间范围内的日志（非 Flow），用于 Glance Widget 刷新 */
    suspend fun getLogsForRangeOnce(startMs: Long, endMs: Long): List<MedicationLog>
}
