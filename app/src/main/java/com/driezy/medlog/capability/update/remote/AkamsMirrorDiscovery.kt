package com.driezy.medlog.capability.update.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream

internal object AkamsMirrorDiscovery {

    const val AKAMS_BASE_URL = "https://github.akams.cn"
    val DEFAULT_MIRRORS = listOf("https://ghproxy.net", "https://gh-proxy.com")
    const val TRUSTED_REPO_PREFIX = "https://github.com/Tinnci/anshin/"

    const val MAX_HTML_BYTES = 128 * 1024
    const val MAX_CHUNK_BYTES = 2 * 1024 * 1024
    const val MAX_CHUNKS = 16
    const val MAX_NODES = 10

    private val CHUNK_PATH_REGEX = Regex("""/_next/static/chunks/[a-zA-Z0-9_.~-]+\.js""")
    private val NODE_REGEX = Regex("""label:\s*"(?:contribute|search)",\s*value:\s*"([a-zA-Z0-9.-]+)"""")
    private val IPV4_REGEX = Regex("""^\d+(?:\.\d+){3}$""")
    private val HOST_CHAR_REGEX = Regex("""^[a-z0-9.-]+$""")

    fun sanitizeMirrorHost(raw: String): String? {
        val trimmed = raw.trim()
            .lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')

        if (trimmed.isEmpty() || trimmed.length > 253) return null
        if (!trimmed.contains('.')) return null
        if (trimmed.contains(':')) return null
        if (!HOST_CHAR_REGEX.matches(trimmed)) return null
        if (IPV4_REGEX.matches(trimmed)) return null
        if (trimmed == "localhost" ||
            trimmed.endsWith(".localhost") ||
            trimmed.endsWith(".local") ||
            trimmed.endsWith(".internal")
        ) {
            return null
        }

        val labels = trimmed.split('.')
        if (labels.any { it.isEmpty() || it.startsWith('-') || it.endsWith('-') }) {
            return null
        }

        return "https://$trimmed"
    }

    fun extractChunkPaths(html: String): List<String> =
        CHUNK_PATH_REGEX.findAll(html)
            .map { it.value }
            .distinct()
            .take(MAX_CHUNKS)
            .toList()

    fun extractMirrorNodes(js: String): List<String> =
        NODE_REGEX.findAll(js)
            .mapNotNull { match -> sanitizeMirrorHost(match.groupValues[1]) }
            .distinct()
            .take(MAX_NODES)
            .toList()

    fun proxiedUrl(mirrorBase: String, targetUrl: String): String =
        "${mirrorBase.trimEnd('/')}/$targetUrl"

    fun rewriteReleaseForMirror(release: GitHubReleaseDto, mirrorBase: String): GitHubReleaseDto {
        val rewrittenAssets = release.assets.map { asset ->
            if (isTrustedRepoUrl(asset.browserDownloadUrl)) {
                asset.copy(browserDownloadUrl = proxiedUrl(mirrorBase, asset.browserDownloadUrl))
            } else {
                asset
            }
        }
        val rewrittenHtmlUrl = if (isTrustedRepoUrl(release.htmlUrl)) {
            proxiedUrl(mirrorBase, release.htmlUrl)
        } else {
            release.htmlUrl
        }
        return release.copy(
            htmlUrl = rewrittenHtmlUrl,
            assets = rewrittenAssets,
        )
    }

    fun discoverMirrors(
        okHttpClient: OkHttpClient,
        userAgent: String,
        akamsBaseUrl: String = AKAMS_BASE_URL,
    ): List<String> {
        val base = akamsBaseUrl.trimEnd('/')
        val html = fetchBoundedText(okHttpClient, base, userAgent, MAX_HTML_BYTES) ?: return emptyList()
        val chunkPaths = extractChunkPaths(html)
        for (chunkPath in chunkPaths) {
            val js = fetchBoundedText(okHttpClient, "$base$chunkPath", userAgent, MAX_CHUNK_BYTES) ?: continue
            val nodes = extractMirrorNodes(js)
            if (nodes.isNotEmpty()) {
                return nodes
            }
        }
        return emptyList()
    }

    private fun isTrustedRepoUrl(url: String): Boolean =
        url.startsWith(TRUSTED_REPO_PREFIX, ignoreCase = true)

    private fun fetchBoundedText(
        okHttpClient: OkHttpClient,
        url: String,
        userAgent: String,
        maxBytes: Int,
    ): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                readBoundedUtf8(response, maxBytes)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readBoundedUtf8(response: Response, maxBytes: Int): String {
        val input = response.body.byteStream()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (total < maxBytes) {
            val toRead = minOf(buffer.size, maxBytes - total)
            val read = input.read(buffer, 0, toRead)
            if (read <= 0) break
            output.write(buffer, 0, read)
            total += read
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
