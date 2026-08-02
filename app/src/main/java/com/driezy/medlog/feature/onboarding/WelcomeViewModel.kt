package com.driezy.medlog.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.driezy.medlog.data.model.RoutineSchedule
import com.driezy.medlog.data.model.RoutineTime
import com.driezy.medlog.data.model.RoutineTimeSlot
import com.driezy.medlog.data.repository.AppearancePreferences
import com.driezy.medlog.data.repository.FeaturePreferences
import com.driezy.medlog.data.repository.ReminderPreferences
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.feature.onboarding.application.CompleteOnboardingUseCase
import com.driezy.medlog.feature.onboarding.model.OnboardingDraft
import com.driezy.medlog.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WelcomeUiState(
    val pageIndex: Int = 0,
    val routineSchedule: RoutineSchedule = RoutineSchedule(),
    val enableSymptomDiary: Boolean = true,
    val enableDrugInteractionCheck: Boolean = true,
    val enableDrugDatabase: Boolean = true,
    val enableHealthModule: Boolean = true,
    val enableTimePeriodMode: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface WelcomeUiAction {
    data class PageChanged(val index: Int) : WelcomeUiAction
    data class TimeChanged(val slot: RoutineTimeSlot, val time: RoutineTime) : WelcomeUiAction
    data class SymptomDiaryChanged(val enabled: Boolean) : WelcomeUiAction
    data class DrugInteractionChanged(val enabled: Boolean) : WelcomeUiAction
    data class DrugDatabaseChanged(val enabled: Boolean) : WelcomeUiAction
    data class HealthModuleChanged(val enabled: Boolean) : WelcomeUiAction
    data class TimePeriodModeChanged(val enabled: Boolean) : WelcomeUiAction
    data class ThemeModeChanged(val mode: ThemeMode) : WelcomeUiAction
    data object Submit : WelcomeUiAction
}

sealed interface WelcomeUiEffect {
    data object Finished : WelcomeUiEffect
}

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val reminderPreferences: ReminderPreferences,
    private val featurePreferences: FeaturePreferences,
    private val appearancePreferences: AppearancePreferences,
    private val completeOnboarding: CompleteOnboardingUseCase,
) : BaseViewModel() {
    private val _uiState = MutableStateFlow(restoreState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<WelcomeUiEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        if (savedStateHandle.get<Boolean>(KEY_DRAFT_INITIALIZED) != true) {
            viewModelScope.launch {
                val reminders = reminderPreferences.reminders.first()
                val features = featurePreferences.features.first()
                val appearance = appearancePreferences.appearance.first()
                updateState(
                    _uiState.value.copy(
                        routineSchedule = reminders.routineSchedule,
                        enableSymptomDiary = features.enableSymptomDiary,
                        enableDrugInteractionCheck = features.enableDrugInteractionCheck,
                        enableDrugDatabase = features.enableDrugDatabase,
                        enableHealthModule = features.enableHealthModule,
                        enableTimePeriodMode = features.enableTimePeriodMode,
                        themeMode = appearance.themeMode,
                    ),
                )
                savedStateHandle[KEY_DRAFT_INITIALIZED] = true
            }
        }
    }

    fun onAction(action: WelcomeUiAction) {
        when (action) {
            is WelcomeUiAction.PageChanged -> updateState(
                _uiState.value.copy(pageIndex = action.index.coerceAtLeast(0)),
            )
            is WelcomeUiAction.TimeChanged -> updateState(
                _uiState.value.copy(
                    routineSchedule = _uiState.value.routineSchedule.withTime(action.slot, action.time),
                    errorMessage = null,
                ),
            )
            is WelcomeUiAction.SymptomDiaryChanged -> updateState(
                _uiState.value.copy(enableSymptomDiary = action.enabled),
            )
            is WelcomeUiAction.DrugInteractionChanged -> updateState(
                _uiState.value.copy(enableDrugInteractionCheck = action.enabled),
            )
            is WelcomeUiAction.DrugDatabaseChanged -> updateState(
                _uiState.value.copy(enableDrugDatabase = action.enabled),
            )
            is WelcomeUiAction.HealthModuleChanged -> updateState(
                _uiState.value.copy(enableHealthModule = action.enabled),
            )
            is WelcomeUiAction.TimePeriodModeChanged -> updateState(
                _uiState.value.copy(enableTimePeriodMode = action.enabled),
            )
            is WelcomeUiAction.ThemeModeChanged -> updateState(_uiState.value.copy(themeMode = action.mode))
            WelcomeUiAction.Submit -> submit()
        }
    }

    private fun submit() {
        if (_uiState.value.isSaving) return
        updateState(_uiState.value.copy(isSaving = true, errorMessage = null))
        viewModelScope.launch {
            runCatching { completeOnboarding(_uiState.value.toDraft()) }
                .onSuccess {
                    updateState(_uiState.value.copy(isSaving = false))
                    effectChannel.send(WelcomeUiEffect.Finished)
                }
                .onFailure { error ->
                    updateState(
                        _uiState.value.copy(
                            isSaving = false,
                            errorMessage = error.localizedMessage ?: "Unable to save onboarding settings",
                        ),
                    )
                }
        }
    }

    private fun updateState(state: WelcomeUiState) {
        _uiState.value = state
        savedStateHandle[KEY_PAGE] = state.pageIndex
        savedStateHandle[KEY_WAKE_HOUR] = state.routineSchedule.wake.hour
        savedStateHandle[KEY_WAKE_MINUTE] = state.routineSchedule.wake.minute
        savedStateHandle[KEY_BREAKFAST_HOUR] = state.routineSchedule.breakfast.hour
        savedStateHandle[KEY_BREAKFAST_MINUTE] = state.routineSchedule.breakfast.minute
        savedStateHandle[KEY_LUNCH_HOUR] = state.routineSchedule.lunch.hour
        savedStateHandle[KEY_LUNCH_MINUTE] = state.routineSchedule.lunch.minute
        savedStateHandle[KEY_DINNER_HOUR] = state.routineSchedule.dinner.hour
        savedStateHandle[KEY_DINNER_MINUTE] = state.routineSchedule.dinner.minute
        savedStateHandle[KEY_BED_HOUR] = state.routineSchedule.bed.hour
        savedStateHandle[KEY_BED_MINUTE] = state.routineSchedule.bed.minute
        savedStateHandle[KEY_SYMPTOM] = state.enableSymptomDiary
        savedStateHandle[KEY_INTERACTION] = state.enableDrugInteractionCheck
        savedStateHandle[KEY_DATABASE] = state.enableDrugDatabase
        savedStateHandle[KEY_HEALTH] = state.enableHealthModule
        savedStateHandle[KEY_TIME_PERIOD] = state.enableTimePeriodMode
        savedStateHandle[KEY_THEME] = state.themeMode.name
    }

    private fun restoreState(): WelcomeUiState {
        val defaults = RoutineSchedule()
        return WelcomeUiState(
            pageIndex = savedStateHandle[KEY_PAGE] ?: 0,
            routineSchedule = RoutineSchedule(
                wake = restoredTime(KEY_WAKE_HOUR, KEY_WAKE_MINUTE, defaults.wake),
                breakfast = restoredTime(KEY_BREAKFAST_HOUR, KEY_BREAKFAST_MINUTE, defaults.breakfast),
                lunch = restoredTime(KEY_LUNCH_HOUR, KEY_LUNCH_MINUTE, defaults.lunch),
                dinner = restoredTime(KEY_DINNER_HOUR, KEY_DINNER_MINUTE, defaults.dinner),
                bed = restoredTime(KEY_BED_HOUR, KEY_BED_MINUTE, defaults.bed),
            ),
            enableSymptomDiary = savedStateHandle[KEY_SYMPTOM] ?: true,
            enableDrugInteractionCheck = savedStateHandle[KEY_INTERACTION] ?: true,
            enableDrugDatabase = savedStateHandle[KEY_DATABASE] ?: true,
            enableHealthModule = savedStateHandle[KEY_HEALTH] ?: true,
            enableTimePeriodMode = savedStateHandle[KEY_TIME_PERIOD] ?: true,
            themeMode = savedStateHandle.get<String>(KEY_THEME)
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
        )
    }

    private fun restoredTime(hourKey: String, minuteKey: String, default: RoutineTime) = RoutineTime(
        hour = savedStateHandle[hourKey] ?: default.hour,
        minute = savedStateHandle[minuteKey] ?: default.minute,
    )

    private fun WelcomeUiState.toDraft() = OnboardingDraft(
        routineSchedule = routineSchedule,
        enableSymptomDiary = enableSymptomDiary,
        enableDrugInteractionCheck = enableDrugInteractionCheck,
        enableDrugDatabase = enableDrugDatabase,
        enableHealthModule = enableHealthModule,
        enableTimePeriodMode = enableTimePeriodMode,
        themeMode = themeMode,
    )

    private companion object {
        const val KEY_DRAFT_INITIALIZED = "welcome.draftInitialized"
        const val KEY_PAGE = "welcome.page"
        const val KEY_WAKE_HOUR = "welcome.wake.hour"
        const val KEY_WAKE_MINUTE = "welcome.wake.minute"
        const val KEY_BREAKFAST_HOUR = "welcome.breakfast.hour"
        const val KEY_BREAKFAST_MINUTE = "welcome.breakfast.minute"
        const val KEY_LUNCH_HOUR = "welcome.lunch.hour"
        const val KEY_LUNCH_MINUTE = "welcome.lunch.minute"
        const val KEY_DINNER_HOUR = "welcome.dinner.hour"
        const val KEY_DINNER_MINUTE = "welcome.dinner.minute"
        const val KEY_BED_HOUR = "welcome.bed.hour"
        const val KEY_BED_MINUTE = "welcome.bed.minute"
        const val KEY_SYMPTOM = "welcome.feature.symptom"
        const val KEY_INTERACTION = "welcome.feature.interaction"
        const val KEY_DATABASE = "welcome.feature.database"
        const val KEY_HEALTH = "welcome.feature.health"
        const val KEY_TIME_PERIOD = "welcome.feature.timePeriod"
        const val KEY_THEME = "welcome.theme"
    }
}
