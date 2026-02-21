package com.example.medlog.ui.screen.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.repository.ThemeMode
import com.example.medlog.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WelcomeUiState(
    // 作息时间（用于第3页快速设置）
    val wakeHour: Int = 7,      val wakeMinute: Int = 0,
    val breakfastHour: Int = 8, val breakfastMinute: Int = 0,
    val lunchHour: Int = 12,    val lunchMinute: Int = 0,
    val dinnerHour: Int = 18,   val dinnerMinute: Int = 0,
    val bedHour: Int = 22,      val bedMinute: Int = 0,
    // 功能开关（第5页选择）
    val enableSymptomDiary: Boolean = true,
    val enableDrugInteractionCheck: Boolean = true,
    val enableDrugDatabase: Boolean = true,
    val enableHealthModule: Boolean = true,
    // 外观（第4页选择）
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** true = 已完成引导，外层导航层监听后跳转 Home */
    val isCompleted: Boolean = false,
)

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    init {
        // 预填用户已有作息设置（重新打开引导时保留之前的值）
        viewModelScope.launch {
            val prefs = prefsRepository.settingsFlow.first()
            _uiState.value = WelcomeUiState(
                wakeHour      = prefs.wakeHour,      wakeMinute      = prefs.wakeMinute,
                breakfastHour = prefs.breakfastHour, breakfastMinute = prefs.breakfastMinute,
                lunchHour     = prefs.lunchHour,     lunchMinute     = prefs.lunchMinute,
                dinnerHour    = prefs.dinnerHour,    dinnerMinute    = prefs.dinnerMinute,
                bedHour       = prefs.bedHour,       bedMinute       = prefs.bedMinute,
                enableSymptomDiary         = prefs.enableSymptomDiary,
                enableDrugInteractionCheck = prefs.enableDrugInteractionCheck,
                enableDrugDatabase         = prefs.enableDrugDatabase,
                enableHealthModule         = prefs.enableHealthModule,
                themeMode                  = prefs.themeMode,
            )
        }
    }

    fun onTimeChange(field: String, hour: Int, minute: Int) {
        _uiState.value = when (field) {
            "wake"      -> _uiState.value.copy(wakeHour = hour,      wakeMinute = minute)
            "breakfast" -> _uiState.value.copy(breakfastHour = hour, breakfastMinute = minute)
            "lunch"     -> _uiState.value.copy(lunchHour = hour,     lunchMinute = minute)
            "dinner"    -> _uiState.value.copy(dinnerHour = hour,    dinnerMinute = minute)
            "bed"       -> _uiState.value.copy(bedHour = hour,       bedMinute = minute)
            else        -> _uiState.value
        }
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

    fun onThemeModeChange(mode: ThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    /** 保存作息时间 + 功能开关 + 标记引导已完成，触发导航至 Home */
    fun finishWelcome() {
        viewModelScope.launch {
            val s = _uiState.value
            prefsRepository.updateRoutineTime("wake",      s.wakeHour,      s.wakeMinute)
            prefsRepository.updateRoutineTime("breakfast", s.breakfastHour, s.breakfastMinute)
            prefsRepository.updateRoutineTime("lunch",     s.lunchHour,     s.lunchMinute)
            prefsRepository.updateRoutineTime("dinner",    s.dinnerHour,    s.dinnerMinute)
            prefsRepository.updateRoutineTime("bed",       s.bedHour,       s.bedMinute)
            prefsRepository.updateFeatureFlags(
                enableSymptomDiary   = s.enableSymptomDiary,
                enableDrugInteraction = s.enableDrugInteractionCheck,
                enableDrugDatabase   = s.enableDrugDatabase,
                enableHealthModule   = s.enableHealthModule,
            )
            prefsRepository.updateThemeMode(s.themeMode)
            prefsRepository.updateHasSeenWelcome(true)
            _uiState.value = _uiState.value.copy(isCompleted = true)
        }
    }
}
