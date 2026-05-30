package com.driezy.medlog.ui.screen.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.ViewModel
import com.driezy.medlog.ui.BaseViewModel
import androidx.lifecycle.viewModelScope
import com.driezy.medlog.ai.AiApiKeyStore
import com.driezy.medlog.ai.AiCloudConfigResolver
import com.driezy.medlog.ai.AiProviderConfig
import com.driezy.medlog.ai.CloudAiEndpointPreset
import com.driezy.medlog.ai.CloudAiEndpointPresetLoader
import com.driezy.medlog.ai.CloudAiEndpointProtocol
import com.driezy.medlog.ai.CloudAiDiscoveredModel
import com.driezy.medlog.ai.CloudAiModelDiscoveryClient
import com.driezy.medlog.ai.OpenAiAuthMode
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.repository.AiCacheRepository
import com.driezy.medlog.data.repository.AiUsageSummaryRow
import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.MedicationRepository
import com.driezy.medlog.data.repository.OpenAiCompatibleCloudAuthMode
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.data.repository.OcrModelType
import com.driezy.medlog.data.repository.UserPreferencesRepository
import com.driezy.medlog.domain.BackupRestoreUseCase
import com.driezy.medlog.domain.ResyncRemindersUseCase
import com.driezy.medlog.ui.theme.ThemePalette
import com.driezy.medlog.widget.MedLogWidget
import com.driezy.medlog.widget.NextDoseWidget
import com.driezy.medlog.widget.StreakWidget
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
    val themePalette: ThemePalette = ThemePalette.ANSHIN,
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val prefsRepository: UserPreferencesRepository,
    private val aiApiKeyStore: AiApiKeyStore,
    private val aiCacheRepository: AiCacheRepository,
    private val resyncReminders: ResyncRemindersUseCase,
    private val backupRestore: BackupRestoreUseCase,
    @param:ApplicationContext private val appContext: Context,
) : BaseViewModel() {

    private val aiUsageSummary = MutableStateFlow(emptyList<AiUsageSummaryRow>())
    private val modelDiscoveryClient = CloudAiModelDiscoveryClient()
    private val cloudAiModelDiscovery = MutableStateFlow(CloudAiModelDiscoveryUiState())
    private val endpointPresets = CloudAiEndpointPresetLoader.load(appContext)

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.getArchivedMedications().catch { e -> Log.e("SettingsVM", "Failed to load archived meds", e); emit(emptyList()) },
        prefsRepository.settingsFlow,
        aiApiKeyStore.availableProviders,
        aiUsageSummary,
        cloudAiModelDiscovery,
    ) { archived, prefs, availableProviders, usageSummary, modelDiscovery ->
        val cloudAiCapabilities = AiCloudConfigResolver.resolveCapabilities(prefs)
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
            themePalette    = ThemePalette.fromStoredName(prefs.themePaletteName),
            autoCollapseCompletedGroups = prefs.autoCollapseCompletedGroups,
            earlyReminderMinutes = prefs.earlyReminderMinutes,
            widgetShowActions = prefs.widgetShowActions,
            followUpReminderEnabled = prefs.followUpReminderEnabled,
            followUpDelayMinutes    = prefs.followUpDelayMinutes,
            followUpMaxCount        = prefs.followUpMaxCount,
            ocrModelType            = prefs.ocrModelType,
            cloudAiEnabled = prefs.cloudAiEnabled,
            cloudAiImageAnalysisEnabled = prefs.cloudAiImageAnalysisEnabled,
            cloudAiHealthInsightsEnabled = prefs.cloudAiHealthInsightsEnabled,
            cloudAiWifiOnly = prefs.cloudAiWifiOnly,
            cloudAiProvider = prefs.cloudAiProvider,
            cloudAiModel = prefs.cloudAiModel,
            mimoCloudAiBaseUrl = prefs.mimoCloudAiBaseUrl,
            anthropicCloudAiBaseUrl = prefs.anthropicCloudAiBaseUrl,
            openAiCompatibleBaseUrl = prefs.openAiCompatibleBaseUrl,
            openAiCompatibleAuthMode = prefs.openAiCompatibleAuthMode,
            openAiCompatibleProviderName = prefs.openAiCompatibleProviderName,
            cloudAiAvailableProviders = availableProviders,
            cloudAiProviderHasApiKey = prefs.cloudAiProvider in availableProviders,
            cloudAiSupportsImageInput = cloudAiCapabilities.supportsImageInput,
            cloudAiSupportsText = cloudAiCapabilities.supportsText,
            cloudAiSupportsJsonInstruction = cloudAiCapabilities.supportsJsonInstruction,
            cloudAiModelDiscoveryInProgress = modelDiscovery.inProgress,
            cloudAiModelDiscoveryConnected = modelDiscovery.connected,
            cloudAiModelDiscoveryError = modelDiscovery.error,
            cloudAiDiscoveredModels = modelDiscovery.models,
            cloudAiEndpointPresets = endpointPresets,
            aiUsageSummary = usageSummary,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        refreshAiUsageSummary()
    }

    fun refreshAiUsageSummary() {
        safeLaunch {
            val sevenDaysMillis = 7L * 24 * 60 * 60 * 1000
            aiUsageSummary.value = aiCacheRepository.usageSummary(System.currentTimeMillis() - sevenDaysMillis)
        }
    }

    fun setPersistentReminder(enabled: Boolean) {
        safeLaunch { prefsRepository.updatePersistentReminder(enabled) }
    }

    fun setPersistentInterval(minutes: Int) {
        safeLaunch { prefsRepository.updatePersistentInterval(minutes) }
    }

    fun setOcrModelType(modelType: OcrModelType) {
        safeLaunch { prefsRepository.updateOcrModelType(modelType) }
    }

    fun setCloudAiSettings(
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
    ) {
        safeLaunch {
            prefsRepository.updateCloudAiSettings(
                enabled = enabled,
                imageAnalysisEnabled = imageAnalysisEnabled,
                healthInsightsEnabled = healthInsightsEnabled,
                wifiOnly = wifiOnly,
                provider = provider,
                model = model,
                mimoBaseUrl = mimoBaseUrl,
                anthropicBaseUrl = anthropicBaseUrl,
                openAiCompatibleBaseUrl = openAiCompatibleBaseUrl,
                openAiCompatibleAuthMode = openAiCompatibleAuthMode,
                openAiCompatibleProviderName = openAiCompatibleProviderName,
            )
        }
    }

    fun setCloudAiApiKey(provider: CloudAiProvider, apiKey: String) {
        safeLaunch { aiApiKeyStore.setApiKey(provider, apiKey) }
    }

    fun setCurrentCloudAiApiKey(apiKey: String) {
        setCloudAiApiKey(uiState.value.cloudAiProvider, apiKey)
    }

    fun refreshCloudAiModels() {
        safeLaunch {
            cloudAiModelDiscovery.value = cloudAiModelDiscovery.value.copy(
                inProgress = true,
                connected = null,
                error = null,
            )
            val settings = prefsRepository.settingsFlow.first()
            val apiKey = aiApiKeyStore.getApiKey(settings.cloudAiProvider)
            val config = settings.toDiscoveryConfig(apiKey)
            if (config == null) {
                cloudAiModelDiscovery.value = CloudAiModelDiscoveryUiState(
                    connected = false,
                    error = "API key or OpenAI-compatible Base URL is missing.",
                )
                return@safeLaunch
            }

            val result = modelDiscoveryClient.fetch(config)
            val selected = result.selectBestModel(requireImageInput = true)
                ?: result.selectBestModel(requireImageInput = false)
            if (result.isConnected && selected != null) {
                prefsRepository.updateCloudAiSettings(model = selected.id)
            }
            cloudAiModelDiscovery.value = CloudAiModelDiscoveryUiState(
                connected = result.isConnected,
                error = result.errorMessage,
                models = result.models,
            )
        }
    }

    fun applyCloudAiEndpointPreset(preset: CloudAiEndpointPreset) {
        safeLaunch {
            when (preset.protocol) {
                CloudAiEndpointProtocol.ANTHROPIC -> prefsRepository.updateCloudAiSettings(
                    provider = CloudAiProvider.ANTHROPIC,
                    anthropicBaseUrl = preset.api,
                    openAiCompatibleProviderName = preset.name,
                )
                CloudAiEndpointProtocol.OPENAI_COMPATIBLE -> prefsRepository.updateCloudAiSettings(
                    provider = CloudAiProvider.OPENAI_COMPATIBLE,
                    openAiCompatibleBaseUrl = preset.api,
                    openAiCompatibleProviderName = preset.name,
                    openAiCompatibleAuthMode = OpenAiCompatibleCloudAuthMode.BEARER,
                )
            }
        }
    }

    fun clearCloudAiApiKey(provider: CloudAiProvider) {
        safeLaunch { aiApiKeyStore.clearApiKey(provider) }
    }

    fun clearCurrentCloudAiApiKey() {
        clearCloudAiApiKey(uiState.value.cloudAiProvider)
    }

    fun unarchiveMedication(id: Long) {
        safeLaunch { repository.unarchiveMedication(id) }
    }

    fun updateRoutineTime(field: String, hour: Int, minute: Int) {
        safeLaunch {
            prefsRepository.updateRoutineTime(field, hour, minute)
            // 作息时间已更新，重新计算所有药品的提醒时间并重调度闹钟
            val newPrefs = prefsRepository.settingsFlow.first()
            resyncReminders(newPrefs)
        }
    }

    fun setTravelMode(enabled: Boolean) {
        safeLaunch {
            // 开启旅行模式时如果尚未保存家乡时区，自动记录当前设备时区
            val currentTz = java.util.TimeZone.getDefault().id
            val savedTzId = prefsRepository.settingsFlow.first().homeTimeZoneId
            val homeTz = if (enabled && savedTzId.isBlank()) currentTz else savedTzId
            prefsRepository.updateTravelMode(enabled, homeTz)
        }
    }

    fun setEnableSymptomDiary(enabled: Boolean) {
        safeLaunch { prefsRepository.updateFeatureFlags(enableSymptomDiary = enabled) }
    }

    fun setEnableDrugInteractionCheck(enabled: Boolean) {
        safeLaunch { prefsRepository.updateFeatureFlags(enableDrugInteraction = enabled) }
    }

    fun setEnableDrugDatabase(enabled: Boolean) {
        safeLaunch { prefsRepository.updateFeatureFlags(enableDrugDatabase = enabled) }
    }

    fun setEnableHealthModule(enabled: Boolean) {
        safeLaunch { prefsRepository.updateFeatureFlags(enableHealthModule = enabled) }
    }

    fun setEnableTimePeriodMode(enabled: Boolean) {
        safeLaunch { prefsRepository.updateFeatureFlags(enableTimePeriodMode = enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        safeLaunch { prefsRepository.updateThemeMode(mode) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        safeLaunch { prefsRepository.updateUseDynamicColor(enabled) }
    }

    fun setThemePalette(palette: ThemePalette) {
        safeLaunch {
            prefsRepository.updateThemePalette(palette.name)
            refreshPlacedWidgets()
        }
    }

    fun setAutoCollapseCompletedGroups(enabled: Boolean) {
        safeLaunch { prefsRepository.updateAutoCollapseCompletedGroups(enabled) }
    }

    fun setEarlyReminderMinutes(minutes: Int) {
        safeLaunch { prefsRepository.updateEarlyReminderMinutes(minutes) }
    }

    fun setWidgetShowActions(enabled: Boolean) {
        safeLaunch {
            prefsRepository.updateWidgetShowActions(enabled)
            // SSOT 刷新：设置变更后立即更新所有上屏小组件
            val widget = MedLogWidget()
            val manager = GlanceAppWidgetManager(appContext)
            manager.getGlanceIds(MedLogWidget::class.java).forEach { id ->
                widget.update(appContext, id)
            }
        }
    }

    private suspend fun refreshPlacedWidgets() {
        val manager = GlanceAppWidgetManager(appContext)
        val todayWidget = MedLogWidget()
        manager.getGlanceIds(MedLogWidget::class.java).forEach { id ->
            todayWidget.update(appContext, id)
        }
        val nextDoseWidget = NextDoseWidget()
        manager.getGlanceIds(NextDoseWidget::class.java).forEach { id ->
            nextDoseWidget.update(appContext, id)
        }
        val streakWidget = StreakWidget()
        manager.getGlanceIds(StreakWidget::class.java).forEach { id ->
            streakWidget.update(appContext, id)
        }
    }

    /** 更新漏服再提醒设置 */
    fun setFollowUpSettings(enabled: Boolean? = null, delayMinutes: Int? = null, maxCount: Int? = null) {
        safeLaunch {
            prefsRepository.updateFollowUpSettings(enabled, delayMinutes, maxCount)
        }
    }

    /** 重置欢迎引导状态，下次启动或手动调用时回到引导页 */
    fun resetWelcome() {
        safeLaunch { prefsRepository.updateHasSeenWelcome(false) }
    }

    // ── 备份与恢复 ────────────────────────────────────────────────

    sealed interface BackupEvent {
        data class Success(val message: String) : BackupEvent
        data class Error(val message: String) : BackupEvent
        /** 恢复成功，UI 层应重启进程 */
        data object RestoreSuccess : BackupEvent
    }

    private val _backupEvent = MutableSharedFlow<BackupEvent>()
    val backupEvent: SharedFlow<BackupEvent> = _backupEvent.asSharedFlow()

    private val _backupInProgress = MutableStateFlow(false)
    val backupInProgress: StateFlow<Boolean> = _backupInProgress.asStateFlow()

    fun backup(uri: Uri) {
        safeLaunch {
            _backupInProgress.value = true
            try {
                backupRestore.backup(uri)
                _backupEvent.emit(BackupEvent.Success(appContext.getString(com.driezy.medlog.R.string.settings_backup_success)))
            } catch (e: Exception) {
                Log.e("SettingsVM", "Backup failed", e)
                _backupEvent.emit(BackupEvent.Error(e.localizedMessage ?: "Unknown error"))
            } finally {
                _backupInProgress.value = false
            }
        }
    }

    fun restore(uri: Uri) {
        safeLaunch {
            _backupInProgress.value = true
            try {
                backupRestore.restore(uri)
                _backupEvent.emit(BackupEvent.RestoreSuccess)
                // backupInProgress 保持 true —— UI 层收到 RestoreSuccess 后会重启进程
            } catch (e: Exception) {
                Log.e("SettingsVM", "Restore failed", e)
                val msg = when (e) {
                    is IllegalArgumentException -> appContext.getString(com.driezy.medlog.R.string.settings_backup_invalid_file)
                    else -> e.localizedMessage ?: "Unknown error"
                }
                _backupEvent.emit(BackupEvent.Error(msg))
                _backupInProgress.value = false
            }
        }
    }
}

private data class CloudAiModelDiscoveryUiState(
    val inProgress: Boolean = false,
    val connected: Boolean? = null,
    val error: String? = null,
    val models: List<CloudAiDiscoveredModel> = emptyList(),
)

private fun com.driezy.medlog.data.repository.SettingsPreferences.toDiscoveryConfig(
    apiKey: String?,
): AiProviderConfig? =
    when (cloudAiProvider) {
        CloudAiProvider.MIMO -> apiKey?.let {
            AiProviderConfig.Mimo(
                apiKey = it,
                model = activeCloudAiModel(),
                baseUrl = mimoCloudAiBaseUrl.ifBlank { AiCloudConfigResolver.mimoBaseUrlFor(it) },
            )
        }

        CloudAiProvider.GEMINI -> apiKey?.let {
            AiProviderConfig.Gemini(
                apiKey = it,
                model = activeCloudAiModel(),
            )
        }

        CloudAiProvider.ANTHROPIC -> apiKey?.let {
            AiProviderConfig.Anthropic(
                apiKey = it,
                model = activeCloudAiModel(),
                baseUrl = anthropicCloudAiBaseUrl.ifBlank { "https://api.anthropic.com" },
            )
        }

        CloudAiProvider.OPENAI_COMPATIBLE -> {
            val baseUrl = openAiCompatibleBaseUrl.takeIf { it.isNotBlank() } ?: return null
            AiProviderConfig.OpenAiCompatible(
                baseUrl = baseUrl,
                model = activeCloudAiModel(),
                apiKey = apiKey,
                authMode = when (openAiCompatibleAuthMode) {
                    OpenAiCompatibleCloudAuthMode.API_KEY_HEADER -> OpenAiAuthMode.API_KEY_HEADER
                    OpenAiCompatibleCloudAuthMode.BEARER -> OpenAiAuthMode.BEARER
                },
                providerName = openAiCompatibleProviderName.ifBlank { CloudAiProvider.OPENAI_COMPATIBLE.providerName },
            )
        }
    }
