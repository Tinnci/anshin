package com.driezy.medlog.capability.update

import com.driezy.medlog.capability.update.application.CheckAppUpdateUseCase
import com.driezy.medlog.capability.update.remote.AppUpdateService
import com.driezy.medlog.capability.update.remote.GitHubReleaseAssetDto
import com.driezy.medlog.capability.update.remote.GitHubReleaseDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CheckAppUpdateUseCaseTest {

    private class FakeUpdateService(var result: Result<GitHubReleaseDto>) : AppUpdateService {
        override suspend fun fetchLatestRelease(): Result<GitHubReleaseDto> = result
    }

    private fun sampleRelease(
        tag: String = "v1.27.0",
        assets: List<GitHubReleaseAssetDto> = listOf(
            GitHubReleaseAssetDto(
                name = "Anshin-v1.27.0-arm64-v8a.apk",
                size = 80_000_000L,
                browserDownloadUrl = "https://example.com/arm64.apk",
            ),
            GitHubReleaseAssetDto(
                name = "Anshin-v1.27.0-armeabi-v7a.apk",
                size = 65_000_000L,
                browserDownloadUrl = "https://example.com/armeabi.apk",
            ),
            GitHubReleaseAssetDto(
                name = "Anshin-v1.27.0-universal.apk",
                size = 200_000_000L,
                browserDownloadUrl = "https://example.com/universal.apk",
            ),
        ),
    ) = GitHubReleaseDto(
        tagName = tag,
        name = "Anshin $tag",
        body = "Bug fixes and improvements",
        htmlUrl = "https://github.com/Tinnci/anshin/releases/tag/$tag",
        publishedAt = "2026-11-09T00:00:00Z",
        assets = assets,
    )

    @Test
    fun detectNewerVersion() = runTest {
        val fakeService = FakeUpdateService(Result.success(sampleRelease("v1.27.0")))
        val useCase = CheckAppUpdateUseCase(fakeService)

        val result = useCase(currentVersionName = "1.26.0", supportedAbis = arrayOf("arm64-v8a"))
        assertTrue(result.isSuccess)

        val info = result.getOrThrow()
        assertTrue(info.isUpdateAvailable)
        assertEquals("1.27.0", info.latestVersion)
        assertEquals("1.26.0", info.currentVersion)
        assertEquals("https://example.com/arm64.apk", info.downloadUrl)
        assertEquals("Anshin-v1.27.0-arm64-v8a.apk", info.downloadFileName)
    }

    @Test
    fun detectUpToDateWhenSameVersion() = runTest {
        val fakeService = FakeUpdateService(Result.success(sampleRelease("v1.26.0")))
        val useCase = CheckAppUpdateUseCase(fakeService)

        val result = useCase(currentVersionName = "1.26.0", supportedAbis = arrayOf("arm64-v8a"))
        assertTrue(result.isSuccess)

        val info = result.getOrThrow()
        assertFalse(info.isUpdateAvailable)
        assertEquals("1.26.0", info.latestVersion)
    }

    @Test
    fun selectAppropriateDeviceAbiAsset() = runTest {
        val fakeService = FakeUpdateService(Result.success(sampleRelease("v1.27.0")))
        val useCase = CheckAppUpdateUseCase(fakeService)

        // 32-bit ARM device
        val arm32Result = useCase(currentVersionName = "1.26.0", supportedAbis = arrayOf("armeabi-v7a", "armeabi"))
        assertEquals("https://example.com/armeabi.apk", arm32Result.getOrThrow().downloadUrl)

        // x86_64 emulator falls back to universal APK
        val x86Result = useCase(currentVersionName = "1.26.0", supportedAbis = arrayOf("x86_64", "x86"))
        assertEquals("https://example.com/universal.apk", x86Result.getOrThrow().downloadUrl)
    }

    @Test
    fun fallbackToReleaseUrlWhenNoApkAssets() = runTest {
        val release = sampleRelease("v1.27.0", assets = emptyList())
        val fakeService = FakeUpdateService(Result.success(release))
        val useCase = CheckAppUpdateUseCase(fakeService)

        val result = useCase(currentVersionName = "1.26.0", supportedAbis = arrayOf("arm64-v8a"))
        val info = result.getOrThrow()
        assertEquals(release.htmlUrl, info.downloadUrl)
        assertEquals(null, info.downloadFileName)
    }

    @Test
    fun networkFailurePropagates() = runTest {
        val fakeService = FakeUpdateService(Result.failure(IOException("No internet")))
        val useCase = CheckAppUpdateUseCase(fakeService)

        val result = useCase(currentVersionName = "1.26.0", supportedAbis = arrayOf("arm64-v8a"))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }
}
