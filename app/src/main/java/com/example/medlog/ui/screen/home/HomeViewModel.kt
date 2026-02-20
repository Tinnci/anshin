package com.example.medlog.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.LogStatus
import com.example.medlog.data.model.Medication
import com.example.medlog.data.model.MedicationLog
import com.example.medlog.data.repository.LogRepository
import com.example.medlog.data.repository.MedicationRepository
import com.example.medlog.notification.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import javax.inject.Inject

data class MedicationWithStatus(
    val medication: Medication,
    val log: MedicationLog? = null,
) {
    val isTaken get() = log?.status == LogStatus.TAKEN
    val isSkipped get() = log?.status == LogStatus.SKIPPED
}

data class HomeUiState(
    val items: List<MedicationWithStatus> = emptyList(),
    val takenCount: Int = 0,
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    /** 当前连续服药天数 */
    val currentStreak: Int = 0,
    /** 历史最长连续天数 */
    val longestStreak: Int = 0,
) {
    /**
     * 药品按分类分组（分类为空的归入"其他"组，统一展示）。
     * 当所有药品无分类时返回单个 "" -> all 分组（供卡片列表扁平化渲染）。
     */
    val groupedItems: List<Pair<String, List<MedicationWithStatus>>> by lazy {
        val hasCat = items.any { it.medication.category.isNotBlank() }
        if (!hasCat) return@lazy listOf("" to items)
        items
            .groupBy { it.medication.category.ifBlank { "其他" } }
            .entries
            .sortedWith(
                // 中成药相关分类排序靠前，其次按药名首字母
                compareBy(
                    { if (it.key.contains("中成药") || TCM_CATEGORY_KEYWORDS.any { kw -> it.key.contains(kw) }) 0 else 1 },
                    { it.key },
                )
            )
            .map { it.key to it.value }
    }

    companion object {
        private val TCM_CATEGORY_KEYWORDS = listOf(
            "理气", "补益", "清热", "祛湿", "活血", "止咳", "安神", "妇科", "骨伤", "外科",
        )
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val medicationRepo: MedicationRepository,
    private val logRepo: LogRepository,
    private val notificationHelper: NotificationHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeMedications()
        computeStreak()
        scanLowStockOnLaunch()
    }

    private fun observeMedications() {
        viewModelScope.launch {
            val today = todayRange()
            combine(
                medicationRepo.getActiveMedications(),
                logRepo.getLogsForDateRange(today.first, today.second),
            ) { meds, logs ->
                val items = meds.map { med ->
                    MedicationWithStatus(med, logs.find { it.medicationId == med.id })
                }
                HomeUiState(
                    items = items,
                    takenCount = items.count { it.isTaken },
                    totalCount = items.size,
                    isLoading = false,
                )
            }.catch { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message,
                )
            }.collect { state ->
                _uiState.value = state
                // 实时更新今日进度通知（Live Activity 风格）
                val pending = state.items
                    .filter { !it.isTaken && !it.isSkipped }
                    .map { it.medication.name }
                notificationHelper.showOrUpdateProgressNotification(
                    taken        = state.takenCount,
                    total        = state.totalCount,
                    pendingNames = pending,
                )
            }
        }
    }

    fun toggleMedicationStatus(item: MedicationWithStatus) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val today = todayRange()
            if (item.isTaken) {
                item.log?.let { logRepo.deleteLog(it) }
                item.medication.stock?.let { stock ->
                    medicationRepo.updateStock(item.medication.id, stock + item.medication.doseQuantity)
                }
                notificationHelper.scheduleAllReminders(item.medication)
            } else {
                logRepo.deleteLogsForDate(item.medication.id, today.first, today.second)
                logRepo.insertLog(
                    MedicationLog(
                        medicationId = item.medication.id,
                        scheduledTimeMs = scheduledMs(item.medication),
                        actualTakenTimeMs = now,
                        status = LogStatus.TAKEN,
                    )
                )
                item.medication.stock?.let { stock ->
                    val newStock = (stock - item.medication.doseQuantity).coerceAtLeast(0.0)
                    medicationRepo.updateStock(item.medication.id, newStock)
                    checkAndNotifyLowStock(item.medication, newStock)
                }
                notificationHelper.cancelAllReminders(item.medication.id)
            }
        }
    }

    fun skipMedication(item: MedicationWithStatus) {
        viewModelScope.launch {
            val today = todayRange()
            logRepo.deleteLogsForDate(item.medication.id, today.first, today.second)
            logRepo.insertLog(
                MedicationLog(
                    medicationId = item.medication.id,
                    scheduledTimeMs = scheduledMs(item.medication),
                    actualTakenTimeMs = null,
                    status = LogStatus.SKIPPED,
                )
            )
            notificationHelper.cancelAllReminders(item.medication.id)
        }
    }

    /** 撤销服药或跳过操作，根据当前 state 内的最新记录进行回退 */
    fun undoByMedicationId(medicationId: Long) {
        viewModelScope.launch {
            val currentItem = _uiState.value.items.find { it.medication.id == medicationId }
                ?: return@launch
            currentItem.log?.let { log ->
                logRepo.deleteLog(log)
                if (currentItem.isTaken) {
                    // 恢复库存并重新设置提醒
                    currentItem.medication.stock?.let { stock ->
                        medicationRepo.updateStock(
                            currentItem.medication.id,
                            stock + currentItem.medication.doseQuantity,
                        )
                    }
                    notificationHelper.scheduleAllReminders(currentItem.medication)
                } else if (currentItem.isSkipped) {
                    notificationHelper.scheduleAllReminders(currentItem.medication)
                }
            }
        }
    }

    fun takeAll() {
        _uiState.value.items
            .filter { !it.isTaken && !it.isSkipped }
            .forEach { toggleMedicationStatus(it) }
    }

    /** 触发服药时检查库存是否低于阈值，低于则推送通知 */
    private fun checkAndNotifyLowStock(med: Medication, newStock: Double) {
        val threshold = med.refillThreshold ?: return
        if (newStock <= threshold) {
            notificationHelper.showLowStockNotification(
                medicationId = med.id,
                medicationName = med.name,
                stock = newStock,
                unit = med.doseUnit,
            )
        }
    }

    /** App 启动时扫描所有活跃药品，补推低库存通知（防止用户忽略了通知） */
    private fun scanLowStockOnLaunch() {
        viewModelScope.launch {
            medicationRepo.getActiveMedications()
                .take(1)
                .catch { }
                .collect { meds ->
                    meds.forEach { med ->
                        val stock = med.stock ?: return@forEach
                        val threshold = med.refillThreshold ?: return@forEach
                        if (stock <= threshold) {
                            notificationHelper.showLowStockNotification(
                                medicationId = med.id,
                                medicationName = med.name,
                                stock = stock,
                                unit = med.doseUnit,
                            )
                        }
                    }
                }
        }
    }

    /** 计算连续服药天数，启动时跑一次 */
    private fun computeStreak() {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            // 取近90天日志，足够覆盖合理 streak
            val startMs = now - 90L * 24 * 60 * 60 * 1000
            logRepo.getLogsForDateRange(startMs, now)
                .take(1)
                .catch { }
                .collect { logs ->
                    // 按日期分组，只关心有 TAKEN 记录的日期
                    val daysWithTaken = logs
                        .filter { it.status == LogStatus.TAKEN }
                        .map {
                            Instant.ofEpochMilli(it.scheduledTimeMs)
                                .atZone(zone).toLocalDate()
                        }
                        .toSet()

                    val today = LocalDate.now()
                    // 当前 streak：从今天（或昨天）向前连续计数
                    val startDay = if (today in daysWithTaken) today else today.minusDays(1)
                    var current = 0
                    var cursor = startDay
                    while (cursor in daysWithTaken) {
                        current++
                        cursor = cursor.minusDays(1)
                    }

                    // 历史最长 streak
                    var longest = 0
                    var run = 0
                    var prev: LocalDate? = null
                    for (d in daysWithTaken.sorted()) {
                        run = if (prev != null && d == prev!!.plusDays(1)) run + 1 else 1
                        if (run > longest) longest = run
                        prev = d
                    }

                    _uiState.value = _uiState.value.copy(
                        currentStreak = current,
                        longestStreak = longest,
                    )
                }
        }
    }

    private fun todayRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        return start to (start + 24 * 60 * 60 * 1000 - 1)
    }

    private fun scheduledMs(med: Medication): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, med.reminderHour)
            set(Calendar.MINUTE, med.reminderMinute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}

