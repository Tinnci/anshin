package com.example.medlog.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * 应用设置 DataStore 单例扩展属性。
 * 集中声明，供 [UserPreferencesRepository] 和 Glance 小组件共享同一 DataStore 实例。
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "medlog_settings",
)
