package com.driezy.medlog.capability.update

import com.driezy.medlog.capability.update.remote.AkamsMirrorDiscovery
import com.driezy.medlog.capability.update.remote.GitHubReleaseAssetDto
import com.driezy.medlog.capability.update.remote.GitHubReleaseClient
import com.driezy.medlog.capability.update.remote.GitHubReleaseDto
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AkamsMirrorDiscoveryTest {

    @Test
    fun sanitizeMirrorHostAcceptsValidAndRejectsUnsafe() {
        assertEquals("https://gh.llkk.cc", AkamsMirrorDiscovery.sanitizeMirrorHost("gh.llkk.cc"))
        assertEquals("https://ghproxy.net", AkamsMirrorDiscovery.sanitizeMirrorHost("https://ghproxy.net/"))
        assertEquals("https://gh-proxy.com", AkamsMirrorDiscovery.sanitizeMirrorHost("HTTP://GH-PROXY.COM/"))

        assertNull(AkamsMirrorDiscovery.sanitizeMirrorHost(""))
        assertNull(AkamsMirrorDiscovery.sanitizeMirrorHost("localhost"))
        assertNull(AkamsMirrorDiscovery.sanitizeMirrorHost("api.localhost"))
        assertNull(AkamsMirrorDiscovery.sanitizeMirrorHost("127.0.0.1"))
        assertNull(AkamsMirrorDiscovery.sanitizeMirrorHost("10.0.0.1"))
        assertNull(AkamsMirrorDiscovery.sanitizeMirrorHost("192.168.1.1"))
        assertNull(AkamsMirrorDiscovery.sanitizeMirrorHost("[::1]"))
        assertNull(AkamsMirrorDiscovery.sanitizeMirrorHost("service.local"))
        assertNull(AkamsMirrorDiscovery.sanitizeMirrorHost("meta.internal"))
        assertNull(AkamsMirrorDiscovery.sanitizeMirrorHost("bad/path.com"))
        assertNull(AkamsMirrorDiscovery.sanitizeMirrorHost("no-dot-host"))
        assertNull(AkamsMirrorDiscovery.sanitizeMirrorHost("-bad.example.com"))
    }

    @Test
    fun extractChunkPathsDeduplicatesAndCapsAtMaxChunks() {
        val chunks = (1..20).joinToString("\n") { i ->
            """<script src="/_next/static/chunks/chunk-$i.js"></script>"""
        } + """<script src="/_next/static/chunks/chunk-1.js"></script>"""

        val extracted = AkamsMirrorDiscovery.extractChunkPaths(chunks)
        assertEquals(AkamsMirrorDiscovery.MAX_CHUNKS, extracted.size)
        assertEquals("/_next/static/chunks/chunk-1.js", extracted.first())
        assertEquals("/_next/static/chunks/chunk-16.js", extracted.last())
    }

    @Test
    fun extractMirrorNodesFiltersSsrfAndCapsAtMaxNodes() {
        val js = """
            const a = [
              {label:"contribute",value:"localhost"},
              {label:"search",value:"127.0.0.1"},
              {label:"contribute",value:"gh.llkk.cc"},
              {label:"search",value:"ghproxy.net"},
              {label:"contribute",value:"gh.llkk.cc"},
              ${(1..12).joinToString(",") { """{label:"search",value:"node$it.example.com"}""" }}
            ];
        """.trimIndent()

        val nodes = AkamsMirrorDiscovery.extractMirrorNodes(js)
        assertEquals(AkamsMirrorDiscovery.MAX_NODES, nodes.size)
        assertEquals("https://gh.llkk.cc", nodes[0])
        assertEquals("https://ghproxy.net", nodes[1])
        assertEquals("https://node1.example.com", nodes[2])
        assertEquals("https://node8.example.com", nodes[9])
    }

    @Test
    fun rewriteReleaseForMirrorRewritesTrustedUrlsOnly() {
        val release = GitHubReleaseDto(
            tagName = "v1.28.0",
            htmlUrl = "https://github.com/Tinnci/anshin/releases/tag/v1.28.0",
            assets = listOf(
                GitHubReleaseAssetDto(
                    name = "Anshin-v1.28.0-arm64-v8a.apk",
                    browserDownloadUrl = "https://github.com/Tinnci/anshin/releases/download/v1.28.0/Anshin-v1.28.0-arm64-v8a.apk",
                ),
                GitHubReleaseAssetDto(
                    name = "evil.apk",
                    browserDownloadUrl = "https://evil.example.com/payload.apk",
                ),
            ),
        )

        val rewritten = AkamsMirrorDiscovery.rewriteReleaseForMirror(release, "https://ghproxy.net/")
        assertEquals(
            "https://ghproxy.net/https://github.com/Tinnci/anshin/releases/tag/v1.28.0",
            rewritten.htmlUrl,
        )
        assertEquals(
            "https://ghproxy.net/https://github.com/Tinnci/anshin/releases/download/v1.28.0/Anshin-v1.28.0-arm64-v8a.apk",
            rewritten.assets[0].browserDownloadUrl,
        )
        assertEquals(
            "https://evil.example.com/payload.apk",
            rewritten.assets[1].browserDownloadUrl,
        )
    }

    @Test
    fun directSuccessDoesNotCallMirrors() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = buildClient { request ->
            val url = request.url.toString()
            requestedUrls.add(url)
            jsonResponse(request, sampleReleaseJson("v1.28.0"))
        }

        val service = GitHubReleaseClient(client)
        val result = service.fetchLatestRelease()
        assertTrue(result.isSuccess)
        assertEquals(listOf(GitHubReleaseClient.LATEST_RELEASE_URL), requestedUrls)
        assertEquals(
            "https://github.com/Tinnci/anshin/releases/download/v1.28.0/Anshin-v1.28.0-arm64-v8a.apk",
            result.getOrThrow().assets.first().browserDownloadUrl,
        )
    }

    @Test
    fun directFailureFallsBackToStaticMirrorAndRewritesDownloadUrls() = runTest {
        val requestedUrls = mutableListOf<String>()
        val staticMirror = "https://ghproxy.net"
        val client = buildClient { request ->
            val url = request.url.toString()
            requestedUrls.add(url)
            when (url) {
                GitHubReleaseClient.LATEST_RELEASE_URL -> throw IOException("Direct GitHub blocked")
                "$staticMirror/${GitHubReleaseClient.LATEST_RELEASE_URL}" -> jsonResponse(
                    request,
                    sampleReleaseJson("v1.28.0"),
                )
                else -> error("Unexpected request: $url")
            }
        }

        val service = GitHubReleaseClient(
            okHttpClient = client,
            defaultMirrors = listOf(staticMirror),
            akamsBaseUrl = "https://github.akams.cn",
        )
        val result = service.fetchLatestRelease()
        assertTrue(result.isSuccess)
        assertEquals(
            listOf(
                GitHubReleaseClient.LATEST_RELEASE_URL,
                "$staticMirror/${GitHubReleaseClient.LATEST_RELEASE_URL}",
            ),
            requestedUrls,
        )
        val dto = result.getOrThrow()
        assertEquals(
            "$staticMirror/https://github.com/Tinnci/anshin/releases/download/v1.28.0/Anshin-v1.28.0-arm64-v8a.apk",
            dto.assets.first().browserDownloadUrl,
        )
        assertEquals(
            "$staticMirror/https://github.com/Tinnci/anshin/releases/tag/v1.28.0",
            dto.htmlUrl,
        )
    }

    @Test
    fun staticFailureTriggersAkamsDiscoveryAndCachesResult() = runTest {
        val requestedUrls = mutableListOf<String>()
        val staticMirror = "https://ghproxy.net"
        val akamsBase = "https://github.akams.cn"
        val discoveredMirror = "https://gh.llkk.cc"

        val client = buildClient { request ->
            val url = request.url.toString()
            requestedUrls.add(url)
            when (url) {
                GitHubReleaseClient.LATEST_RELEASE_URL -> throw IOException("Direct blocked")
                "$staticMirror/${GitHubReleaseClient.LATEST_RELEASE_URL}" -> errorResponse(request, 502)
                akamsBase, "$akamsBase/" -> textResponse(
                    request,
                    """<html><script src="/_next/static/chunks/app-123.js"></script></html>""",
                    "text/html",
                )
                "$akamsBase/_next/static/chunks/app-123.js" -> textResponse(
                    request,
                    """const nodes=[{label:"contribute",value:"gh.llkk.cc"}];""",
                    "application/javascript",
                )
                "$discoveredMirror/${GitHubReleaseClient.LATEST_RELEASE_URL}" -> jsonResponse(
                    request,
                    sampleReleaseJson("v1.29.0"),
                )
                else -> error("Unexpected request: $url")
            }
        }

        val service = GitHubReleaseClient(
            okHttpClient = client,
            defaultMirrors = listOf(staticMirror),
            akamsBaseUrl = akamsBase,
        )

        val first = service.fetchLatestRelease()
        assertTrue(first.isSuccess)
        assertEquals("v1.29.0", first.getOrThrow().tagName)
        assertEquals(
            "$discoveredMirror/https://github.com/Tinnci/anshin/releases/download/v1.29.0/Anshin-v1.29.0-arm64-v8a.apk",
            first.getOrThrow().assets.first().browserDownloadUrl,
        )

        val second = service.fetchLatestRelease()
        assertTrue(second.isSuccess)
        assertEquals(1, requestedUrls.count { it.trimEnd('/') == akamsBase })
    }

    @Test
    fun allFailuresPropagateFailureResult() = runTest {
        val client = buildClient { _ ->
            throw IOException("Offline")
        }
        val service = GitHubReleaseClient(
            okHttpClient = client,
            defaultMirrors = listOf("https://ghproxy.net"),
            akamsBaseUrl = "https://github.akams.cn",
        )
        val result = service.fetchLatestRelease()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    private fun buildClient(handler: (okhttp3.Request) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .build()

    private fun jsonResponse(request: okhttp3.Request, json: String): Response =
        textResponse(request, json, "application/json")

    private fun textResponse(request: okhttp3.Request, body: String, mediaType: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody(mediaType.toMediaType()))
            .build()

    private fun errorResponse(request: okhttp3.Request, code: Int): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Error")
            .body("".toResponseBody("text/plain".toMediaType()))
            .build()

    private fun sampleReleaseJson(tag: String): String = """
        {
          "tag_name": "$tag",
          "name": "Anshin $tag",
          "html_url": "https://github.com/Tinnci/anshin/releases/tag/$tag",
          "assets": [
            {
              "name": "Anshin-$tag-arm64-v8a.apk",
              "size": 80000000,
              "browser_download_url": "https://github.com/Tinnci/anshin/releases/download/$tag/Anshin-$tag-arm64-v8a.apk"
            }
          ]
        }
    """.trimIndent()
}
