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
import com.driezy.medlog.di.ComputationDispatcher
import com.driezy.medlog.domain.StreakCalculator
import com.driezy.medlog.domain.todayRange
import com.driezy.medlog.feature.medications.application.DoseChange
import com.driezy.medlog.feature.medications.application.FuturePlanCalculator
import com.driezy.medlog.feature.medications.application.ImportMode
import com.driezy.medlog.feature.medications.application.ImportPlanUseCase
import com.driezy.medlog.feature.medications.application.PlanExport
import com.driezy.medlog.feature.medications.application.PlanExportCodec
import com.driezy.medlog.feature.medications.application.PlanExportDecodeResult
import com.driezy.medlog.feature.medications.application.ToggleMedicationDoseUseCase
import com.driezy.medlog.feature.medications.application.matchDoseLogsToSlots
import com.driezy.medlog.interaction.InteractionRuleEngine
import com.driezy.medlog.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
    val scheduledAtMs: Long? = null,
) {
    val isTaken get() = log?.status == LogStatus.TAKEN
    val isSkipped get() = log?.status == LogStatus.SKIPPED
    val isPartial get() = log?.status == LogStatus.PARTIAL

    /** 今日已有操作（已服、已跳过、部分服用），不再需要服药提醒 */
    val isHandled get() = isTaken || isSkipped || isPartial
}

