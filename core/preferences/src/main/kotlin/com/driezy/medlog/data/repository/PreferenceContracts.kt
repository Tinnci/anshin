package com.driezy.medlog.data.repository

import com.driezy.medlog.data.model.RoutineSchedule
import com.driezy.medlog.data.model.RoutineTime
import com.driezy.medlog.data.model.RoutineTimeSlot
import kotlinx.coroutines.flow.Flow

data class AppearancePreferenceState(
    val themeMode: ThemeMode,
    val useDynamicColor: Boolean,
    val themePaletteName: String,
    val fontMode: FontMode,
    val appTextScale: AppTextScale,
    val uiDensityScale: UiDensityScale,
    val autoCollapseCompletedGroups: Boolean,
    val homeHeroStyle: HomeHeroStyle,
)

interface AppearancePreferences {
    val appearance: Flow<AppearancePreferenceState>
    suspend fun updateThemeMode(themeMode: ThemeMode)
    suspend fun updateUseDynamicColor(enabled: Boolean)
    suspend fun updateThemePalette(paletteName: String)
    suspend fun updateFontMode(fontMode: FontMode)
    suspend fun updateAppTextScale(scale: AppTextScale)
    suspend fun updateUiDensityScale(scale: UiDensityScale)
    suspend fun updateAutoCollapseCompletedGroups(enabled: Boolean)
    suspend fun updateHomeHeroStyle(style: HomeHeroStyle)
}

data class ReminderPreferenceState(
    val persistentReminder: Boolean,
    val persistentIntervalMinutes: Int,
    val routineSchedule: RoutineSchedule,
    val travelMode: Boolean,
    val homeTimeZoneId: String,
    val earlyReminderMinutes: Int,
    val followUpReminderEnabled: Boolean,
    val followUpDelayMinutes: Int,
    val followUpMaxCount: Int,
)

interface ReminderPreferences {
    val reminders: Flow<ReminderPreferenceState>
    suspend fun updatePersistentReminder(enabled: Boolean)
    suspend fun updatePersistentInterval(minutes: Int)
    suspend fun updateRoutineTime(slot: RoutineTimeSlot, time: RoutineTime)
    suspend fun updateRoutineSchedule(schedule: RoutineSchedule)
    suspend fun updateTravelMode(enabled: Boolean, homeTimeZoneId: String = "")
    suspend fun updateEarlyReminderMinutes(minutes: Int)
    suspend fun updateFollowUpSettings(enabled: Boolean? = null, delayMinutes: Int? = null, maxCount: Int? = null)
}

data class FeaturePreferenceState(
    val enableSymptomDiary: Boolean,
    val enableDrugInteractionCheck: Boolean,
    val enableDrugDatabase: Boolean,
    val enableHealthModule: Boolean,
    val enableTimePeriodMode: Boolean,
)

interface FeaturePreferences {
    val features: Flow<FeaturePreferenceState>
    suspend fun updateFeatureFlags(
        enableSymptomDiary: Boolean? = null,
        enableDrugInteraction: Boolean? = null,
        enableDrugDatabase: Boolean? = null,
        enableHealthModule: Boolean? = null,
        enableTimePeriodMode: Boolean? = null,
    )
}

data class AiPreferenceState(
    val ocrModelType: OcrModelType,
    val cloudAiEnabled: Boolean,
    val cloudAiImageAnalysisEnabled: Boolean,
    val cloudAiHealthInsightsEnabled: Boolean,
    val cloudAiWifiOnly: Boolean,
    val cloudAiProvider: CloudAiProvider,
    val cloudAiModel: String,
    val mimoCloudAiBaseUrl: String,
    val anthropicCloudAiBaseUrl: String,
    val openAiCompatibleBaseUrl: String,
    val openAiCompatibleAuthMode: OpenAiCompatibleCloudAuthMode,
    val openAiCompatibleProviderName: String,
)

interface AiPreferences {
    val ai: Flow<AiPreferenceState>
    suspend fun updateOcrModelType(modelType: OcrModelType)
    suspend fun updateCloudAiSettings(
        enabled: Boolean? = null,
        imageAnalysisEnabled: Boolean? = null,
        healthInsightsEnabled: Boolean? = null,
        wifiOnly: Boolean? = null,
        provider: CloudAiProvider? = null,
        model: String? = null,
        mimoBaseUrl: String? = null,
        anthropicBaseUrl: String? = null,
        openAiCompatibleBaseUrl: String? = null,
        openAiCompatibleAuthMode: OpenAiCompatibleCloudAuthMode? = null,
        openAiCompatibleProviderName: String? = null,
    )
}

data class WidgetPreferenceState(
    val showActions: Boolean,
    val themeMode: WidgetThemeMode,
    val colorSource: WidgetColorSource,
    val paletteName: String,
    val densityScale: WidgetDensityScale,
    val textScale: WidgetTextScale,
)

interface WidgetPreferences {
    val widgets: Flow<WidgetPreferenceState>
    suspend fun updateWidgetShowActions(enabled: Boolean)
    suspend fun updateWidgetAppearance(
        themeMode: WidgetThemeMode? = null,
        colorSource: WidgetColorSource? = null,
        paletteName: String? = null,
        densityScale: WidgetDensityScale? = null,
        textScale: WidgetTextScale? = null,
    )
}
