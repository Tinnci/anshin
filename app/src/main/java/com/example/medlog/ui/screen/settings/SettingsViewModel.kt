package com.example.medlog.ui.screen.settings

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.model.Medication
import com.example.medlog.data.repository.MedicationRepository
import com.example.medlog.data.repository.ThemeMode
import com.example.medlog.data.repository.UserPreferencesRepository
import com.example.medlog.domain.ResyncRemindersUseCase
import com.example.medlog.widget.MedLogWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.TimeZone

data class SettingsUiState(
    val archivedMedications: List<Medication> = emptyList(),
    val persistentReminder: Boolean = false,
    val persistentIntervalMinutes: Int = 5,
    val wakeHour: Int = 7, val wakeMinute: Int = 0,
    val breakfastHour: Int = 8, val breakfastMinute: Int = 0,
    val lunchHour: Int = 12, val lunchMinute: Int = 0,
    val dinnerHour: Int = 18, val dinnerMinute: Int = 0,
    val bedHour: Int = 22, val bedMinute: Int = 0,
    val travelMode: Boolean = false,
    val homeTimeZoneId: String = "",
    // ── 可选功能开关 ───────────────────────────────────────────────────────────
    val enableSymptomDiary: Boolean = true,
    val enableDrugInteractionCheck: Boolean = true,
    val enableDrugDatabase: Boolean = true,
    val enableHealthModule: Boolean = true,
    /** 作息时间段模式：关闭后添加药品时只显示精确时间，隐藏所有作息时间相关 UI */
    val enableTimePeriodMode: Boolean = true,
    // ── 外观 ──────────────────────────────────────────────────────
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    // ── 今日页面 ────────────────────────────────────────────────────────────
    val autoCollapseCompletedGroups: Boolean = true,
    // ── 提前预告提醒 ─────────────────────────────────────────────────────────
    /** 0=关闭 ; 15/30/60=提前对应分钟 */
    val earlyReminderMinutes: Int = 0,
    // ── 小组件显示偏好 ──────────────────────────────────────────────────────────
    /** true = 显示交互服药按钮；false = 仅显示状态指示 */
    val widgetShowActions: Boolean = true,    // ── 漏服再提醒 ──────────────────────────────────────────────────
    val followUpReminderEnabled: Boolean = false,
    val followUpDelayMinutes: Int = 15,
    val followUpMaxCount: Int = 1,)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val prefsRepository: UserPreferencesRepository,
    private val resyncReminders: ResyncRemindersUseCase,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.getArchivedMedications().catch { emit(emptyList()) },
        prefsRepository.settingsFlow,
    ) { archived, prefs ->
        SettingsUiState(
            archivedMedications     = archived,
            persistentReminder      = prefs.persistentReminder,
            persistentIntervalMinutes = prefs.persistentIntervalMinutes,
            wakeHour      = prefs.wakeHour,      wakeMinute      = prefs.wakeMinute,
            breakfastHour = prefs.breakfastHour, breakfastMinute = prefs.breakfastMinute,
            lunchHour     = prefs.lunchHour,     lunchMinute     = prefs.lunchMinute,
            dinnerHour    = prefs.dinnerHour,    dinnerMinute    = prefs.dinnerMinute,
            bedHour       = prefs.bedHour,       bedMinute       = prefs.bedMinute,
            travelMode    = prefs.travelMode,
            homeTimeZoneId = prefs.homeTimeZoneId,
            enableSymptomDiary         = prefs.enableSymptomDiary,
            enableDrugInteractionCheck = prefs.enableDrugInteractionCheck,
            enableDrugDatabase         = prefs.enableDrugDatabase,
            enableHealthModule         = prefs.enableHealthModule,
            enableTimePeriodMode       = prefs.enableTimePeriodMode,
            themeMode       = prefs.themeMode,
            useDynamicColor = prefs.useDynamicColor,
            autoCollapseCompletedGroups = prefs.autoCollapseCompletedGroups,
            earlyReminderMinutes = prefs.earlyReminderMinutes,
            widgetShowActions = prefs.widgetShowActions,
            followUpReminderEnabled = prefs.followUpReminderEnabled,
            followUpDelayMinutes    = prefs.followUpDelayMinutes,
            followUpMaxCount        = prefs.followUpMaxCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setPersistentReminder(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.updatePersistentReminder(enabled) }
    }

    fun setPersistentInterval(minutes: Int) {
        viewModelScope.launch { prefsRepository.updatePersistentInterval(minutes) }
    }

    fun unarchiveMedication(id: Long) {
        viewModelScope.launch { repository.unarchiveMedication(id) }
    }

    fun updateRoutineTime(field: String, hour: Int, minute: Int) {
        viewModelScope.launch {
            prefsRepository.updateRoutineTime(field, hour, minute)
            // 作息时间已更新，重新计算所有药品的提醒时间并重调度闹钟
            val newPrefs = prefsRepository.settingsFlow.first()
            resyncReminders(newPrefs)
        }
    }

    fun setTravelMode(enabled: Boolean) {
        viewModelScope.launch {
            // 开启旅行模式时如果尚未保存家乡时区，自动记录当前设备时区
            val currentTz = java.util.TimeZone.getDefault().id
            val savedTzId = prefsRepository.settingsFlow.first().homeTimeZoneId
            val homeTz = if (enabled && savedTzId.isBlank()) currentTz else savedTzId
            prefsRepository.updateTravelMode(enabled, homeTz)
        }
    }

    fun setEnableSymptomDiary(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.updateFeatureFlags(enableSymptomDiary = enabled) }
    }

    fun setEnableDrugInteractionCheck(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.updateFeatureFlags(enableDrugInteraction = enabled) }
    }

    fun setEnableDrugDatabase(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.updateFeatureFlags(enableDrugDatabase = enabled) }
    }

    fun setEnableHealthModule(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.updateFeatureFlags(enableHealthModule = enabled) }
    }

    fun setEnableTimePeriodMode(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.updateFeatureFlags(enableTimePeriodMode = enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefsRepository.updateThemeMode(mode) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.updateUseDynamicColor(enabled) }
    }

    fun setAutoCollapseCompletedGroups(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.updateAutoCollapseCompletedGroups(enabled) }
    }

    fun setEarlyReminderMinutes(minutes: Int) {
        viewModelScope.launch { prefsRepository.updateEarlyReminderMinutes(minutes) }
    }

    fun setWidgetShowActions(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.updateWidgetShowActions(enabled)
            // SSOT 刷新：设置变更后立即更新所有上屏小组件
            val widget = MedLogWidget()
            val manager = GlanceAppWidgetManager(appContext)
            manager.getGlanceIds(MedLogWidget::class.java).forEach { id ->
                widget.update(appContext, id)
            }
        }
    }

    /** 更新漏服再提醒设置 */
    fun setFollowUpSettings(enabled: Boolean? = null, delayMinutes: Int? = null, maxCount: Int? = null) {
        viewModelScope.launch {
            prefsRepository.updateFollowUpSettings(enabled, delayMinutes, maxCount)
        }
    }

    /** 重置欢迎引导状态，下次启动或手动调用时回到引导页 */
    fun resetWelcome() {
        viewModelScope.launch { prefsRepository.updateHasSeenWelcome(false) }
    }
}