data class HomeUiState(
    val today: LocalDate = LocalDate.ofEpochDay(0),
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
    val savingDoses: Set<MedicationDoseKey> = emptySet(),
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
        val nowIds = nowTaskItems.map { it.doseKey }.toSet()
        items.filter { item ->
            !item.medication.isPRN && item.doseKey !in nowIds
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
    data object RefreshTime : HomeUiAction
    data class RestoreDose(val change: DoseChange) : HomeUiAction
    data object ToggleGrouping : HomeUiAction
    data class QrScanned(val raw: String) : HomeUiAction
    data class ConfirmImport(val mode: ImportMode) : HomeUiAction
    data object ClearImportPreview : HomeUiAction
}

sealed interface HomeUiEffect {
    data class ImportSucceeded(val count: Int) : HomeUiEffect
    data class DoseSaved(val change: DoseChange) : HomeUiEffect
    data class Failed(val message: String?) : HomeUiEffect
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
    private val planCalculator: FuturePlanCalculator,
    @param:ComputationDispatcher private val computationDispatcher: CoroutineDispatcher,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(today = LocalDate.now(clock)))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val currentTime = MutableStateFlow(clock.instant())
    private val busyDoses = mutableSetOf<MedicationDoseKey>()

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
    }.flowOn(computationDispatcher).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
            HomeUiAction.RefreshTime -> {
                currentTime.value = clock.instant()
                if (_uiState.value.errorMessage != null) observeMedications()
            }
            is HomeUiAction.RestoreDose -> restoreDose(action.change)
            HomeUiAction.ToggleGrouping -> toggleGroupBy()
            is HomeUiAction.QrScanned -> onQrScanned(action.raw)
            is HomeUiAction.ConfirmImport -> confirmImport(action.mode)
            HomeUiAction.ClearImportPreview -> clearImportPreview()
        }
    }

    private var observation: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeMedications() {
        observation?.cancel()
        observation = viewModelScope.launch {
            val datedLogs = combine(prefsRepository.settingsFlow, currentTime) { preferences, now ->
                preferences to now.atZone(preferences.reminderZone(clock.zone)).toLocalDate()
            }.distinctUntilChanged().flatMapLatest { (preferences, today) ->
                val zone = preferences.reminderZone(clock.zone)
                val range = todayRange(Clock.fixed(today.atStartOfDay(zone).toInstant(), zone))
                logRepo.getLogsForDateRange(0L, range.second).map { logs ->
                    HomeDatedLogs(logs, preferences, today, zone)
                }
            }.catch { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                throw e
            }
            val medications = medicationRepo.getAllMedications().catch { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                throw e
            }
            combine(
                medications,
                datedLogs,
                interactionsFlow,
                medicationRepo.observePlanRevisions(),
                currentTime,
            ) { meds, dated, interactions, revisions, now ->
                val logs = dated.logs.filter {
                    Instant.ofEpochMilli(it.scheduledTimeMs).atZone(dated.zone).toLocalDate() ==
                        dated.today
                }
                val prefs = dated.preferences
                val planned = planCalculator.calculate(
                    meds,
                    days = 1,
                    from = dated.today.atStartOfDay(dated.zone).toInstant(),
                    zoneId = dated.zone,
                    revisions = revisions,
                    logs = dated.logs,
                )
                val logsByMedication = logs.groupBy { it.medicationId }
                val items = planned.groupBy { it.medication.id }.flatMap { (id, slots) ->
                    val matched =
                        matchDoseLogsToSlots(
                            slots.map { it.scheduledAt.toEpochMilli() },
                            logsByMedication[id].orEmpty(),
                        )
                    slots.mapIndexed { index, slot ->
                        MedicationWithStatus(
                            medication = slot.medication,
                            log = matched[index],
                            timeSlotIndex = slot.timeSlotIndex,
                            scheduledTime = slot.timeLabel,
                            scheduledAtMs = slot.scheduledAt.toEpochMilli(),
                        )
                    }
                } + meds.filter { it.isPRN && !it.isArchived }.map { med ->
                    MedicationWithStatus(medication = med, log = logsByMedication[med.id]?.lastOrNull())
                }
                HomeObservation(
                    state = HomeUiState(
                        today = dated.today,
                        items = items,
                        isLoading = false,
                        interactions = interactions,
                        autoCollapseCompletedGroups = prefs.autoCollapseCompletedGroups,
                        homeHeroStyle = prefs.homeHeroStyle,
                        currentMinuteOfDay = now.atZone(dated.zone).toLocalTime().toSecondOfDay() / 60,
                        exportUri = PlanExportCodec.encode(meds.filterNot { it.isArchived }, dated.zone),
                    ),
                    showProgressNotification = prefs.persistentReminder,
                )
            }.flowOn(computationDispatcher).catch { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "load_failed") }
            }.collect { observation ->
                val state = observation.state
                // 保留用户的分组偏好，不被新状态覆盖
                val previous = _uiState.value
                _uiState.value = state.copy(
                    groupByTime = previous.groupByTime,
                    currentStreak = previous.currentStreak,
                    importPreview = previous.importPreview,
                    importError = previous.importError,
                    savingDoses = previous.savingDoses,
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
        val target = if (item.isHandled) null else LogStatus.TAKEN
        saveDose(item, target)
    }

    fun skipMedication(item: MedicationWithStatus) = saveDose(item, LogStatus.SKIPPED)

    fun markPartialDose(item: MedicationWithStatus, actualQty: Double) = saveDose(item, LogStatus.PARTIAL, actualQty)

    fun undoDose(doseKey: MedicationDoseKey) {
        _uiState.value.items.find { it.doseKey == doseKey }?.let { saveDose(it, null) }
    }

    private fun saveDose(
        item: MedicationWithStatus,
        status: LogStatus?,
        quantity: Double = item.medication.doseQuantity,
    ) {
        val key = item.doseKey
        if (!busyDoses.add(key)) return
        _uiState.update { it.copy(savingDoses = busyDoses.toSet(), errorMessage = null) }
        viewModelScope.launch {
            try {
                val scheduled = item.scheduledAtMs ?: item.log?.scheduledTimeMs ?: clock.millis()
                val change = toggleDoseUseCase.setStatus(item.medication, scheduled, status, quantity, item.log)
                if (change.before != change.after) effectChannel.send(HomeUiEffect.DoseSaved(change))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                effectChannel.send(HomeUiEffect.Failed(error.localizedMessage))
            } finally {
                busyDoses.remove(key)
                _uiState.update { it.copy(savingDoses = busyDoses.toSet()) }
            }
        }
    }

    private fun restoreDose(change: DoseChange) {
        viewModelScope.launch {
            try {
                toggleDoseUseCase.restore(change)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                effectChannel.send(HomeUiEffect.Failed(error.localizedMessage))
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
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun computeStreak() {
        viewModelScope.launch {
            combine(prefsRepository.settingsFlow, currentTime) { preferences, now ->
                val zone = preferences.reminderZone(clock.zone)
                now.atZone(zone).toLocalDate() to zone
            }.distinctUntilChanged()
                .flatMapLatest { (today, zone) ->
                    logRepo.getLogsForDateRange(0L, today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
                        .map { logs ->
                            val dates = logs.filter { it.status == LogStatus.TAKEN || it.status == LogStatus.PARTIAL }
                                .map { Instant.ofEpochMilli(it.scheduledTimeMs).atZone(zone).toLocalDate() }.toSet()
                            StreakCalculator.currentStreak(dates, today)
                        }
                }.flowOn(computationDispatcher).catch { e -> Log.e("HomeVM", "Failed to compute streak data", e) }
                .collect { streak -> _uiState.update { it.copy(currentStreak = streak) } }
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
