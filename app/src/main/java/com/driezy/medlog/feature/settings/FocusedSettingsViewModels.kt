package com.driezy.medlog.feature.settings

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.driezy.medlog.capability.ai.AiApiKeyStore
import com.driezy.medlog.capability.reminders.application.ReconcileRemindersUseCase
import com.driezy.medlog.capability.reminders.application.ResyncRemindersUseCase
import com.driezy.medlog.capability.widgets.WidgetRefresher
import com.driezy.medlog.data.repository.AiPreferences
import com.driezy.medlog.data.repository.AppearancePreferences
import com.driezy.medlog.data.repository.FeaturePreferences
import com.driezy.medlog.data.repository.MedicationRepository
import com.driezy.medlog.data.repository.ReminderPreferences
import com.driezy.medlog.data.repository.WidgetPreferences
import com.driezy.medlog.domain.ReminderReconcileReason
import com.driezy.medlog.domain.model.MedicationId
import com.driezy.medlog.ui.BaseViewModel
import com.driezy.medlog.ui.theme.ThemePalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class SettingsAppearanceViewModel @Inject constructor(
    private val preferences: AppearancePreferences,
    private val widgetRefresher: WidgetRefresher,
) : BaseViewModel() {
    val uiState = preferences.appearance.map { prefs ->
        SettingsUiState(
            themeMode = prefs.themeMode,
            useDynamicColor = prefs.useDynamicColor,
            themePalette = ThemePalette.fromStoredName(prefs.themePaletteName),
            fontMode = prefs.fontMode,
            appTextScale = prefs.appTextScale,
            uiDensityScale = prefs.uiDensityScale,
            autoCollapseCompletedGroups = prefs.autoCollapseCompletedGroups,
            homeHeroStyle = prefs.homeHeroStyle,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onAction(action: SettingsUiAction) {
        safeLaunch {
            when (action) {
                is SettingsUiAction.SetThemeMode -> {
                    preferences.updateThemeMode(action.value)
                    refreshWidgets()
                }
                is SettingsUiAction.SetDynamicColor -> {
                    preferences.updateUseDynamicColor(action.enabled)
                    refreshWidgets()
                }
                is SettingsUiAction.SetThemePalette -> {
                    preferences.updateThemePalette(action.value.name)
                    refreshWidgets()
                }
                is SettingsUiAction.SetFontMode -> {
                    preferences.updateFontMode(action.value)
                    refreshWidgets()
                }
                is SettingsUiAction.SetAppTextScale -> preferences.updateAppTextScale(action.value)
                is SettingsUiAction.SetUiDensityScale -> preferences.updateUiDensityScale(action.value)
                is SettingsUiAction.SetAutoCollapse -> preferences.updateAutoCollapseCompletedGroups(action.enabled)
                is SettingsUiAction.SetHomeHeroStyle -> preferences.updateHomeHeroStyle(action.value)
                else -> Unit
            }
        }
    }

    private suspend fun refreshWidgets() = widgetRefresher.refreshAll()
}

@HiltViewModel
class SettingsReminderViewModel @Inject constructor(
    private val preferences: ReminderPreferences,
    private val featurePreferences: FeaturePreferences,
    private val resyncReminders: ResyncRemindersUseCase,
    private val reconcileReminders: ReconcileRemindersUseCase,
    private val clock: Clock,
) : BaseViewModel() {
    val uiState = combine(preferences.reminders, featurePreferences.features) { reminder, feature ->
        SettingsUiState(
            persistentReminder = reminder.persistentReminder,
            persistentIntervalMinutes = reminder.persistentIntervalMinutes,
            routineSchedule = reminder.routineSchedule,
            travelMode = reminder.travelMode,
            homeTimeZoneId = reminder.homeTimeZoneId,
            earlyReminderMinutes = reminder.earlyReminderMinutes,
            followUpReminderEnabled = reminder.followUpReminderEnabled,
            followUpDelayMinutes = reminder.followUpDelayMinutes,
            followUpMaxCount = reminder.followUpMaxCount,
            enableTimePeriodMode = feature.enableTimePeriodMode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onAction(action: SettingsUiAction) {
        safeLaunch {
            when (action) {
                is SettingsUiAction.SetPersistentReminder -> preferences.updatePersistentReminder(action.enabled)
                is SettingsUiAction.SetPersistentInterval -> preferences.updatePersistentInterval(action.minutes)
                is SettingsUiAction.UpdateRoutineTime -> {
                    preferences.updateRoutineTime(action.slot, action.time)
                    resyncReminders(preferences.reminders.first().routineSchedule)
                }
                is SettingsUiAction.SetTravelMode -> {
                    val current = preferences.reminders.first()
                    val homeZone = if (action.enabled && current.homeTimeZoneId.isBlank()) {
                        clock.zone.id
                    } else {
                        current.homeTimeZoneId
                    }
                    preferences.updateTravelMode(action.enabled, homeZone)
                    reconcileReminders.all(ReminderReconcileReason.ROUTINE_CHANGED)
                }
                is SettingsUiAction.SetEarlyReminder -> {
                    preferences.updateEarlyReminderMinutes(action.minutes)
                    reconcileReminders.all(ReminderReconcileReason.ROUTINE_CHANGED)
                }
                is SettingsUiAction.SetFollowUp -> preferences.updateFollowUpSettings(
                    action.enabled,
                    action.delayMinutes,
                    action.maxCount,
                )
                SettingsUiAction.PermissionsRecovered -> {
                    reconcileReminders.all(ReminderReconcileReason.SYSTEM_EVENT)
                }
                is SettingsUiAction.SetTimePeriodMode -> featurePreferences.updateFeatureFlags(
                    enableTimePeriodMode = action.enabled,
                )
                else -> Unit
            }
        }
    }
}

@HiltViewModel
class SettingsModuleViewModel @Inject constructor(
    private val medications: MedicationRepository,
    private val preferences: FeaturePreferences,
    private val reconcileReminders: ReconcileRemindersUseCase,
) : BaseViewModel() {
    val uiState = combine(
        medications.getArchivedMedications().catch { error ->
            Log.e("SettingsModuleVM", "Failed to load archived medications", error)
            emit(emptyList())
        },
        preferences.features,
    ) { archived, feature ->
        SettingsUiState(
            archivedMedications = archived,
            enableSymptomDiary = feature.enableSymptomDiary,
            enableDrugInteractionCheck = feature.enableDrugInteractionCheck,
            enableDrugDatabase = feature.enableDrugDatabase,
            enableHealthModule = feature.enableHealthModule,
            enableTimePeriodMode = feature.enableTimePeriodMode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onAction(action: SettingsUiAction) {
        safeLaunch {
            when (action) {
                is SettingsUiAction.SetSymptomDiary -> preferences.updateFeatureFlags(
                    enableSymptomDiary = action.enabled,
                )
                is SettingsUiAction.SetDrugInteraction -> preferences.updateFeatureFlags(
                    enableDrugInteraction = action.enabled,
                )
                is SettingsUiAction.SetDrugDatabase -> preferences.updateFeatureFlags(
                    enableDrugDatabase = action.enabled,
                )
                is SettingsUiAction.SetHealthModule -> preferences.updateFeatureFlags(
                    enableHealthModule = action.enabled,
                )
                is SettingsUiAction.SetTimePeriodMode -> preferences.updateFeatureFlags(
                    enableTimePeriodMode = action.enabled,
                )
                is SettingsUiAction.UnarchiveMedication -> {
                    medications.unarchiveMedication(action.id)
                    reconcileReminders.medication(
                        MedicationId(action.id),
                        ReminderReconcileReason.MEDICATION_CHANGED,
                    )
                }
                else -> Unit
            }
        }
    }
}

@HiltViewModel
class SettingsIntelligenceViewModel @Inject constructor(private val preferences: AiPreferences) : BaseViewModel() {
    val uiState = preferences.ai.map { ai ->
        SettingsUiState(
            ocrModelType = ai.ocrModelType,
            cloudAiEnabled = ai.cloudAiEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onAction(action: SettingsUiAction) {
        safeLaunch {
            when (action) {
                is SettingsUiAction.SetOcrModel -> preferences.updateOcrModelType(action.value)
                is SettingsUiAction.SetCloudAi -> preferences.updateCloudAiSettings(
                    enabled = action.enabled,
                    imageAnalysisEnabled = action.imageAnalysisEnabled,
                    healthInsightsEnabled = action.healthInsightsEnabled,
                    wifiOnly = action.wifiOnly,
                    provider = action.provider,
                    model = action.model,
                    mimoBaseUrl = action.mimoBaseUrl,
                    anthropicBaseUrl = action.anthropicBaseUrl,
                    openAiCompatibleBaseUrl = action.openAiCompatibleBaseUrl,
                    openAiCompatibleAuthMode = action.openAiCompatibleAuthMode,
                    openAiCompatibleProviderName = action.openAiCompatibleProviderName,
                )
                else -> Unit
            }
        }
    }
}

@HiltViewModel
class SettingsWidgetViewModel @Inject constructor(
    private val preferences: WidgetPreferences,
    appearancePreferences: AppearancePreferences,
    private val widgetRefresher: WidgetRefresher,
) : BaseViewModel() {
    val uiState = combine(preferences.widgets, appearancePreferences.appearance) { widget, appearance ->
        SettingsUiState(
            themeMode = appearance.themeMode,
            useDynamicColor = appearance.useDynamicColor,
            themePalette = ThemePalette.fromStoredName(appearance.themePaletteName),
            widgetShowActions = widget.showActions,
            widgetThemeMode = widget.themeMode,
            widgetColorSource = widget.colorSource,
            widgetPalette = ThemePalette.fromStoredName(widget.paletteName),
            widgetDensityScale = widget.densityScale,
            widgetTextScale = widget.textScale,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onAction(action: SettingsUiAction) {
        safeLaunch {
            when (action) {
                is SettingsUiAction.SetWidgetShowActions -> preferences.updateWidgetShowActions(action.enabled)
                is SettingsUiAction.SetWidgetAppearance -> preferences.updateWidgetAppearance(
                    themeMode = action.themeMode,
                    colorSource = action.colorSource,
                    paletteName = action.palette?.name,
                    densityScale = action.densityScale,
                    textScale = action.textScale,
                )
                else -> Unit
            }
            widgetRefresher.refreshAll()
        }
    }
}

@HiltViewModel
class SettingsHomeViewModel @Inject constructor(
    medications: MedicationRepository,
    features: FeaturePreferences,
    aiPreferences: AiPreferences,
    apiKeyStore: AiApiKeyStore,
) : BaseViewModel() {
    val uiState = combine(
        medications.getArchivedMedications().catch { emit(emptyList()) },
        features.features,
        aiPreferences.ai,
        apiKeyStore.availableProviders,
    ) { archived, feature, ai, availableProviders ->
        SettingsUiState(
            archivedMedications = archived,
            enableSymptomDiary = feature.enableSymptomDiary,
            enableDrugInteractionCheck = feature.enableDrugInteractionCheck,
            enableDrugDatabase = feature.enableDrugDatabase,
            enableHealthModule = feature.enableHealthModule,
            cloudAiEnabled = ai.cloudAiEnabled,
            cloudAiProvider = ai.cloudAiProvider,
            cloudAiProviderHasApiKey = ai.cloudAiProvider in availableProviders,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())
}
