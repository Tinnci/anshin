package com.driezy.medlog.feature.medications.home

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.driezy.medlog.capability.reminders.NotificationHelper
import com.driezy.medlog.capability.reminders.application.ProgressNotificationUseCase
import com.driezy.medlog.data.model.DrugInteraction
import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.model.MedicationLog
import com.driezy.medlog.data.repository.HomeHeroStyle
import com.driezy.medlog.data.repository.LogRepository
import com.driezy.medlog.data.repository.MedicationRepository
import com.driezy.medlog.data.repository.SettingsPreferences
import com.driezy.medlog.data.repository.UserPreferencesRepository
import com.driezy.medlog.data.repository.reminderZone
import com.driezy.medlog.domain.StreakCalculator
import com.driezy.medlog.domain.todayRange
import com.driezy.medlog.feature.medications.application.ImportMode
import com.driezy.medlog.feature.medications.application.ImportPlanUseCase
import com.driezy.medlog.feature.medications.application.PlanExport
import com.driezy.medlog.feature.medications.application.PlanExportCodec
import com.driezy.medlog.feature.medications.application.PlanExportDecodeResult
import com.driezy.medlog.feature.medications.application.ToggleMedicationDoseUseCase
import com.driezy.medlog.interaction.InteractionRuleEngine
import com.driezy.medlog.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class MedicationWithStatus(
    val medication: Medication,
    val log: MedicationLog? = null,
    /**
     * 对于拥有多个提醒时间的药品，标识当前条目对应的时间槽索引。
     * 单时间槽药品始终为 0。
     */
    val timeSlotIndex: Int = 0,
    /**
     * 本条目对应的计划提醒时间 "HH:mm"。
     * 便于 UI 显示每个时间槽的具体时间。
     */
    val scheduledTime: String = "",
) {
    val isTaken get() = log?.status == LogStatus.TAKEN
    val isSkipped get() = log?.status == LogStatus.SKIPPED
    val isPartial get() = log?.status == LogStatus.PARTIAL

    /** 今日已有操作（已服、已跳过、部分服用），不再需要服药提醒 */
    val isHandled get() = isTaken || isSkipped || isPartial
}

data class HomeUiState(
    val items: List<MedicationWithStatus> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    /** 当前连续服药天数 */
    val currentStreak: Int = 0,
    /** 检测到的药品相互作用列表 */
    val interactions: List<DrugInteraction> = emptyList(),
    /** true = 按服药时段分组；false = 按分类分组 */
    val groupByTime: Boolean = true,
    /** 已全部服用的时段默认折叠 */
    val autoCollapseCompletedGroups: Boolean = true,
    /** 用户选择的首页焦点呈现方式。 */
    val homeHeroStyle: HomeHeroStyle = HomeHeroStyle.ACTION,
    val currentMinuteOfDay: Int = 0,
    val importPreview: PlanExport? = null,
    val importError: String? = null,
    val exportUri: String? = null,
) {
    val heroPresentation: HomeHeroPresentation by lazy {
        HomeHeroPresentation.from(items)
    }

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
            .groupBy { it.medication.category.ifBlank { UNCATEGORIZED_KEY } }
            .entries
            .sortedWith(
                // 中成药相关分类排序靠前，其次按药名首字母
                compareBy(
                    { if (it.key.contains("中成药") || TCM_CATEGORY_KEYWORDS.any { kw -> it.key.contains(kw) }) 0 else 1 },
                    { it.key },
                ),
            )
            .map { it.key to it.value }
    }

    /** PRN 按需药品列表（单独渲染为"随时需要"区域） */
    val prnItems: List<MedicationWithStatus> by lazy {
        items.filter { it.medication.isPRN }
    }

    /** 当前最需要处理的剂量：未完成，且计划时间已经到达或在未来 30 分钟内。 */
    val nowTaskItems: List<MedicationWithStatus> by lazy {
        val cutoffMinutes = currentMinuteOfDay + 30
        items.filter { item ->
            !item.medication.isPRN && !item.isHandled && item.scheduledMinuteOfDay() <= cutoffMinutes
        }
    }

    /** 今日稍后：非 PRN 且不属于当前行动组的全部剂量，包含已完成项作为弱化历史。 */
    val laterTaskItems: List<MedicationWithStatus> by lazy {
        val nowIds = nowTaskItems.map { it.medication.id to it.timeSlotIndex }.toSet()
        items.filter { item ->
            !item.medication.isPRN && (item.medication.id to item.timeSlotIndex) !in nowIds
        }
    }

    companion object {
        /** 哨兵键：无分类药品归入此组，Compose UI 层用 stringResource 解析显示文本 */
        const val UNCATEGORIZED_KEY = "\u0000__uncategorized__"
        private val TCM_CATEGORY_KEYWORDS = listOf(
            "理气", "补益", "清热", "祛湿", "活血", "止咳", "安神", "妇科", "骨伤", "外科",
        )
    }
}

