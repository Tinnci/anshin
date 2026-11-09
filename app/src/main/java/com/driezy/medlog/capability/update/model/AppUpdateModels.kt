package com.driezy.medlog.capability.update.model

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val isUpdateAvailable: Boolean,
    val releaseName: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val publishedAt: String,
    val downloadUrl: String?,
    val downloadFileName: String?,
    val fileSize: Long,
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpToDate(val version: String, val checkedAt: Long = System.currentTimeMillis()) : AppUpdateState
    data class UpdateAvailable(val updateInfo: AppUpdateInfo) : AppUpdateState
    data class Error(val message: String) : AppUpdateState
}
