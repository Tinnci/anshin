package com.driezy.medlog.feature.settings

import com.driezy.medlog.capability.ai.CloudAiDiscoveredModel
import com.driezy.medlog.capability.ai.CloudAiEndpointPreset
import com.driezy.medlog.data.model.AiUsageSummaryRow
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.model.RoutineSchedule
import com.driezy.medlog.data.repository.AppTextScale
import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.FontMode
import com.driezy.medlog.data.repository.HomeHeroStyle
import com.driezy.medlog.data.repository.OcrModelType
import com.driezy.medlog.data.repository.OpenAiCompatibleCloudAuthMode
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.data.repository.UiDensityScale
import com.driezy.medlog.data.repository.WidgetColorSource
import com.driezy.medlog.data.repository.WidgetDensityScale
import com.driezy.medlog.data.repository.WidgetTextScale
import com.driezy.medlog.data.repository.WidgetThemeMode
import com.driezy.medlog.ui.theme.ThemePalette

data class SettingsUiState(
    val archivedMedications: List<Medication> = emptyList(),
    val persistentReminder: Boolean = false,
    val persistentIntervalMinutes: Int = 5,
    val routineSchedule: RoutineSchedule = RoutineSchedule(),
    val travelMode: Boolean = false,
    val homeTimeZoneId: String = "",
    val enableSymptomDiary: Boolean = true,
    val enableDrugInteractionCheck: Boolean = true,
    val enableDrugDatabase: Boolean = true,
    val enableHealthModule: Boolean = true,
    val enableTimePeriodMode: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val themePalette: ThemePalette = ThemePalette.ANSHIN,
    val fontMode: FontMode = FontMode.SYSTEM,
    val appTextScale: AppTextScale = AppTextScale.STANDARD,
    val uiDensityScale: UiDensityScale = UiDensityScale.STANDARD,
    val homeHeroStyle: HomeHeroStyle = HomeHeroStyle.ACTION,
    val autoCollapseCompletedGroups: Boolean = true,
    val earlyReminderMinutes: Int = 0,
    val widgetShowActions: Boolean = true,
    val widgetThemeMode: WidgetThemeMode = WidgetThemeMode.SYSTEM,
    val widgetColorSource: WidgetColorSource = WidgetColorSource.SYSTEM_DYNAMIC,
    val widgetPalette: ThemePalette = ThemePalette.ANSHIN,
    val widgetDensityScale: WidgetDensityScale = WidgetDensityScale.STANDARD,
    val widgetTextScale: WidgetTextScale = WidgetTextScale.STANDARD,
    val followUpReminderEnabled: Boolean = false,
    val followUpDelayMinutes: Int = 15,
    val followUpMaxCount: Int = 1,
    val ocrModelType: OcrModelType = OcrModelType.LIGHT_SVTR,
    val cloudAiEnabled: Boolean = false,
    val cloudAiImageAnalysisEnabled: Boolean = false,
    val cloudAiHealthInsightsEnabled: Boolean = false,
    val cloudAiWifiOnly: Boolean = true,
    val cloudAiProvider: CloudAiProvider = CloudAiProvider.MIMO,
    val cloudAiModel: String = CloudAiProvider.MIMO.defaultModel,
    val mimoCloudAiBaseUrl: String = "",
    val anthropicCloudAiBaseUrl: String = "",
    val openAiCompatibleBaseUrl: String = "",
    val openAiCompatibleAuthMode: OpenAiCompatibleCloudAuthMode = OpenAiCompatibleCloudAuthMode.BEARER,
    val openAiCompatibleProviderName: String = "OpenAI-compatible",
    val cloudAiAvailableProviders: Set<CloudAiProvider> = emptySet(),
    val cloudAiProviderHasApiKey: Boolean = false,
    val cloudAiSupportsImageInput: Boolean = true,
    val cloudAiSupportsText: Boolean = true,
    val cloudAiSupportsJsonInstruction: Boolean = true,
    val cloudAiModelDiscoveryInProgress: Boolean = false,
    val cloudAiModelDiscoveryConnected: Boolean? = null,
    val cloudAiModelDiscoveryError: String? = null,
    val cloudAiDiscoveredModels: List<CloudAiDiscoveredModel> = emptyList(),
    val cloudAiEndpointPresets: List<CloudAiEndpointPreset> = emptyList(),
    val aiUsageSummary: List<AiUsageSummaryRow> = emptyList(),
)
