package com.driezy.medlog.ui.screen.welcome

import androidx.lifecycle.viewModelScope
import com.driezy.medlog.data.model.RoutineSchedule
import com.driezy.medlog.data.model.RoutineTime
import com.driezy.medlog.data.model.RoutineTimeSlot
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.data.repository.UserPreferencesRepository
import com.driezy.medlog.data.repository.routineSchedule
import com.driezy.medlog.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WelcomeUiState(
    // 作息时间（用于第3页快速设置）
    val routineSchedule: RoutineSchedule = RoutineSchedule(),
    // 功能开关（第5页选择）
    val enableSymptomDiary: Boolean = true,
    val enableDrugInteractionCheck: Boolean = true,
    val enableDrugDatabase: Boolean = true,
    val enableHealthModule: Boolean = true,
    /** 作息时间段模式（关闭后添加药品时只显示精确时间） */
    val enableTimePeriodMode: Boolean = true,
    // 外观（第4页选择）
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** true = 已完成引导，外层导航层监听后跳转 Home */
    val isCompleted: Boolean = false,
)

@HiltViewModel
class WelcomeViewModel @Inject constructor(private val prefsRepository: UserPreferencesRepository) : BaseViewModel() {

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    init {
        // 预填用户已有作息设置（重新打开引导时保留之前的值）
        viewModelScope.launch {
            val prefs = prefsRepository.settingsFlow.first()
            _uiState.value = WelcomeUiState(
                routineSchedule = prefs.routineSchedule(),
                enableSymptomDiary = prefs.enableSymptomDiary,
                enableDrugInteractionCheck = prefs.enableDrugInteractionCheck,
                enableDrugDatabase = prefs.enableDrugDatabase,
                enableHealthModule = prefs.enableHealthModule,
                enableTimePeriodMode = prefs.enableTimePeriodMode,
                themeMode = prefs.themeMode,
            )
        }
    }

    fun onTimeChange(slot: RoutineTimeSlot, time: RoutineTime) {
        _uiState.value = _uiState.value.copy(
            routineSchedule = _uiState.value.routineSchedule.withTime(slot, time),
        )
    }

    fun onToggleSymptomDiary(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enableSymptomDiary = enabled)
    }

    fun onToggleDrugInteractionCheck(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enableDrugInteractionCheck = enabled)
    }

    fun onToggleDrugDatabase(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enableDrugDatabase = enabled)
    }

    fun onToggleHealthModule(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enableHealthModule = enabled)
    }

    fun onToggleTimePeriodMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enableTimePeriodMode = enabled)
    }

    fun onThemeModeChange(mode: ThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    /** 保存作息时间 + 功能开关 + 标记引导已完成，触发导航至 Home */
    fun finishWelcome() {
        viewModelScope.launch {
            val s = _uiState.value
            prefsRepository.updateRoutineSchedule(s.routineSchedule)
            prefsRepository.updateFeatureFlags(
                enableSymptomDiary = s.enableSymptomDiary,
                enableDrugInteraction = s.enableDrugInteractionCheck,
                enableDrugDatabase = s.enableDrugDatabase,
                enableHealthModule = s.enableHealthModule,
                enableTimePeriodMode = s.enableTimePeriodMode,
            )
            prefsRepository.updateThemeMode(s.themeMode)
            prefsRepository.updateHasSeenWelcome(true)
            _uiState.value = _uiState.value.copy(isCompleted = true)
        }
    }
}
