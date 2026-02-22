package com.example.medlog.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.DrugInteraction
import com.example.medlog.data.model.LogStatus
import com.example.medlog.data.model.Medication
import com.example.medlog.data.model.MedicationLog
import com.example.medlog.data.model.TimePeriod
import com.example.medlog.data.repository.LogRepository
import com.example.medlog.data.repository.MedicationRepository
import com.example.medlog.domain.StreakCalculator
import com.example.medlog.data.repository.UserPreferencesRepository
import com.example.medlog.domain.ToggleMedicationDoseUseCase
import com.example.medlog.domain.ImportPlanUseCase
import com.example.medlog.domain.ImportMode
import com.example.medlog.domain.PlanExport
import com.example.medlog.domain.PlanExportCodec
import com.example.medlog.domain.todayRange
import com.example.medlog.interaction.InteractionRuleEngine
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
    /** 检测到的药品相互作用列表 */
    val interactions: List<DrugInteraction> = emptyList(),
    /** true = 按服药时段分组；false = 按分类分组 */
    val groupByTime: Boolean = true,
    /** 已全部服用的时段默认折叠 */
    val autoCollapseCompletedGroups: Boolean = true,
) {
    /**
     * 药品按分类分组（分类为空的归入"其他"组，统一展示）。
     * 当所有药品无分类时返回单个 "" -> all 分组（供卡片列表扁平化渲染）。
     * 注意：PRN 按需药品不参与分组，见 [prnItems]。
     */
    val groupedItems: List<Pair<String, List<MedicationWithStatus>>> by lazy {
        val regularItems = items.filter { !it.medication.isPRN }
        val hasCat = regularItems.any { it.medication.category.isNotBlank() }
        if (!hasCat) return@lazy listOf("" to regularItems)
        regularItems
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

    /**
     * 按服药时段分组，组内按提醒小时排序。
     * 同时暴露 [TimePeriod] 对象，供 UI 渲染图标及"一键服用本时段"。
     */
    val groupedByTime: List<Pair<String, List<MedicationWithStatus>>> by lazy {
        groupedByTimePeriod.map { (tp, meds) -> tp.key to meds }
    }

    /** 带 [TimePeriod] key 的时段分组，UI 需要时段图标及 key 时使用。PRN 按需药品除外。 */
    val groupedByTimePeriod: List<Pair<TimePeriod, List<MedicationWithStatus>>> by lazy {
        val periodOrder = listOf(
            "morning", "beforeBreakfast", "afterBreakfast",
            "beforeLunch", "afterLunch", "afternoon",
            "beforeDinner", "afterDinner", "evening", "bedtime", "exact",
        )
        fun orderOf(key: String) = periodOrder.indexOf(key).let { if (it < 0) 99 else it }
        items
            .filter { !it.medication.isPRN }
            .sortedWith(
                compareBy(
                    { orderOf(it.medication.timePeriod) },
                    { it.medication.reminderHour },
                    { it.medication.reminderMinute },
                    { it.medication.name },
                ),
            )
            .groupBy { it.medication.timePeriod }
            .entries
            .sortedBy { (key, _) -> orderOf(key) }
            .map { (key, meds) -> TimePeriod.fromKey(key) to meds }
    }

    /** PRN 按需药品列表（单独渲染为"随时需要"区域） */
    val prnItems: List<MedicationWithStatus> by lazy {
        items.filter { it.medication.isPRN }
    }

    /**
     * 下一个仍有待服药品的时段（用于"下一服"提示 Chip）。
     * 仅在 [takenCount] 大于 0 且未全部完成时有意义。
     */
    val nextUpPeriod: Pair<TimePeriod, String>? by lazy {
        groupedByTimePeriod
            .firstOrNull { (_, meds) -> meds.any { !it.isTaken && !it.isSkipped } }
            ?.let { (tp, meds) ->
                val med = meds.first { !it.isTaken && !it.isSkipped }
                tp to "%02d:%02d".format(med.medication.reminderHour, med.medication.reminderMinute)
            }
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
    private val toggleDoseUseCase: ToggleMedicationDoseUseCase,
    private val importPlanUseCase: ImportPlanUseCase,
    private val interactionEngine: InteractionRuleEngine,
    private val prefsRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // ── QR 导出/导入状态 ──────────────────────────────────────────────────────
    /** 扫描到的待确认计划；非 null 时触发导入预览对话框 */
    private val _importPreview = MutableStateFlow<PlanExport?>(null)
    val importPreview: StateFlow<PlanExport?> = _importPreview.asStateFlow()

    /** 二维码解码错误消息（格式不匹配等） */
    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    /** 导入成功事件（值为导入的药品数量）；供 HomeScreen 显示 Snackbar */
    private val _importSuccess = MutableSharedFlow<Int>(replay = 0, extraBufferCapacity = 1)
    val importSuccess: SharedFlow<Int> = _importSuccess.asSharedFlow()

    /** 上次推送今日进度通知时的 (taken, total)；避免重复更新通知 */
    private var lastProgressNotifState = -1 to -1

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
                prefsRepository.settingsFlow,
            ) { meds, logs, prefs ->
                val items = meds.map { med ->
                    MedicationWithStatus(med, logs.find { it.medicationId == med.id })
                }
                val interactions = if (prefs.enableDrugInteractionCheck) {
                    interactionEngine.check(meds)
                } else emptyList()
                val scheduledItems = items.filter { !it.medication.isPRN }
                HomeUiState(
                    items = items,
                    takenCount = scheduledItems.count { it.isTaken },
                    totalCount = scheduledItems.size,
                    isLoading = false,
                    interactions = interactions,
                    autoCollapseCompletedGroups = prefs.autoCollapseCompletedGroups,
                )
            }.catch { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message,
                )
            }.collect { state ->
                // 保留用户的分组偏好，不被新状态覆盖
                _uiState.value = state.copy(groupByTime = _uiState.value.groupByTime)
                // 实时更新今日进度通知（去重：仅在 taken/total 真正变化时更新）
                val taken = state.takenCount
                val total = state.totalCount
                if (taken != lastProgressNotifState.first || total != lastProgressNotifState.second) {
                    lastProgressNotifState = taken to total
                    val pending = state.items
                        .filter { !it.isTaken && !it.isSkipped }
                        .map { it.medication.name }
                    notificationHelper.showOrUpdateProgressNotification(
                        taken        = taken,
                        total        = total,
                        pendingNames = pending,
                    )
                }
            }
        }
    }

    fun toggleMedicationStatus(item: MedicationWithStatus) {
        viewModelScope.launch {
            if (item.isTaken) {
                item.log?.let { toggleDoseUseCase.undoTaken(item.medication, it) }
            } else {
                toggleDoseUseCase.markTaken(item.medication, item.log)
                item.medication.stock?.let { stock ->
                    val newStock = (stock - item.medication.doseQuantity).coerceAtLeast(0.0)
                    checkAndNotifyLowStock(item.medication, newStock)
                }
            }
        }
    }

    fun skipMedication(item: MedicationWithStatus) {
        viewModelScope.launch {
            toggleDoseUseCase.markSkipped(item.medication)
        }
    }

    /** 撤销服药或跳过操作，根据当前 state 内的最新记录进行回退 */
    fun undoByMedicationId(medicationId: Long) {
        viewModelScope.launch {
            val currentItem = _uiState.value.items.find { it.medication.id == medicationId }
                ?: return@launch
            val log = currentItem.log ?: return@launch
            when {
                currentItem.isTaken   -> toggleDoseUseCase.undoTaken(currentItem.medication, log)
                currentItem.isSkipped -> toggleDoseUseCase.undoSkipped(currentItem.medication, log)
            }
        }
    }

    fun takeAll() {
        _uiState.value.items
            .filter { !it.isTaken && !it.isSkipped }
            .forEach { toggleMedicationStatus(it) }
    }

    /** 仅标记指定时段内的所有待服药品为已服 */
    fun takeAllForPeriod(timePeriodKey: String) {
        _uiState.value.items
            .filter { it.medication.timePeriod == timePeriodKey && !it.isTaken && !it.isSkipped }
            .forEach { toggleMedicationStatus(it) }
    }

    /** 切换主页药品列表的分组方式（时间 ↔ 分类） */
    fun toggleGroupBy() {
        _uiState.update { it.copy(groupByTime = !it.groupByTime) }
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
                        // 数量触发型
                        val threshold = med.refillThreshold
                        if (threshold != null && stock <= threshold) {
                            notificationHelper.showLowStockNotification(
                                medicationId = med.id,
                                medicationName = med.name,
                                stock = stock,
                                unit = med.doseUnit,
                            )
                        }
                        // 时间估算型备货提醒
                        if (med.refillReminderDays > 0) {
                            val dailyConsumption = estimateDailyConsumption(med)
                            if (dailyConsumption > 0) {
                                val daysRemaining = (stock / dailyConsumption).toInt()
                                if (daysRemaining <= med.refillReminderDays) {
                                    notificationHelper.showRefillReminderNotification(
                                        medicationId = med.id,
                                        medicationName = med.name,
                                        daysRemaining = daysRemaining,
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }

    /**
     * 估算每日消耗量（单位与 doseUnit 一致）。
     * - daily: 每天 = doseTimes × doseQuantity
     * - interval: 每 N 天一次 = doseTimes × doseQuantity / N
     * - specific_days: 每周 X 天 = doseTimes × doseQuantity × (X/7)
     */
    private fun estimateDailyConsumption(med: com.example.medlog.data.model.Medication): Double {
        val doseTimesPerDay = med.reminderTimes.split(",").filter { it.isNotBlank() }.size
        val onceAmount = doseTimesPerDay * med.doseQuantity
        return when (med.frequencyType) {
            "interval" -> if (med.frequencyInterval > 0) onceAmount / med.frequencyInterval.toDouble() else onceAmount
            "specific_days" -> {
                val daysPerWeek = med.frequencyDays.split(",").filter { it.isNotBlank() }.size
                onceAmount * daysPerWeek / 7.0
            }
            else -> onceAmount // daily
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
                    val current = StreakCalculator.currentStreak(daysWithTaken, today)
                    val longest = StreakCalculator.longestStreak(daysWithTaken)

                    _uiState.value = _uiState.value.copy(
                        currentStreak = current,
                        longestStreak = longest,
                    )
                }
        }
    }

    // ── QR 导出/导入方法 ──────────────────────────────────────────────────────

    /** 解码扫描到的 QR 内容，若合法则设置导入预览 */
    fun onQrScanned(raw: String) {
        val plan = PlanExportCodec.decode(raw)
        if (plan == null || plan.meds.isEmpty()) {
            _importError.value = "invalid_qr"
            return
        }
        _importPreview.value = plan
    }

    /** 用户选择导入模式后执行实际导入 */
    fun confirmImport(mode: ImportMode) {
        val plan = _importPreview.value ?: return
        val count = plan.meds.size
        viewModelScope.launch {
            importPlanUseCase(plan, mode)
            _importPreview.value = null
            _importSuccess.emit(count)
        }
    }

    /** 取消导入预览（用户点击关闭/取消） */
    fun clearImportPreview() {
        _importPreview.value = null
        _importError.value = null
    }

    /** 生成当前活跃药品的导出 URI；若列表为空返回 null */
    fun generateExportUri(): String? =
        PlanExportCodec.encode(uiState.value.items.map { it.medication })

}

