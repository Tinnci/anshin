package com.driezy.medlog.data.repository

import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.data.model.MedicationLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * 纯内存假实现，用于 HistoryViewModel / HomeViewModel 单元测试。
 * 日期查询与真实仓库使用相同的包含边界，便于覆盖跨日和时区场景。
 */
class FakeLogRepository : LogRepository {

    private val logsState = MutableStateFlow<List<MedicationLog>>(emptyList())
    private var nextId = 1L

    /** 直接设置日志列表（测试辅助方法） */
    fun setLogs(logs: List<MedicationLog>) {
        logsState.value = logs
    }

    fun currentLogs(): List<MedicationLog> = logsState.value

    override fun getLogsForDateRange(startMs: Long, endMs: Long): Flow<List<MedicationLog>> =
        logsState.map { logs -> logs.filter { it.scheduledTimeMs in startMs..endMs } }

    override fun getLogsForMedication(medicationId: Long, limit: Int): Flow<List<MedicationLog>> =
        logsState.map { list -> list.filter { it.medicationId == medicationId }.takeLast(limit) }

    override suspend fun getLogForMedicationAndDate(medicationId: Long, startMs: Long, endMs: Long): MedicationLog? =
        logsState.value.find {
            it.medicationId == medicationId &&
                it.scheduledTimeMs in startMs..endMs
        }

    override suspend fun getLogForScheduledTime(medicationId: Long, scheduledTimeMs: Long): MedicationLog? =
        logsState.value.find { it.medicationId == medicationId && it.scheduledTimeMs == scheduledTimeMs }

    override suspend fun insertLog(log: MedicationLog): Long {
        val id = nextId++
        logsState.value = logsState.value + log.copy(id = id)
        return id
    }

    override suspend fun updateLog(log: MedicationLog) {
        logsState.value = logsState.value.map { if (it.id == log.id) log else it }
    }

    override suspend fun deleteLog(log: MedicationLog) {
        logsState.value = logsState.value.filter { it.id != log.id }
    }

    override suspend fun deleteLogsForDate(medicationId: Long, startMs: Long, endMs: Long) {
        logsState.value = logsState.value.filter { log ->
            log.medicationId != medicationId || log.scheduledTimeMs !in startMs..endMs
        }
    }

    override suspend fun deleteLogForScheduledTime(medicationId: Long, scheduledTimeMs: Long) {
        logsState.value = logsState.value.filterNot { log ->
            log.medicationId == medicationId && log.scheduledTimeMs == scheduledTimeMs
        }
    }

    override fun getTakenCountForDateRange(startMs: Long, endMs: Long): Flow<Int> = logsState.map { list ->
        list.count { it.scheduledTimeMs in startMs..endMs && it.status == LogStatus.TAKEN }
    }

    override suspend fun getLogsForRangeOnce(startMs: Long, endMs: Long): List<MedicationLog> =
        logsState.value.filter { it.scheduledTimeMs in startMs..endMs }
}
