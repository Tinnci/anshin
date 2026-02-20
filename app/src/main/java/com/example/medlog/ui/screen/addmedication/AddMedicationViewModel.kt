package com.example.medlog.ui.screen.addmedication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.Medication
import com.example.medlog.data.model.TimePeriod
import com.example.medlog.data.repository.MedicationRepository
import com.example.medlog.data.repository.SettingsPreferences
import com.example.medlog.data.repository.UserPreferencesRepository
import com.example.medlog.notification.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/** 剂型选项 */
data class DrugForm(val key: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

data class AddMedicationUiState(
    // ── 基础信息 ──────────────────────────────────────────────────
    val name: String = "",
    val category: String = "",
    val form: String = "tablet",             // tablet/capsule/liquid/powder
    val isHighPriority: Boolean = false,
    val isCustomDrug: Boolean = false,

    // ── 剂量 ──────────────────────────────────────────────────────
    val doseQuantity: Double = 1.0,          // 每次几片/粒/ml
    val doseUnit: String = "片",

    // ── 按需 / PRN ────────────────────────────────────────────────
    val isPRN: Boolean = false,
    val maxDailyDose: String = "",           // 每日最大剂量（字符串，便于输入）

    // ── 服药时段 & 提醒 ──────────────────────────────────────────
    val timePeriod: TimePeriod = TimePeriod.MORNING,
    val reminderTimes: List<String> = listOf("08:00"), // HH:mm 列表

    // ── 频率 ──────────────────────────────────────────────────────
    val frequencyType: String = "daily",     // daily / interval / specific_days
    val frequencyInterval: Int = 1,
    val frequencyDays: String = "1,2,3,4,5,6,7", // 逗号分隔的周天

    // ── 起止日期 ─────────────────────────────────────────────────
    val startDate: Long = todayStartMs(),
    val endDate: Long? = null,

    // ── 库存 ─────────────────────────────────────────────────────
    val stock: String = "",
    val refillThreshold: String = "",

    // ── 其他 ─────────────────────────────────────────────────────
    val notes: String = "",

    // ── UI 状态 ──────────────────────────────────────────────────
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
)

private fun todayStartMs(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

@HiltViewModel
class AddMedicationViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val notificationHelper: NotificationHelper,
    private val prefsRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddMedicationUiState())
    val uiState: StateFlow<AddMedicationUiState> = _uiState.asStateFlow()

    /** 最新作息时间设置缓存，用于运算添加时段自动时间 */
    private val _latestPrefs = MutableStateFlow(SettingsPreferences())

    init {
        viewModelScope.launch {
            prefsRepository.settingsFlow.collect { _latestPrefs.value = it }
        }
    }

    /** 从药品数据库选药后预填名称和分类（仅新增时生效） */
    fun prefillFromDrug(name: String, category: String) {
        if (_uiState.value.name.isEmpty()) {
            _uiState.value = _uiState.value.copy(name = name, category = category)
        }
    }

    /** 加载已有药品进行编辑 */
    fun loadExisting(medicationId: Long) {
        viewModelScope.launch {
            val med = repository.getMedicationById(medicationId) ?: return@launch
            _uiState.value = AddMedicationUiState(
                name            = med.name,
                category        = med.category,
                form            = med.form,
                isHighPriority  = med.isHighPriority,
                isCustomDrug    = med.isCustomDrug,
                doseQuantity    = med.doseQuantity,
                doseUnit        = med.doseUnit,
                isPRN           = med.isPRN,
                maxDailyDose    = med.maxDailyDose?.toString() ?: "",
                timePeriod      = TimePeriod.fromKey(med.timePeriod),
                reminderTimes   = med.reminderTimes.split(",").filter { it.isNotBlank() }
                                      .ifEmpty { listOf("08:00") },
                frequencyType   = med.frequencyType,
                frequencyInterval = med.frequencyInterval,
                frequencyDays   = med.frequencyDays,
                startDate       = med.startDate,
                endDate         = med.endDate,
                stock           = med.stock?.toString() ?: "",
                refillThreshold = med.refillThreshold?.toString() ?: "",
                notes           = med.notes,
            )
        }
    }

    // ── 字段 setters ────────────────────────────────────────────

    fun onNameChange(v: String)              = update { copy(name = v, error = null) }
    fun onCategoryChange(v: String)          = update { copy(category = v) }
    fun onFormChange(v: String)              = update { copy(form = v) }
    fun onHighPriorityChange(v: Boolean)     = update { copy(isHighPriority = v) }
    fun onCustomDrugChange(v: Boolean)       = update { copy(isCustomDrug = v) }

    fun onDoseQuantityChange(v: Double)      = update { copy(doseQuantity = v) }
    fun onDoseUnitChange(v: String)          = update { copy(doseUnit = v) }

    fun onIsPRNChange(v: Boolean)            = update { copy(isPRN = v) }
    fun onMaxDailyDoseChange(v: String)      = update { copy(maxDailyDose = v) }

    fun onTimePeriodChange(v: TimePeriod) {
        val autoTime = timePeriodToReminderTime(v, _latestPrefs.value)
        update {
            copy(
                timePeriod = v,
                // EXACT 保留当前时间，其他时段连带带入根据作息设置计算的预喆时间
                reminderTimes = if (v == TimePeriod.EXACT) reminderTimes else listOf(autoTime),
            )
        }
    }

    /** 根据时段将作息设置转换为 HH:mm 提醒时间 */
    private fun timePeriodToReminderTime(period: TimePeriod, prefs: SettingsPreferences): String =
        when (period) {
            TimePeriod.EXACT            -> _uiState.value.reminderTimes.firstOrNull() ?: "08:00"
            TimePeriod.MORNING          -> "%02d:%02d".format(prefs.wakeHour,      prefs.wakeMinute)
            TimePeriod.BEFORE_BREAKFAST -> adjustTime(prefs.breakfastHour, prefs.breakfastMinute, -15)
            TimePeriod.AFTER_BREAKFAST  -> adjustTime(prefs.breakfastHour, prefs.breakfastMinute, +15)
            TimePeriod.BEFORE_LUNCH     -> adjustTime(prefs.lunchHour,     prefs.lunchMinute,     -15)
            TimePeriod.AFTER_LUNCH      -> adjustTime(prefs.lunchHour,     prefs.lunchMinute,     +15)
            TimePeriod.BEFORE_DINNER    -> adjustTime(prefs.dinnerHour,    prefs.dinnerMinute,    -15)
            TimePeriod.AFTER_DINNER     -> adjustTime(prefs.dinnerHour,    prefs.dinnerMinute,    +15)
            TimePeriod.EVENING          -> adjustTime(prefs.bedHour,       prefs.bedMinute,       -60)
            TimePeriod.BEDTIME          -> "%02d:%02d".format(prefs.bedHour, prefs.bedMinute)
            TimePeriod.AFTERNOON        -> "15:00"   // 下午固定 15:00
        }

    /** 截断钟调进/出，返回 HH:mm */
    private fun adjustTime(hour: Int, minute: Int, deltaMinutes: Int): String {
        val total = hour * 60 + minute + deltaMinutes
        val h = ((total / 60) % 24 + 24) % 24
        val m = ((total % 60)     + 60) % 60
        return "%02d:%02d".format(h, m)
    }

    fun addReminderTime(hhmm: String) {
        val existing = _uiState.value.reminderTimes.toMutableList()
        if (!existing.contains(hhmm)) { existing += hhmm; existing.sort() }
        update { copy(reminderTimes = existing) }
    }
    fun removeReminderTime(hhmm: String)     = update {
        copy(reminderTimes = reminderTimes.filterNot { it == hhmm }.ifEmpty { listOf("08:00") })
    }

    fun onFrequencyTypeChange(v: String)     = update { copy(frequencyType = v) }
    fun onFrequencyIntervalChange(v: Int)    = update { copy(frequencyInterval = v.coerceIn(1, 90)) }
    fun toggleFrequencyDay(day: Int) {
        val current = _uiState.value.frequencyDays.split(",").filter { it.isNotBlank() }.toMutableList()
        val s = day.toString()
        if (current.contains(s)) current.remove(s) else current.add(s)
        val sorted = current.distinct().sortedBy { it.toIntOrNull() ?: 0 }.joinToString(",")
        update { copy(frequencyDays = sorted.ifBlank { "1" }) }
    }

    fun onStartDateChange(v: Long)           = update { copy(startDate = v) }
    fun onEndDateChange(v: Long?)            = update { copy(endDate = v) }

    fun onStockChange(v: String)             = update { copy(stock = v) }
    fun onRefillThresholdChange(v: String)   = update { copy(refillThreshold = v) }
    fun onNotesChange(v: String)             = update { copy(notes = v) }

    // ── 保存 ─────────────────────────────────────────────────────

    fun save(existingId: Long?) {
        val state = _uiState.value
        if (state.name.isBlank()) {
            update { copy(error = "药品名称不能为空") }; return
        }
        viewModelScope.launch {
            update { copy(isSaving = true) }
            // 取第一个提醒时间作为 reminderHour/Minute（向后兼容通知调度）
            val firstTime = state.reminderTimes.firstOrNull() ?: "08:00"
            val (h, m) = firstTime.split(":").let {
                (it.getOrNull(0)?.toIntOrNull() ?: 8) to (it.getOrNull(1)?.toIntOrNull() ?: 0)
            }
            val medication = Medication(
                id              = existingId ?: 0,
                name            = state.name.trim(),
                category        = state.category.trim(),
                form            = state.form,
                isHighPriority  = state.isHighPriority,
                isCustomDrug    = state.isCustomDrug,
                dose            = state.doseQuantity,  // 兼容旧字段
                doseUnit        = state.doseUnit,
                doseQuantity    = state.doseQuantity,
                isPRN           = state.isPRN,
                maxDailyDose    = state.maxDailyDose.toDoubleOrNull(),
                timePeriod      = state.timePeriod.key,
                reminderTimes   = state.reminderTimes.joinToString(","),
                reminderHour    = h,
                reminderMinute  = m,
                frequencyType   = state.frequencyType,
                frequencyInterval = state.frequencyInterval,
                frequencyDays   = state.frequencyDays,
                startDate       = state.startDate,
                endDate         = state.endDate,
                stock           = state.stock.toDoubleOrNull(),
                refillThreshold = state.refillThreshold.toDoubleOrNull(),
                notes           = state.notes,
            )
            if (existingId == null) {
                val newId = repository.addMedication(medication)
                notificationHelper.scheduleAllReminders(medication.copy(id = newId))
            } else {
                repository.updateMedication(medication)
                notificationHelper.cancelAllReminders(existingId)
                notificationHelper.scheduleAllReminders(medication)
            }
            update { copy(isSaving = false, isSaved = true) }
        }
    }

    private inline fun update(block: AddMedicationUiState.() -> AddMedicationUiState) {
        _uiState.value = _uiState.value.block()
    }
}
