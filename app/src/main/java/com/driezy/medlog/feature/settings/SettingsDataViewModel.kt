package com.driezy.medlog.feature.settings

import android.content.Context
import android.util.Log
import com.driezy.medlog.R
import com.driezy.medlog.data.repository.OnboardingPreferences
import com.driezy.medlog.feature.settings.application.BackupRestoreUseCase
import com.driezy.medlog.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@HiltViewModel
class SettingsDataViewModel @Inject constructor(
    private val backupRestore: BackupRestoreUseCase,
    private val onboardingPreferences: OnboardingPreferences,
    @param:ApplicationContext private val context: Context,
) : BaseViewModel() {
    private val _inProgress = MutableStateFlow(false)
    val inProgress = _inProgress.asStateFlow()

    private val effectChannel = Channel<SettingsUiEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    fun onAction(action: SettingsUiAction) {
        when (action) {
            is SettingsUiAction.Backup -> backup(action)
            is SettingsUiAction.Restore -> restore(action)
            SettingsUiAction.ResetWelcome -> safeLaunch { onboardingPreferences.updateHasSeenWelcome(false) }
            else -> Unit
        }
    }

    private fun backup(action: SettingsUiAction.Backup) {
        safeLaunch {
            _inProgress.value = true
            runCatching { backupRestore.backup(action.uri) }
                .onSuccess {
                    effectChannel.send(SettingsUiEffect.Message(context.getString(R.string.settings_backup_success)))
                }
                .onFailure { error ->
                    Log.e("SettingsDataVM", "Backup failed", error)
                    effectChannel.send(SettingsUiEffect.Message(error.localizedMessage ?: "Unknown error"))
                }
            _inProgress.value = false
        }
    }

    private fun restore(action: SettingsUiAction.Restore) {
        safeLaunch {
            _inProgress.value = true
            runCatching { backupRestore.restore(action.uri) }
                .onSuccess { effectChannel.send(SettingsUiEffect.RestartApplication) }
                .onFailure { error ->
                    Log.e("SettingsDataVM", "Restore failed", error)
                    val message = if (error is IllegalArgumentException) {
                        context.getString(R.string.settings_backup_invalid_file)
                    } else {
                        error.localizedMessage ?: "Unknown error"
                    }
                    effectChannel.send(SettingsUiEffect.Message(message))
                    _inProgress.value = false
                }
        }
    }
}
