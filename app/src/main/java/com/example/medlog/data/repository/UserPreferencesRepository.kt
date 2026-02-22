package com.example.medlog.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.medlog.data.local.settingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 应用主题模式 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

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
    /** 是否启用症状日记（底部导航显示「日记」Tab） */
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
    // ── 漏服再提醒 ──────────────────────────────────────────────────────────────
    val followUpReminderEnabled: Boolean = false,
    val followUpDelayMinutes: Int = 15,
    val followUpMaxCount: Int = 1,
)

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
        // 今日页面显示偏好
        val AUTO_COLLAPSE_DONE = booleanPreferencesKey("auto_collapse_completed_groups")
        // 提前预告提醒
        val EARLY_REMINDER_MINUTES = intPreferencesKey("early_reminder_minutes")
        // 小组件显示偏好
        val WIDGET_SHOW_ACTIONS = booleanPreferencesKey("widget_show_actions")
        // 漏服再提醒
        val FOLLOW_UP_ENABLED       = booleanPreferencesKey("follow_up_reminder_enabled")
        val FOLLOW_UP_DELAY_MINUTES = intPreferencesKey("follow_up_delay_minutes")
        val FOLLOW_UP_MAX_COUNT     = intPreferencesKey("follow_up_max_count")
    }

    /** 持续输出最新设置（Flow，app 生命周期内可观察） */
    val settingsFlow: Flow<SettingsPreferences> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
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
                autoCollapseCompletedGroups = prefs[AUTO_COLLAPSE_DONE] ?: true,
                earlyReminderMinutes = prefs[EARLY_REMINDER_MINUTES] ?: 0,
                widgetShowActions = prefs[WIDGET_SHOW_ACTIONS] ?: true,
                followUpReminderEnabled = prefs[FOLLOW_UP_ENABLED] ?: false,
                followUpDelayMinutes    = prefs[FOLLOW_UP_DELAY_MINUTES] ?: 15,
                followUpMaxCount        = prefs[FOLLOW_UP_MAX_COUNT] ?: 1,
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
    }}