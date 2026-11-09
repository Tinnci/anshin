package com.driezy.medlog.ui

import androidx.lifecycle.viewModelScope
import com.driezy.medlog.capability.bpx1.application.Bpx1SyncService
import com.driezy.medlog.capability.update.application.AppUpdateManager
import com.driezy.medlog.data.repository.SettingsPreferences
import com.driezy.medlog.data.repository.UserPreferencesRepository
import com.driezy.medlog.ui.BaseViewModel
import com.driezy.medlog.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * App 壳层 ViewModel。
 * 职责（SRP）：确定 NavHost 的初始目的地 + 全局功能开关 + 前台 BPX1 自动同步生命周期协调。
 */
@HiltViewModel
class MedLogAppViewModel @Inject constructor(
    prefsRepository: UserPreferencesRepository,
    private val bpx1SyncService: Bpx1SyncService,
    private val appUpdateManager: AppUpdateManager,
) : BaseViewModel() {

    init {
        safeLaunch {
            appUpdateManager.checkSilentlyIfDue()
        }
    }

    fun setAppForegroundActive(active: Boolean) {
        bpx1SyncService.setForegroundActive(active)
    }

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
