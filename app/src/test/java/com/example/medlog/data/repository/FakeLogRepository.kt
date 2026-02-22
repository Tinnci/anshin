package com.example.medlog.data.repository

import com.example.medlog.data.model.MedicationLog
import com.example.medlog.data.model.LogStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * 纯内存假实现，用于 HistoryViewModel / HomeViewModel 单元测试。
 * getLogsForDateRange 忽略日期范围过滤，直接返回所有受控日志，
 * 便于测试无需关心真实时间戳。
 */
class FakeLogRepository : LogRepository {

    private val _logs = MutableStateFlow<List<MedicationLog>>(emptyList())
    private var nextId = 1L

    /** 直接设置日志列表（测试辅助方法） */
    fun setLogs(logs: List<MedicationLog>) {
        _logs.value = logs
    }

    override fun getLogsForDateRange(startMs: Long, endMs: Long): Flow<List<MedicationLog>> =
        _logs  // Fake: return all logs; test data is already scoped to relevant dates

    override fun getLogsForMedication(medicationId: Long, limit: Int): Flow<List<MedicationLog>> =
        _logs.map { list -> list.filter { it.medicationId == medicationId }.takeLast(limit) }

    override suspend fun getLogForMedicationAndDate(
        medicationId: Long,
        startMs: Long,
        endMs: Long,
    ): MedicationLog? = _logs.value.find {
        it.medicationId == medicationId &&
            it.scheduledTimeMs in startMs..endMs
    }

    override suspend fun insertLog(log: MedicationLog): Long {
        val id = nextId++
        _logs.value = _logs.value + log.copy(id = id)
        return id
    }

    override suspend fun updateLog(log: MedicationLog) {
        _logs.value = _logs.value.map { if (it.id == log.id) log else it }
    }

    override suspend fun deleteLog(log: MedicationLog) {
        _logs.value = _logs.value.filter { it.id != log.id }
    }

    override suspend fun deleteLogsForDate(medicationId: Long, startMs: Long, endMs: Long) {
        _logs.value = _logs.value.filter { log ->
            log.medicationId != medicationId || log.scheduledTimeMs !in startMs..endMs
        }
    }

    override fun getTakenCountForDateRange(startMs: Long, endMs: Long): Flow<Int> =
        _logs.map { list ->
            list.count { it.scheduledTimeMs in startMs..endMs && it.status == LogStatus.TAKEN }
        }
}
