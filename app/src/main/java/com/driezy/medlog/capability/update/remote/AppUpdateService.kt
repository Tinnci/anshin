package com.driezy.medlog.capability.update.remote

import com.driezy.medlog.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface AppUpdateService {
    suspend fun fetchLatestRelease(): Result<GitHubReleaseDto>
}

@Singleton
class GitHubReleaseClient @Inject constructor(private val okHttpClient: OkHttpClient) : AppUpdateService {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun fetchLatestRelease(): Result<GitHubReleaseDto> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("User-Agent", "Anshin-Android/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/vnd.github.v3+json")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("GitHub API error HTTP ${response.code}: ${response.message}")
                }
                val body = response.body.string()
                if (body.isBlank()) throw IOException("Empty response body from GitHub API")
                json.decodeFromString<GitHubReleaseDto>(body)
            }
        }
    }

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/Tinnci/anshin/releases/latest"
    }
}
