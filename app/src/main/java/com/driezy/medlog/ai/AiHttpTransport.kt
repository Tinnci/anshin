package com.driezy.medlog.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class AiHttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
)

data class AiHttpResponse(
    val code: Int,
    val body: String,
)

interface AiHttpTransport {
    suspend fun post(request: AiHttpRequest): AiHttpResponse
}

class UrlConnectionAiHttpTransport(
    private val connectTimeoutMillis: Int = 30_000,
    private val readTimeoutMillis: Int = 60_000,
) : AiHttpTransport {

    override suspend fun post(request: AiHttpRequest): AiHttpResponse =
        withContext(Dispatchers.IO) {
            val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                doOutput = true
                request.headers.forEach { (key, value) -> setRequestProperty(key, value) }
            }

            try {
                connection.outputStream.use { output ->
                    output.write(request.body.toByteArray(Charsets.UTF_8))
                }

                val stream = if (connection.responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream ?: connection.inputStream
                }
                AiHttpResponse(
                    code = connection.responseCode,
                    body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() },
                )
            } finally {
                connection.disconnect()
            }
        }
}
