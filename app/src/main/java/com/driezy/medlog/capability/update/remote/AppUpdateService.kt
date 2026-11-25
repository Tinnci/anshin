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

    private var defaultMirrors: List<String> = AkamsMirrorDiscovery.DEFAULT_MIRRORS
    private var akamsBaseUrl: String = AkamsMirrorDiscovery.AKAMS_BASE_URL

    @Volatile
    private var cachedAkamsMirrors: List<String>? = null

    internal constructor(
        okHttpClient: OkHttpClient,
        defaultMirrors: List<String> = AkamsMirrorDiscovery.DEFAULT_MIRRORS,
        akamsBaseUrl: String = AkamsMirrorDiscovery.AKAMS_BASE_URL,
    ) : this(okHttpClient) {
        this.defaultMirrors = defaultMirrors
        this.akamsBaseUrl = akamsBaseUrl
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun fetchLatestRelease(): Result<GitHubReleaseDto> = withContext(Dispatchers.IO) {
        val userAgent = "Anshin-Android/${BuildConfig.VERSION_NAME}"
        var lastError: Throwable? = null

        try {
            return@withContext Result.success(executeReleaseRequest(LATEST_RELEASE_URL, userAgent))
        } catch (t: Throwable) {
            lastError = t
        }

        val triedMirrors = LinkedHashSet<String>()
        for (mirror in defaultMirrors) {
            triedMirrors.add(mirror)
            try {
                val proxied = AkamsMirrorDiscovery.proxiedUrl(mirror, LATEST_RELEASE_URL)
                val release = executeReleaseRequest(proxied, userAgent)
                return@withContext Result.success(AkamsMirrorDiscovery.rewriteReleaseForMirror(release, mirror))
            } catch (t: Throwable) {
                lastError = t
            }
        }

        val discovered = cachedAkamsMirrors ?: AkamsMirrorDiscovery.discoverMirrors(
            okHttpClient = okHttpClient,
            userAgent = userAgent,
            akamsBaseUrl = akamsBaseUrl,
        ).also { cachedAkamsMirrors = it }

        for (mirror in discovered) {
            if (!triedMirrors.add(mirror)) continue
            try {
                val proxied = AkamsMirrorDiscovery.proxiedUrl(mirror, LATEST_RELEASE_URL)
                val release = executeReleaseRequest(proxied, userAgent)
                return@withContext Result.success(AkamsMirrorDiscovery.rewriteReleaseForMirror(release, mirror))
            } catch (t: Throwable) {
                lastError = t
            }
        }

        Result.failure(lastError ?: IOException("Failed to fetch latest release"))
    }

    private fun executeReleaseRequest(url: String, userAgent: String): GitHubReleaseDto {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/vnd.github.v3+json")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub API error HTTP ${response.code}: ${response.message}")
            }
            val body = response.body.string()
            if (body.isBlank()) throw IOException("Empty response body from GitHub API")
            return json.decodeFromString<GitHubReleaseDto>(body)
        }
    }

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/Tinnci/anshin/releases/latest"
    }
}
