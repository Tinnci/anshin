package com.driezy.medlog.feature.settings

import android.net.Uri
import com.driezy.medlog.capability.ai.CloudAiEndpointPreset
import com.driezy.medlog.data.model.RoutineTime
import com.driezy.medlog.data.model.RoutineTimeSlot
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

sealed interface SettingsUiAction {
    data class SetThemeMode(val value: ThemeMode) : SettingsUiAction
    data class SetDynamicColor(val enabled: Boolean) : SettingsUiAction
    data class SetThemePalette(val value: ThemePalette) : SettingsUiAction
    data class SetFontMode(val value: FontMode) : SettingsUiAction
    data class SetAppTextScale(val value: AppTextScale) : SettingsUiAction
    data class SetUiDensityScale(val value: UiDensityScale) : SettingsUiAction
    data class SetAutoCollapse(val enabled: Boolean) : SettingsUiAction
    data class SetHomeHeroStyle(val value: HomeHeroStyle) : SettingsUiAction

    data class SetPersistentReminder(val enabled: Boolean) : SettingsUiAction
    data class SetPersistentInterval(val minutes: Int) : SettingsUiAction
    data class UpdateRoutineTime(val slot: RoutineTimeSlot, val time: RoutineTime) : SettingsUiAction
    data class SetTravelMode(val enabled: Boolean) : SettingsUiAction
    data class SetEarlyReminder(val minutes: Int) : SettingsUiAction
    data class SetFollowUp(val enabled: Boolean? = null, val delayMinutes: Int? = null, val maxCount: Int? = null) :
        SettingsUiAction
    data object PermissionsRecovered : SettingsUiAction

    data class SetSymptomDiary(val enabled: Boolean) : SettingsUiAction
    data class SetDrugInteraction(val enabled: Boolean) : SettingsUiAction
    data class SetDrugDatabase(val enabled: Boolean) : SettingsUiAction
    data class SetHealthModule(val enabled: Boolean) : SettingsUiAction
    data class SetTimePeriodMode(val enabled: Boolean) : SettingsUiAction
    data class UnarchiveMedication(val id: Long) : SettingsUiAction

    data class SetOcrModel(val value: OcrModelType) : SettingsUiAction
    data class SetCloudAi(
        val enabled: Boolean? = null,
        val imageAnalysisEnabled: Boolean? = null,
        val healthInsightsEnabled: Boolean? = null,
        val wifiOnly: Boolean? = null,
        val provider: CloudAiProvider? = null,
        val model: String? = null,
        val mimoBaseUrl: String? = null,
        val anthropicBaseUrl: String? = null,
        val openAiCompatibleBaseUrl: String? = null,
        val openAiCompatibleAuthMode: OpenAiCompatibleCloudAuthMode? = null,
        val openAiCompatibleProviderName: String? = null,
    ) : SettingsUiAction
    data class SaveCloudApiKey(val value: String) : SettingsUiAction
    data class ImportCloudApiKey(val raw: String) : SettingsUiAction
    data object ClearCloudApiKey : SettingsUiAction
    data object RefreshCloudModels : SettingsUiAction
    data class ApplyCloudEndpointPreset(val preset: CloudAiEndpointPreset) : SettingsUiAction

    data class SetWidgetShowActions(val enabled: Boolean) : SettingsUiAction
    data class SetWidgetAppearance(
        val themeMode: WidgetThemeMode? = null,
        val colorSource: WidgetColorSource? = null,
        val palette: ThemePalette? = null,
        val densityScale: WidgetDensityScale? = null,
        val textScale: WidgetTextScale? = null,
    ) : SettingsUiAction

    data object RefreshAiUsage : SettingsUiAction
    data object ResetWelcome : SettingsUiAction
    data class Backup(val uri: Uri) : SettingsUiAction
    data class Restore(val uri: Uri) : SettingsUiAction
}

sealed interface SettingsUiEffect {
    data class Message(val text: String) : SettingsUiEffect
    data object RestartApplication : SettingsUiEffect
}
