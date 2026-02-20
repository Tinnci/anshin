package com.example.medlog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * 职责（SRP）：仅负责确定 NavHost 的初始目的地。
 * - `null`  → 正在加载 DataStore，NavHost 尚未渲染（避免闪现错误路由）
 * - [Route.Welcome] → 首次启动，显示引导页
 * - [Route.Home]    → 已完成引导，直接进入主页
 */
@HiltViewModel
class MedLogAppViewModel @Inject constructor(
    prefsRepository: UserPreferencesRepository,
) : ViewModel() {

    val startDestination: StateFlow<Route?> = prefsRepository.settingsFlow
        .map { prefs -> if (prefs.hasSeenWelcome) Route.Home else Route.Welcome }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,   // null = 正在加载，外层显示加载指示器
        )
}
