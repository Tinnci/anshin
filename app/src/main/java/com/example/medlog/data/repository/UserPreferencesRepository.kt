package com.example.medlog.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** DataStore 文件名 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "medlog_settings",
)

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
     * 适用于短期出行、蕊时皮量、抢时差消失等场景。
     */
    val travelMode: Boolean = false,
    /** 家乡时区 ID（如 "Asia/Shanghai"）。空串表示使用内容是设备默认时区。 */
    val homeTimeZoneId: String = "",
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
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
}