sealed interface HomeUiAction {
    data class ToggleDose(val item: MedicationWithStatus) : HomeUiAction
    data class SkipDose(val item: MedicationWithStatus) : HomeUiAction
    data class MarkPartial(val item: MedicationWithStatus, val quantity: Double) : HomeUiAction
    data class UndoDose(val key: MedicationDoseKey) : HomeUiAction
    data object ToggleGrouping : HomeUiAction
    data class QrScanned(val raw: String) : HomeUiAction
    data class ConfirmImport(val mode: ImportMode) : HomeUiAction
    data object ClearImportPreview : HomeUiAction
}

sealed interface HomeUiEffect {
    data class ImportSucceeded(val count: Int) : HomeUiEffect
}

private data class HomeObservation(val state: HomeUiState, val showProgressNotification: Boolean)
private data class HomeDatedLogs(
    val logs: List<MedicationLog>,
    val preferences: SettingsPreferences,
    val today: LocalDate,
    val zone: ZoneId,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val medicationRepo: MedicationRepository,
    private val logRepo: LogRepository,
    private val notificationHelper: NotificationHelper,
    private val toggleDoseUseCase: ToggleMedicationDoseUseCase,
    private val importPlanUseCase: ImportPlanUseCase,
    private val interactionEngine: InteractionRuleEngine,
    private val prefsRepository: UserPreferencesRepository,
    private val progressNotif: ProgressNotificationUseCase,
    private val clock: Clock,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<HomeUiEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    /**
     * 药品相互作用列表 — 仅在 getActiveMedications 或 enableDrugInteractionCheck 实际变化时重新计算，
     * 避免每次服药日志更新都触发 O(n²) 的 interactionEngine.check()。
     */
    private val interactionsFlow: StateFlow<List<DrugInteraction>> = combine(
        medicationRepo.getActiveMedications().distinctUntilChanged(),
        prefsRepository.settingsFlow.map { it.enableDrugInteractionCheck }.distinctUntilChanged(),
    ) { meds, enableCheck ->
        if (enableCheck) interactionEngine.check(meds) else emptyList()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 上次推送今日进度通知时的 (taken, total)；避免重复更新通知 */
    private var lastProgressNotifState = -1 to -1

    init {
        observeMedications()
        computeStreak()
        scanLowStockOnLaunch()
    }

    fun onAction(action: HomeUiAction) {
        when (action) {
            is HomeUiAction.ToggleDose -> toggleMedicationStatus(action.item)
            is HomeUiAction.SkipDose -> skipMedication(action.item)
            is HomeUiAction.MarkPartial -> markPartialDose(action.item, action.quantity)
            is HomeUiAction.UndoDose -> undoDose(action.key)
            HomeUiAction.ToggleGrouping -> toggleGroupBy()
            is HomeUiAction.QrScanned -> onQrScanned(action.raw)
            is HomeUiAction.ConfirmImport -> confirmImport(action.mode)
            HomeUiAction.ClearImportPreview -> clearImportPreview()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeMedications() {
        viewModelScope.launch {
            val datedLogs = prefsRepository.settingsFlow.flatMapLatest { preferences ->
                val zone = preferences.reminderZone(clock.zone)
                val zonedClock = clock.withZone(zone)
                val today = LocalDate.now(zonedClock)
                val range = todayRange(zonedClock)
                logRepo.getLogsForDateRange(range.first, range.second).map { logs ->
                    HomeDatedLogs(logs, preferences, today, zone)
                }
            }.catch { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                emit(HomeDatedLogs(emptyList(), SettingsPreferences(), LocalDate.now(clock.zone), clock.zone))
            }
            val medications = medicationRepo.getActiveMedications().catch { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                emit(emptyList())
            }
            combine(
                medications,
                datedLogs,
                interactionsFlow,
            ) { meds, dated, interactions ->
                val logs = dated.logs
                val prefs = dated.preferences
                val items = meds.flatMap { med ->
                    val medLogs = logs.filter { it.medicationId == med.id }
                    val times = med.reminderTimes.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    if (med.isPRN) {
                        listOf(
                            MedicationWithStatus(
                                medication = med,
                                log = medLogs.firstOrNull(),
                                timeSlotIndex = 0,
                                scheduledTime = times.firstOrNull() ?: "",
                            ),
                        )
                    } else {
                        val scheduledTimes = times.ifEmpty {
                            listOf("%02d:%02d".format(med.reminderHour, med.reminderMinute))
                        }
                        val slotTimesMs = scheduledTimes.map { timeStr ->
                            val parts = timeStr.split(":").mapNotNull { it.toIntOrNull() }
                            val slotHour = parts.getOrElse(0) { med.reminderHour }.coerceIn(0, 23)
                            val slotMinute = parts.getOrElse(1) { med.reminderMinute }.coerceIn(0, 59)
                            dated.today.atTime(slotHour, slotMinute)
                                .atZone(dated.zone)
                                .toInstant()
                                .toEpochMilli()
                        }
                        val matchedLogs = matchDoseLogsToSlots(slotTimesMs, medLogs)
                        scheduledTimes.mapIndexed { index, timeStr ->
                            MedicationWithStatus(
                                medication = med,
                                log = matchedLogs[index],
                                timeSlotIndex = index,
                                scheduledTime = timeStr,
                            )
                        }
                    }
                }
                HomeObservation(
                    state = HomeUiState(
                        items = items,
                        isLoading = false,
                        interactions = interactions,
                        autoCollapseCompletedGroups = prefs.autoCollapseCompletedGroups,
                        homeHeroStyle = prefs.homeHeroStyle,
                        currentMinuteOfDay = clock.instant().atZone(dated.zone).toLocalTime().toSecondOfDay() / 60,
                        exportUri = PlanExportCodec.encode(items.map { it.medication }, dated.zone),
                    ),
                    showProgressNotification = prefs.persistentReminder,
                )
            }.collect { observation ->
                val state = observation.state
                // 保留用户的分组偏好，不被新状态覆盖
                val previous = _uiState.value
                _uiState.value = state.copy(
                    groupByTime = previous.groupByTime,
                    currentStreak = previous.currentStreak,
                    importPreview = previous.importPreview,
                    importError = previous.importError,
                )
                // 实时更新今日进度通知（去重：仅在 taken/total 真正变化时更新）
                val hero = state.heroPresentation
                val taken = hero.handledCount
                val total = hero.totalCount
                if (!observation.showProgressNotification) {
                    if (lastProgressNotifState != (-1 to -1)) {
                        progressNotif.dismiss()
                        lastProgressNotifState = -1 to -1
                    }
                } else if (taken != lastProgressNotifState.first || total != lastProgressNotifState.second) {
                    lastProgressNotifState = taken to total
                    val pending = state.items
                        .filter { !it.medication.isPRN && !it.isHandled }
                        .map { it.medication.name }
                    progressNotif(
                        taken = taken,
                        total = total,
                        pendingNames = pending,
                    )
                }
            }
        }
    }

    fun toggleMedicationStatus(item: MedicationWithStatus) {
        safeLaunch(onError = { e -> _uiState.update { it.copy(errorMessage = e.message) } }) {
            when {
                item.isTaken -> item.log?.let {
                    toggleDoseUseCase.undoTaken(item.medication, it, item.timeSlotIndex)
                }
                item.isPartial -> item.log?.let {
                    toggleDoseUseCase.undoPartial(item.medication, it, item.timeSlotIndex)
                }
                else -> toggleDoseUseCase.markTaken(item.medication, item.log, item.timeSlotIndex)
            }
        }
    }

    fun skipMedication(item: MedicationWithStatus) {
        safeLaunch(onError = { e -> _uiState.update { it.copy(errorMessage = e.message) } }) {
            toggleDoseUseCase.markSkipped(item.medication, item.log, item.timeSlotIndex)
        }
    }

    /** 标记为部分服用：将实际服用剂量写入日志，并按 actualQty 扣减库存 */
    fun markPartialDose(item: MedicationWithStatus, actualQty: Double) {
        safeLaunch(onError = { e -> _uiState.update { it.copy(errorMessage = e.message) } }) {
            toggleDoseUseCase.markPartial(item.medication, item.log, actualQty, item.timeSlotIndex)
        }
    }

    /** 撤销指定剂量槽的操作，根据当前 state 内的最新记录进行回退。 */
    fun undoDose(doseKey: MedicationDoseKey) {
        safeLaunch(onError = { e -> _uiState.update { it.copy(errorMessage = e.message) } }) {
            val currentItem = _uiState.value.items.find { it.doseKey == doseKey }
                ?: return@safeLaunch
            val log = currentItem.log ?: return@safeLaunch
            when {
                currentItem.isTaken -> toggleDoseUseCase.undoTaken(
                    currentItem.medication,
                    log,
                    currentItem.timeSlotIndex,
                )
                currentItem.isSkipped -> toggleDoseUseCase.undoSkipped(
                    currentItem.medication,
                    log,
                    currentItem.timeSlotIndex,
                )
                currentItem.isPartial -> toggleDoseUseCase.undoPartial(
                    currentItem.medication,
                    log,
                    currentItem.timeSlotIndex,
                )
            }
        }
    }

    /** 切换主页药品列表的分组方式（时间 ↔ 分类） */
    fun toggleGroupBy() {
        _uiState.update { it.copy(groupByTime = !it.groupByTime) }
    }

    /** App 启动时扫描所有活跃药品，补推低库存通知（防止用户忽略了通知） */
    private fun scanLowStockOnLaunch() {
        viewModelScope.launch {
            medicationRepo.getActiveMedications()
                .take(1)
                .catch { e -> Log.e("HomeVM", "Failed to scan low stock medications", e) }
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
    private fun estimateDailyConsumption(med: com.driezy.medlog.data.model.Medication): Double {
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
            val preferences = prefsRepository.settingsFlow.first()
            val zone = preferences.reminderZone(clock.zone)
            val zonedClock = clock.withZone(zone)
            val today = LocalDate.now(zonedClock)
            val now = clock.millis()
            // 取近90天日志，足够覆盖合理 streak
            val startMs = today.minusDays(89).atStartOfDay(zone).toInstant().toEpochMilli()
            logRepo.getLogsForDateRange(startMs, now)
                .take(1)
                .catch { e -> Log.e("HomeVM", "Failed to compute streak data", e) }
                .collect { logs ->
                    // 按日期分组，只关心有 TAKEN 记录的日期
                    val daysWithTaken = logs
                        .filter { it.status == LogStatus.TAKEN }
                        .map {
                            Instant.ofEpochMilli(it.scheduledTimeMs)
                                .atZone(zone).toLocalDate()
                        }
                        .toSet()

                    val current = StreakCalculator.currentStreak(daysWithTaken, today)
                    _uiState.value = _uiState.value.copy(
                        currentStreak = current,
                    )
                }
        }
    }

    // ── QR 导出/导入方法 ──────────────────────────────────────────────────────

    /** 解码扫描到的 QR 内容，若合法则设置导入预览 */
    fun onQrScanned(raw: String) {
        when (val result = PlanExportCodec.decodeWithDiagnostics(raw)) {
            is PlanExportDecodeResult.Success -> {
                if (result.plan.meds.isEmpty()) {
                    Log.w("HomeVM", "QR import failed: empty medication list")
                    _uiState.update { it.copy(importError = "invalid_qr") }
                    return
                }
                _uiState.update { it.copy(importPreview = result.plan, importError = null) }
            }
            is PlanExportDecodeResult.Failure -> {
                Log.w("HomeVM", "QR import failed: ${result.reason}")
                _uiState.update { it.copy(importError = "invalid_qr") }
            }
        }
    }

    /** 用户选择导入模式后执行实际导入 */
    fun confirmImport(mode: ImportMode) {
        val plan = _uiState.value.importPreview ?: return
        val count = plan.meds.size
        safeLaunch(onError = { e -> _uiState.update { it.copy(importError = e.message) } }) {
            importPlanUseCase(plan, mode)
            _uiState.update { it.copy(importPreview = null, importError = null) }
            effectChannel.send(HomeUiEffect.ImportSucceeded(count))
        }
    }

    /** 取消导入预览（用户点击关闭/取消） */
    fun clearImportPreview() {
        _uiState.update { it.copy(importPreview = null, importError = null) }
    }
}
