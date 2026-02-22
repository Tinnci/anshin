package com.example.medlog.ui

import androidx.lifecycle.ViewModel
import com.example.medlog.ui.BaseViewModel
import androidx.lifecycle.viewModelScope
import com.example.medlog.data.repository.SettingsPreferences
import com.example.medlog.data.repository.UserPreferencesRepository
import com.example.medlog.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * App 壳层 ViewModel。
 * 职责（SRP）：确定 NavHost 的初始目的地 + 全局功能开关。
 */
@HiltViewModel
class MedLogAppViewModel @Inject constructor(
    prefsRepository: UserPreferencesRepository,
) : BaseViewModel() {

    val startDestination: StateFlow<Route?> = prefsRepository.settingsFlow
        .map { prefs -> if (prefs.hasSeenWelcome) Route.Home else Route.Welcome }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    /** 全局功能开关（用于动态过滤底部导航目标） */
    val featureFlags: StateFlow<SettingsPreferences> = prefsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsPreferences(),
        )
}
