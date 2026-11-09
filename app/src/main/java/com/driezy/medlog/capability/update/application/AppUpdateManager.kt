package com.driezy.medlog.capability.update.application

import com.driezy.medlog.capability.update.model.AppUpdateInfo
import com.driezy.medlog.capability.update.model.AppUpdateState
import com.driezy.medlog.data.repository.UpdatePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateManager @Inject constructor(
    private val checkAppUpdateUseCase: CheckAppUpdateUseCase,
    private val updatePreferences: UpdatePreferences,
) {
    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    suspend fun checkForUpdates(isUserInitiated: Boolean = true): AppUpdateState {
        _updateState.value = AppUpdateState.Checking
        val result = checkAppUpdateUseCase()
        val newState = result.fold(
            onSuccess = { info: AppUpdateInfo ->
                updatePreferences.updateLastUpdateCheckTime(System.currentTimeMillis())
                if (info.isUpdateAvailable) {
                    if (isUserInitiated) {
                        _showUpdateDialog.value = true
                    }
                    AppUpdateState.UpdateAvailable(info)
                } else {
                    AppUpdateState.UpToDate(info.currentVersion)
                }
            },
            onFailure = { error ->
                AppUpdateState.Error(error.localizedMessage ?: "Check update failed")
            },
        )
        _updateState.value = newState
        return newState
    }

    suspend fun checkSilentlyIfDue() {
        val prefState = updatePreferences.update.first()
        if (!prefState.autoCheckUpdates) return
        val lastCheck = prefState.lastUpdateCheckTime
        val now = System.currentTimeMillis()
        if (now - lastCheck < 24 * 60 * 60 * 1000L) return

        val result = checkAppUpdateUseCase()
        result.onSuccess { info ->
            updatePreferences.updateLastUpdateCheckTime(now)
            if (info.isUpdateAvailable) {
                _updateState.value = AppUpdateState.UpdateAvailable(info)
            }
        }
    }

    fun openUpdateDialog() {
        if (_updateState.value is AppUpdateState.UpdateAvailable) {
            _showUpdateDialog.value = true
        }
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }
}
