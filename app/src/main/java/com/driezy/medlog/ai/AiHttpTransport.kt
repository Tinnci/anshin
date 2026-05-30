package com.driezy.medlog.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class AiHttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String = "",
)

data class AiHttpResponse(
    val code: Int,
    val body: String,
)

interface AiHttpTransport {
    suspend fun post(request: AiHttpRequest): AiHttpResponse

    suspend fun get(request: AiHttpRequest): AiHttpResponse =
        throw UnsupportedOperationException("GET is not supported by this transport")
}

class UrlConnectionAiHttpTransport(
    private val connectTimeoutMillis: Int = 30_000,
    private val readTimeoutMillis: Int = 60_000,
) : AiHttpTransport {

    override suspend fun post(request: AiHttpRequest): AiHttpResponse =
        execute(request = request, method = "POST", writeBody = true)

    override suspend fun get(request: AiHttpRequest): AiHttpResponse =
        execute(request = request, method = "GET", writeBody = false)

    private suspend fun execute(
        request: AiHttpRequest,
        method: String,
        writeBody: Boolean,
    ): AiHttpResponse =
        withContext(Dispatchers.IO) {
            val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                doOutput = writeBody
                request.headers.forEach { (key, value) -> setRequestProperty(key, value) }
            }

            try {
                if (writeBody) {
                    connection.outputStream.use { output ->
                        output.write(request.body.toByteArray(Charsets.UTF_8))
                    }
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
