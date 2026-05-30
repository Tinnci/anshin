package com.driezy.medlog.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.driezy.medlog.data.local.settingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 应用主题模式 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 应用字体模式：默认尊重系统字体，可切换为品牌字体。 */
enum class FontMode {
    SYSTEM,
    ANSHIN,
    ;

    companion object {
        fun fromStoredName(name: String?): FontMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

enum class AppTextScale(val factor: Float) {
    SMALL(0.90f),
    STANDARD(1.00f),
    LARGE(1.15f),
    EXTRA_LARGE(1.30f),
    ;

    companion object {
        fun fromStoredName(name: String?): AppTextScale =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

enum class UiDensityScale(val factor: Float) {
    COMPACT(0.94f),
    STANDARD(1.00f),
    COMFORTABLE(1.08f),
    ;

    companion object {
        fun fromStoredName(name: String?): UiDensityScale =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

/** 小组件明暗主题。默认跟随系统/桌面环境，而不是跟随 App 内主题。 */
enum class WidgetThemeMode {
    SYSTEM,
    APP,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromStoredName(name: String?): WidgetThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** 小组件配色来源：设备动态色、App 当前色板，或独立小组件色板。 */
enum class WidgetColorSource {
    SYSTEM_DYNAMIC,
    APP_THEME,
    CUSTOM_PALETTE,
    ;

    companion object {
        fun fromStoredName(name: String?): WidgetColorSource =
            entries.firstOrNull { it.name == name } ?: SYSTEM_DYNAMIC
    }
}

enum class WidgetDensityScale(val factor: Float) {
    COMPACT(0.90f),
    STANDARD(1.00f),
    COMFORTABLE(1.10f),
    ;

    companion object {
        fun fromStoredName(name: String?): WidgetDensityScale =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

enum class WidgetTextScale(val factor: Float) {
    STANDARD(1.00f),
    LARGE(1.10f),
    ;

    companion object {
        fun fromStoredName(name: String?): WidgetTextScale =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

/** 七段数码管 OCR 识别模型类型 */
enum class OcrModelType { LIGHT_SVTR, FASTVIT_T8 }

/** 云端 AI provider。API key 不存放在 DataStore，后续由加密存储提供。 */
enum class CloudAiProvider(val providerName: String, val defaultModel: String) {
    MIMO("MiMo", "mimo-v2.5-pro"),
    GEMINI("Gemini", "gemini-2.5-flash"),
    ANTHROPIC("Anthropic", "claude-sonnet-4-20250514"),
    OPENAI_COMPATIBLE("OpenAI-compatible", "gpt-4.1"),
}

enum class OpenAiCompatibleCloudAuthMode {
    API_KEY_HEADER,
    BEARER,
}

/** DataStore 文件名 */
// 已移至 data/local/SettingsDataStore.kt 统一管理

/** 用户偏好设置数据容器 */
data class SettingsPreferences(
    val persistentReminder: Boolean = false,
    val persistentIntervalMinutes: Int = 5,
    val wakeHour: Int = 7,    val wakeMinute: Int = 0,
    val breakfastHour: Int = 8,  val breakfastMinute: Int = 0,
    val lunchHour: Int = 12,  val lunchMinute: Int = 0,
    val dinnerHour: Int = 18, val dinnerMinute: Int = 0,
    val bedHour: Int = 22,   val bedMinute: Int = 0,
    /** 是否已完成欢迎引导（首次启动标志） */
    val hasSeenWelcome: Boolean = false,
    /**
     * 旅行模式：开启后考虑按「家乡时区」计算提醒时间。
     */
    val travelMode: Boolean = false,
    /** 家乡时区 ID（如 "Asia/Shanghai"）。空串表示使用内容是设备默认时区。 */
    val homeTimeZoneId: String = "",

    // ── 可选功能开关 ───────────────────────────────────────────────────────────
    /** 是否启用身心记录（底部导航显示「日记」Tab） */
    val enableSymptomDiary: Boolean = true,
    /** 是否启用药品相互作用检测（首页横幅 + 实时检测） */
    val enableDrugInteractionCheck: Boolean = true,
    /** 是否启用药品数据库浏览（底部导航显示「药品」Tab） */
    val enableDrugDatabase: Boolean = true,
    /** 是否启用健康体征模块（底部导航显示「健康」Tab） */
    val enableHealthModule: Boolean = true,
    /** 是否启用作息时间段模式（关闭后添加药品时只显示精确时间选择器） */
    val enableTimePeriodMode: Boolean = true,

    // ── 外观偏好 ──────────────────────────────────────────────────────────────
    /** 深色/浅色/跟随系统 */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 是否使用 Material You 动态颜色（Android 12+ 才生效） */
    val useDynamicColor: Boolean = true,
    /** 主题配色方案名称。实际色板定义在 UI theme 层。 */
    val themePaletteName: String = "ANSHIN",
    /** 字体模式。默认 SYSTEM，尊重系统字体设置。 */
    val fontMode: FontMode = FontMode.SYSTEM,
    /** 应用内文字大小缩放，叠加在系统字体大小之上。 */
    val appTextScale: AppTextScale = AppTextScale.STANDARD,
    /** 应用内元素密度缩放，影响 dp 尺寸。 */
    val uiDensityScale: UiDensityScale = UiDensityScale.STANDARD,

    // ── 今日页面显示偏好 ───────────────────────────────────────────────────────
    /** 已全部服用的时段默认折叠，节省屏幕空间 */
    val autoCollapseCompletedGroups: Boolean = true,
    // ── 提醒弹性设置 ───────────────────────────────────────────
    /**
     * 提前 N 分钟发送预告提醒。
     * 0 = 关闭（不发预告）；15 / 30 / 60 = 提前对应分钟数发送
     */
    val earlyReminderMinutes: Int = 0,
    // ── 小组件显示偏好 ──────────────────────────────────────────────────────────
    /**
     * 小组件节点是否显示可交互服药被按按钮（true）还是仅显示待服状态指示（false）。
     * true = 操作模式（默认）；false = 状态模式
     */
    val widgetShowActions: Boolean = true,
    /** 小组件明暗主题，独立于主程序主题。 */
    val widgetThemeMode: WidgetThemeMode = WidgetThemeMode.SYSTEM,
    /** 小组件配色来源。 */
    val widgetColorSource: WidgetColorSource = WidgetColorSource.SYSTEM_DYNAMIC,
    /** 小组件独立色板名称，仅在 [WidgetColorSource.CUSTOM_PALETTE] 下生效。 */
    val widgetPaletteName: String = "ANSHIN",
    /** 小组件布局密度，影响 padding/间距/图标和按钮尺寸。 */
    val widgetDensityScale: WidgetDensityScale = WidgetDensityScale.STANDARD,
    /** 小组件文字大小。 */
    val widgetTextScale: WidgetTextScale = WidgetTextScale.STANDARD,
    // ── 漏服再提醒 ──────────────────────────────────────────────────────────────
    val followUpReminderEnabled: Boolean = false,
    val followUpDelayMinutes: Int = 15,
    val followUpMaxCount: Int = 1,

    // ── 健康模块 ──────────────────────────────────────────────────────────────
    /** 用户身高（cm），用于 BMI 计算；0 表示未设置 */
    val userHeightCm: Float = 0f,

    // ── OCR 模型选择 ────────────────────────────────────────────────────────────
    val ocrModelType: OcrModelType = OcrModelType.LIGHT_SVTR,

    // ── 云端 AI 设置 ───────────────────────────────────────────────────────────
    /** 总开关，默认关闭；每个功能还需要单独 opt-in。 */
    val cloudAiEnabled: Boolean = false,
    val cloudAiImageAnalysisEnabled: Boolean = false,
    val cloudAiHealthInsightsEnabled: Boolean = false,
    /** 默认仅 Wi-Fi 上传图片/上下文到云端。 */
    val cloudAiWifiOnly: Boolean = true,
    val cloudAiProvider: CloudAiProvider = CloudAiProvider.MIMO,
    val cloudAiModel: String = CloudAiProvider.MIMO.defaultModel,
    val mimoCloudAiModel: String = CloudAiProvider.MIMO.defaultModel,
    val mimoCloudAiBaseUrl: String = "",
    val geminiCloudAiModel: String = CloudAiProvider.GEMINI.defaultModel,
    val anthropicCloudAiModel: String = CloudAiProvider.ANTHROPIC.defaultModel,
    val anthropicCloudAiBaseUrl: String = "",
    val openAiCompatibleCloudAiModel: String = CloudAiProvider.OPENAI_COMPATIBLE.defaultModel,
    val openAiCompatibleBaseUrl: String = "",
    val openAiCompatibleAuthMode: OpenAiCompatibleCloudAuthMode = OpenAiCompatibleCloudAuthMode.BEARER,
    val openAiCompatibleProviderName: String = "OpenAI-compatible",
) {
    fun cloudAiModelFor(provider: CloudAiProvider): String =
        when (provider) {
            CloudAiProvider.MIMO -> mimoCloudAiModel.ifBlank { provider.defaultModel }
            CloudAiProvider.GEMINI -> geminiCloudAiModel.ifBlank { provider.defaultModel }
            CloudAiProvider.ANTHROPIC -> anthropicCloudAiModel.ifBlank { provider.defaultModel }
            CloudAiProvider.OPENAI_COMPATIBLE -> openAiCompatibleCloudAiModel.ifBlank { provider.defaultModel }
        }

    fun activeCloudAiModel(): String {
        val providerModel = cloudAiModelFor(cloudAiProvider)
        val providerHasExplicitModel = providerModel != cloudAiProvider.defaultModel
        val looksLikeLegacyMimoDefault =
            cloudAiProvider != CloudAiProvider.MIMO && cloudAiModel == CloudAiProvider.MIMO.defaultModel
        val selectedModel = when {
            cloudAiModel.isBlank() -> providerModel
            looksLikeLegacyMimoDefault -> providerModel
            providerHasExplicitModel -> providerModel
            else -> cloudAiModel
        }
        return selectedModel.ifBlank { cloudAiProvider.defaultModel }
    }
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val dataStore: DataStore<Preferences> = context.settingsDataStore

    companion object Keys {
        val PERSISTENT_REMINDER          = booleanPreferencesKey("persistent_reminder")
        val PERSISTENT_INTERVAL_MINUTES  = intPreferencesKey("persistent_interval_minutes")
        val WAKE_HOUR      = intPreferencesKey("wake_hour")
        val WAKE_MINUTE    = intPreferencesKey("wake_minute")
        val BREAKFAST_HOUR = intPreferencesKey("breakfast_hour")
        val BREAKFAST_MIN  = intPreferencesKey("breakfast_minute")
        val LUNCH_HOUR     = intPreferencesKey("lunch_hour")
        val LUNCH_MIN      = intPreferencesKey("lunch_minute")
        val DINNER_HOUR    = intPreferencesKey("dinner_hour")
        val DINNER_MIN     = intPreferencesKey("dinner_minute")
        val BED_HOUR       = intPreferencesKey("bed_hour")
        val BED_MIN        = intPreferencesKey("bed_minute")
        val HAS_SEEN_WELCOME = booleanPreferencesKey("has_seen_welcome")
        val TRAVEL_MODE       = booleanPreferencesKey("travel_mode")
        val HOME_TIMEZONE_ID  = stringPreferencesKey("home_timezone_id")
        // 可选功能开关
        val ENABLE_SYMPTOM_DIARY         = booleanPreferencesKey("enable_symptom_diary")
        val ENABLE_DRUG_INTERACTION      = booleanPreferencesKey("enable_drug_interaction")
        val ENABLE_DRUG_DATABASE         = booleanPreferencesKey("enable_drug_database")
        val ENABLE_HEALTH_MODULE         = booleanPreferencesKey("enable_health_module")
        val ENABLE_TIME_PERIOD_MODE      = booleanPreferencesKey("enable_time_period_mode")
        // 外观
        val THEME_MODE         = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR  = booleanPreferencesKey("use_dynamic_color")
        val THEME_PALETTE      = stringPreferencesKey("theme_palette")
        val FONT_MODE          = stringPreferencesKey("font_mode")
        val APP_TEXT_SCALE     = stringPreferencesKey("app_text_scale")
        val UI_DENSITY_SCALE   = stringPreferencesKey("ui_density_scale")
        // 今日页面显示偏好
        val AUTO_COLLAPSE_DONE = booleanPreferencesKey("auto_collapse_completed_groups")
        // 提前预告提醒
        val EARLY_REMINDER_MINUTES = intPreferencesKey("early_reminder_minutes")
        // 小组件显示偏好
        val WIDGET_SHOW_ACTIONS = booleanPreferencesKey("widget_show_actions")
        val WIDGET_THEME_MODE = stringPreferencesKey("widget_theme_mode")
        val WIDGET_COLOR_SOURCE = stringPreferencesKey("widget_color_source")
        val WIDGET_PALETTE = stringPreferencesKey("widget_palette")
        val WIDGET_DENSITY_SCALE = stringPreferencesKey("widget_density_scale")
        val WIDGET_TEXT_SCALE = stringPreferencesKey("widget_text_scale")
        // 漏服再提醒
        val FOLLOW_UP_ENABLED       = booleanPreferencesKey("follow_up_reminder_enabled")
        val FOLLOW_UP_DELAY_MINUTES = intPreferencesKey("follow_up_delay_minutes")
        val FOLLOW_UP_MAX_COUNT     = intPreferencesKey("follow_up_max_count")
        // 健康模块
        val USER_HEIGHT_CM = floatPreferencesKey("user_height_cm")
        // OCR 识别设置
        val OCR_MODEL_TYPE = stringPreferencesKey("ocr_model_type")
        // 云端 AI 设置
        val CLOUD_AI_ENABLED = booleanPreferencesKey("cloud_ai_enabled")
        val CLOUD_AI_IMAGE_ANALYSIS_ENABLED = booleanPreferencesKey("cloud_ai_image_analysis_enabled")
        val CLOUD_AI_HEALTH_INSIGHTS_ENABLED = booleanPreferencesKey("cloud_ai_health_insights_enabled")
        val CLOUD_AI_WIFI_ONLY = booleanPreferencesKey("cloud_ai_wifi_only")
        val CLOUD_AI_PROVIDER = stringPreferencesKey("cloud_ai_provider")
        val CLOUD_AI_MODEL = stringPreferencesKey("cloud_ai_model")
        val CLOUD_AI_MIMO_MODEL = stringPreferencesKey("cloud_ai_mimo_model")
        val CLOUD_AI_MIMO_BASE_URL = stringPreferencesKey("cloud_ai_mimo_base_url")
        val CLOUD_AI_GEMINI_MODEL = stringPreferencesKey("cloud_ai_gemini_model")
        val CLOUD_AI_ANTHROPIC_MODEL = stringPreferencesKey("cloud_ai_anthropic_model")
        val CLOUD_AI_ANTHROPIC_BASE_URL = stringPreferencesKey("cloud_ai_anthropic_base_url")
        val CLOUD_AI_OPENAI_COMPATIBLE_MODEL = stringPreferencesKey("cloud_ai_openai_compatible_model")
        val OPENAI_COMPATIBLE_BASE_URL = stringPreferencesKey("openai_compatible_base_url")
        val OPENAI_COMPATIBLE_AUTH_MODE = stringPreferencesKey("openai_compatible_auth_mode")
        val OPENAI_COMPATIBLE_PROVIDER_NAME = stringPreferencesKey("openai_compatible_provider_name")

        private fun cloudAiModelKey(provider: CloudAiProvider): Preferences.Key<String> =
            when (provider) {
                CloudAiProvider.MIMO -> CLOUD_AI_MIMO_MODEL
                CloudAiProvider.GEMINI -> CLOUD_AI_GEMINI_MODEL
                CloudAiProvider.ANTHROPIC -> CLOUD_AI_ANTHROPIC_MODEL
                CloudAiProvider.OPENAI_COMPATIBLE -> CLOUD_AI_OPENAI_COMPATIBLE_MODEL
            }
    }

    /** 持续输出最新设置（Flow，app 生命周期内可观察） */
    val settingsFlow: Flow<SettingsPreferences> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            val cloudAiProvider = prefs[CLOUD_AI_PROVIDER]?.let {
                runCatching { CloudAiProvider.valueOf(it) }.getOrNull()
            } ?: CloudAiProvider.MIMO
            val legacyCloudAiModel = prefs[CLOUD_AI_MODEL]
            fun storedModel(provider: CloudAiProvider): String {
                val legacyForSelectedProvider = if (cloudAiProvider == provider) legacyCloudAiModel else null
                return prefs[cloudAiModelKey(provider)] ?: legacyForSelectedProvider ?: provider.defaultModel
            }
            val mimoCloudAiModel = storedModel(CloudAiProvider.MIMO)
            val geminiCloudAiModel = storedModel(CloudAiProvider.GEMINI)
            val anthropicCloudAiModel = storedModel(CloudAiProvider.ANTHROPIC)
            val openAiCompatibleCloudAiModel = storedModel(CloudAiProvider.OPENAI_COMPATIBLE)

            SettingsPreferences(
                persistentReminder         = prefs[PERSISTENT_REMINDER] ?: false,
                persistentIntervalMinutes  = prefs[PERSISTENT_INTERVAL_MINUTES] ?: 5,
                wakeHour      = prefs[WAKE_HOUR]      ?: 7,  wakeMinute    = prefs[WAKE_MINUTE]    ?: 0,
                breakfastHour = prefs[BREAKFAST_HOUR] ?: 8,  breakfastMinute = prefs[BREAKFAST_MIN] ?: 0,
                lunchHour     = prefs[LUNCH_HOUR]     ?: 12, lunchMinute   = prefs[LUNCH_MIN]      ?: 0,
                dinnerHour    = prefs[DINNER_HOUR]    ?: 18, dinnerMinute  = prefs[DINNER_MIN]     ?: 0,
                bedHour       = prefs[BED_HOUR]       ?: 22, bedMinute     = prefs[BED_MIN]        ?: 0,
                hasSeenWelcome = prefs[HAS_SEEN_WELCOME] ?: false,
                travelMode    = prefs[TRAVEL_MODE]      ?: false,
                homeTimeZoneId = prefs[HOME_TIMEZONE_ID]  ?: "",
                enableSymptomDiary        = prefs[ENABLE_SYMPTOM_DIARY]    ?: true,
                enableDrugInteractionCheck = prefs[ENABLE_DRUG_INTERACTION] ?: true,
                enableDrugDatabase        = prefs[ENABLE_DRUG_DATABASE]     ?: true,
                enableHealthModule        = prefs[ENABLE_HEALTH_MODULE]     ?: true,
                enableTimePeriodMode     = prefs[ENABLE_TIME_PERIOD_MODE]  ?: true,
                themeMode       = prefs[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                                    ?: ThemeMode.SYSTEM,
                useDynamicColor = prefs[USE_DYNAMIC_COLOR] ?: true,
                themePaletteName = prefs[THEME_PALETTE] ?: "ANSHIN",
                fontMode = FontMode.fromStoredName(prefs[FONT_MODE]),
                appTextScale = AppTextScale.fromStoredName(prefs[APP_TEXT_SCALE]),
                uiDensityScale = UiDensityScale.fromStoredName(prefs[UI_DENSITY_SCALE]),
                autoCollapseCompletedGroups = prefs[AUTO_COLLAPSE_DONE] ?: true,
                earlyReminderMinutes = prefs[EARLY_REMINDER_MINUTES] ?: 0,
                widgetShowActions = prefs[WIDGET_SHOW_ACTIONS] ?: true,
                widgetThemeMode = WidgetThemeMode.fromStoredName(prefs[WIDGET_THEME_MODE]),
                widgetColorSource = WidgetColorSource.fromStoredName(prefs[WIDGET_COLOR_SOURCE]),
                widgetPaletteName = prefs[WIDGET_PALETTE] ?: "ANSHIN",
                widgetDensityScale = WidgetDensityScale.fromStoredName(prefs[WIDGET_DENSITY_SCALE]),
                widgetTextScale = WidgetTextScale.fromStoredName(prefs[WIDGET_TEXT_SCALE]),
                followUpReminderEnabled = prefs[FOLLOW_UP_ENABLED] ?: false,
                followUpDelayMinutes    = prefs[FOLLOW_UP_DELAY_MINUTES] ?: 15,
                followUpMaxCount        = prefs[FOLLOW_UP_MAX_COUNT] ?: 1,
                userHeightCm            = prefs[USER_HEIGHT_CM] ?: 0f,
                ocrModelType            = prefs[OCR_MODEL_TYPE]?.let { runCatching { OcrModelType.valueOf(it) }.getOrNull() }
                                            ?: OcrModelType.LIGHT_SVTR,
                cloudAiEnabled = prefs[CLOUD_AI_ENABLED] ?: false,
                cloudAiImageAnalysisEnabled = prefs[CLOUD_AI_IMAGE_ANALYSIS_ENABLED] ?: false,
                cloudAiHealthInsightsEnabled = prefs[CLOUD_AI_HEALTH_INSIGHTS_ENABLED] ?: false,
                cloudAiWifiOnly = prefs[CLOUD_AI_WIFI_ONLY] ?: true,
                cloudAiProvider = cloudAiProvider,
                cloudAiModel = when (cloudAiProvider) {
                    CloudAiProvider.MIMO -> mimoCloudAiModel
                    CloudAiProvider.GEMINI -> geminiCloudAiModel
                    CloudAiProvider.ANTHROPIC -> anthropicCloudAiModel
                    CloudAiProvider.OPENAI_COMPATIBLE -> openAiCompatibleCloudAiModel
                },
                mimoCloudAiModel = mimoCloudAiModel,
                mimoCloudAiBaseUrl = prefs[CLOUD_AI_MIMO_BASE_URL] ?: "",
                geminiCloudAiModel = geminiCloudAiModel,
                anthropicCloudAiModel = anthropicCloudAiModel,
                anthropicCloudAiBaseUrl = prefs[CLOUD_AI_ANTHROPIC_BASE_URL] ?: "",
                openAiCompatibleCloudAiModel = openAiCompatibleCloudAiModel,
                openAiCompatibleBaseUrl = prefs[OPENAI_COMPATIBLE_BASE_URL] ?: "",
                openAiCompatibleAuthMode = prefs[OPENAI_COMPATIBLE_AUTH_MODE]?.let {
                    runCatching { OpenAiCompatibleCloudAuthMode.valueOf(it) }.getOrNull()
                } ?: OpenAiCompatibleCloudAuthMode.BEARER,
                openAiCompatibleProviderName = prefs[OPENAI_COMPATIBLE_PROVIDER_NAME] ?: "OpenAI-compatible",
            )
        }

    suspend fun updatePersistentReminder(enabled: Boolean) {
        dataStore.edit { it[PERSISTENT_REMINDER] = enabled }
    }

    suspend fun updatePersistentInterval(minutes: Int) {
        dataStore.edit { it[PERSISTENT_INTERVAL_MINUTES] = minutes }
    }

    suspend fun updateRoutineTime(field: String, hour: Int, minute: Int) {
        dataStore.edit { prefs ->
            when (field) {
                "wake"      -> { prefs[WAKE_HOUR] = hour;      prefs[WAKE_MINUTE]    = minute }
                "breakfast" -> { prefs[BREAKFAST_HOUR] = hour; prefs[BREAKFAST_MIN]  = minute }
                "lunch"     -> { prefs[LUNCH_HOUR] = hour;     prefs[LUNCH_MIN]      = minute }
                "dinner"    -> { prefs[DINNER_HOUR] = hour;    prefs[DINNER_MIN]     = minute }
                "bed"       -> { prefs[BED_HOUR] = hour;       prefs[BED_MIN]        = minute }
            }
        }
    }

    suspend fun updateHasSeenWelcome(seen: Boolean) {
        dataStore.edit { it[HAS_SEEN_WELCOME] = seen }
    }

    suspend fun updateTravelMode(enabled: Boolean, homeTimeZoneId: String = "") {
        dataStore.edit {
            it[TRAVEL_MODE] = enabled
            if (homeTimeZoneId.isNotBlank()) it[HOME_TIMEZONE_ID] = homeTimeZoneId
        }
    }

    /**
     * 更新可选功能开关（null = 保持原值不变）。
     */
    suspend fun updateFeatureFlags(
        enableSymptomDiary: Boolean? = null,
        enableDrugInteraction: Boolean? = null,
        enableDrugDatabase: Boolean? = null,
        enableHealthModule: Boolean? = null,
        enableTimePeriodMode: Boolean? = null,
    ) {
        dataStore.edit { prefs ->
            if (enableSymptomDiary != null) prefs[ENABLE_SYMPTOM_DIARY] = enableSymptomDiary
            if (enableDrugInteraction != null) prefs[ENABLE_DRUG_INTERACTION] = enableDrugInteraction
            if (enableDrugDatabase != null) prefs[ENABLE_DRUG_DATABASE] = enableDrugDatabase
            if (enableHealthModule != null) prefs[ENABLE_HEALTH_MODULE] = enableHealthModule
            if (enableTimePeriodMode != null) prefs[ENABLE_TIME_PERIOD_MODE] = enableTimePeriodMode
        }
    }

    /** 更新外观主题模式 */
    suspend fun updateThemeMode(themeMode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = themeMode.name }
    }

    /** 更新动态颜色（Material You）开关 */
    suspend fun updateUseDynamicColor(enabled: Boolean) {
        dataStore.edit { it[USE_DYNAMIC_COLOR] = enabled }
    }

    /** 更新主题配色方案 */
    suspend fun updateThemePalette(paletteName: String) {
        dataStore.edit { it[THEME_PALETTE] = paletteName }
    }

    suspend fun updateFontMode(fontMode: FontMode) {
        dataStore.edit { it[FONT_MODE] = fontMode.name }
    }

    suspend fun updateAppTextScale(scale: AppTextScale) {
        dataStore.edit { it[APP_TEXT_SCALE] = scale.name }
    }

    suspend fun updateUiDensityScale(scale: UiDensityScale) {
        dataStore.edit { it[UI_DENSITY_SCALE] = scale.name }
    }

    /** 更新「已完成分组默认折叠」开关 */
    suspend fun updateAutoCollapseCompletedGroups(enabled: Boolean) {
        dataStore.edit { it[AUTO_COLLAPSE_DONE] = enabled }
    }

    /** 更新提前预告提醒分钟数（0 = 关闭） */
    suspend fun updateEarlyReminderMinutes(minutes: Int) {
        dataStore.edit { it[EARLY_REMINDER_MINUTES] = minutes }
    }

    /** 更新小组件显示模式（true = 操作按物，false = 状态显示） */
    suspend fun updateWidgetShowActions(enabled: Boolean) {
        dataStore.edit { it[WIDGET_SHOW_ACTIONS] = enabled }
    }

    suspend fun updateWidgetAppearance(
        themeMode: WidgetThemeMode? = null,
        colorSource: WidgetColorSource? = null,
        paletteName: String? = null,
        densityScale: WidgetDensityScale? = null,
        textScale: WidgetTextScale? = null,
    ) {
        dataStore.edit { prefs ->
            if (themeMode != null) prefs[WIDGET_THEME_MODE] = themeMode.name
            if (colorSource != null) prefs[WIDGET_COLOR_SOURCE] = colorSource.name
            if (paletteName != null) prefs[WIDGET_PALETTE] = paletteName
            if (densityScale != null) prefs[WIDGET_DENSITY_SCALE] = densityScale.name
            if (textScale != null) prefs[WIDGET_TEXT_SCALE] = textScale.name
        }
    }

    /** 更新漏服再提醒设置 */
    suspend fun updateFollowUpSettings(
        enabled: Boolean? = null,
        delayMinutes: Int? = null,
        maxCount: Int? = null,
    ) {
        dataStore.edit { prefs ->
            if (enabled != null)      prefs[FOLLOW_UP_ENABLED]       = enabled
            if (delayMinutes != null) prefs[FOLLOW_UP_DELAY_MINUTES] = delayMinutes
            if (maxCount != null)     prefs[FOLLOW_UP_MAX_COUNT]     = maxCount
        }
    }

    /** 更新用户身高（cm），用于 BMI 计算 */
    suspend fun updateUserHeight(heightCm: Float) {
        dataStore.edit { it[USER_HEIGHT_CM] = heightCm }
    }

    /** 更新 OCR 识别模型类型 */
    suspend fun updateOcrModelType(modelType: OcrModelType) {
        dataStore.edit { it[OCR_MODEL_TYPE] = modelType.name }
    }

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
    ) {
        dataStore.edit { prefs ->
            val selectedProvider = provider ?: prefs[CLOUD_AI_PROVIDER]?.let {
                runCatching { CloudAiProvider.valueOf(it) }.getOrNull()
            } ?: CloudAiProvider.MIMO
            if (enabled != null) prefs[CLOUD_AI_ENABLED] = enabled
            if (imageAnalysisEnabled != null) prefs[CLOUD_AI_IMAGE_ANALYSIS_ENABLED] = imageAnalysisEnabled
            if (healthInsightsEnabled != null) prefs[CLOUD_AI_HEALTH_INSIGHTS_ENABLED] = healthInsightsEnabled
            if (wifiOnly != null) prefs[CLOUD_AI_WIFI_ONLY] = wifiOnly
            if (provider != null) {
                prefs[CLOUD_AI_PROVIDER] = provider.name
                if (model == null) {
                    prefs[CLOUD_AI_MODEL] = prefs[cloudAiModelKey(provider)] ?: provider.defaultModel
                }
            }
            if (model != null) {
                val resolvedModel = model.ifBlank { selectedProvider.defaultModel }
                prefs[CLOUD_AI_MODEL] = resolvedModel
                prefs[cloudAiModelKey(selectedProvider)] = resolvedModel
            }
            if (mimoBaseUrl != null) prefs[CLOUD_AI_MIMO_BASE_URL] = mimoBaseUrl.trim()
            if (anthropicBaseUrl != null) prefs[CLOUD_AI_ANTHROPIC_BASE_URL] = anthropicBaseUrl.trim()
            if (openAiCompatibleBaseUrl != null) prefs[OPENAI_COMPATIBLE_BASE_URL] = openAiCompatibleBaseUrl.trim()
            if (openAiCompatibleAuthMode != null) prefs[OPENAI_COMPATIBLE_AUTH_MODE] = openAiCompatibleAuthMode.name
            if (openAiCompatibleProviderName != null) {
                prefs[OPENAI_COMPATIBLE_PROVIDER_NAME] =
                    openAiCompatibleProviderName.ifBlank { "OpenAI-compatible" }
            }
        }
    }
}
