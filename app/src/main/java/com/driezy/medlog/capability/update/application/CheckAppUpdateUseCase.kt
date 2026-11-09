package com.driezy.medlog.capability.update.application

import android.os.Build
import com.driezy.medlog.BuildConfig
import com.driezy.medlog.capability.update.model.AppUpdateInfo
import com.driezy.medlog.capability.update.model.SemVer
import com.driezy.medlog.capability.update.remote.AppUpdateService
import com.driezy.medlog.capability.update.remote.GitHubReleaseAssetDto
import javax.inject.Inject

class CheckAppUpdateUseCase @Inject constructor(private val updateService: AppUpdateService) {

    suspend operator fun invoke(
        currentVersionName: String = BuildConfig.VERSION_NAME,
        supportedAbis: Array<String> = runCatching { Build.SUPPORTED_ABIS }.getOrNull() ?: emptyArray(),
    ): Result<AppUpdateInfo> = runCatching {
        val release = updateService.fetchLatestRelease().getOrThrow()
        val latestTag = release.tagName.trim()
        val latestSemVer = SemVer.parseOrNull(latestTag)
            ?: throw IllegalStateException("Could not parse latest release version: $latestTag")
        val currentSemVer = SemVer.parseOrNull(currentVersionName)
            ?: throw IllegalStateException("Could not parse current app version: $currentVersionName")

        val isUpdateAvailable = latestSemVer > currentSemVer
        val bestAsset = selectBestAsset(release.assets, supportedAbis)

        AppUpdateInfo(
            currentVersion = currentVersionName,
            latestVersion = latestTag.removePrefix("v").removePrefix("V"),
            isUpdateAvailable = isUpdateAvailable,
            releaseName = release.name ?: latestTag,
            releaseNotes = release.body ?: "",
            releaseUrl = release.htmlUrl,
            publishedAt = release.publishedAt ?: "",
            downloadUrl = bestAsset?.browserDownloadUrl ?: release.htmlUrl,
            downloadFileName = bestAsset?.name,
            fileSize = bestAsset?.size ?: 0L,
        )
    }

    internal fun selectBestAsset(
        assets: List<GitHubReleaseAssetDto>,
        supportedAbis: Array<String>,
    ): GitHubReleaseAssetDto? {
        val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apkAssets.isEmpty()) return null

        for (abi in supportedAbis) {
            val match = apkAssets.firstOrNull { it.name.contains(abi, ignoreCase = true) }
            if (match != null) return match
        }
        val universal = apkAssets.firstOrNull { it.name.contains("universal", ignoreCase = true) }
        if (universal != null) return universal
        return apkAssets.firstOrNull()
    }
}
