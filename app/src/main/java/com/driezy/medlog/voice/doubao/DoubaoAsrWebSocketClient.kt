package com.driezy.medlog.voice.doubao

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class DoubaoAsrWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun connect(credentials: DoubaoDeviceCredentials): DoubaoAsrSocket =
        suspendCancellableCoroutine { continuation ->
            val responseChannel = Channel<DoubaoAsrResponse>(Channel.BUFFERED)
            val requestId = UUID.randomUUID().toString()
            val request = Request.Builder()
                .url("${DoubaoAsrConstants.WEBSOCKET_URL}?aid=${DoubaoAsrConstants.AID}&device_id=${credentials.deviceId}")
                .header("User-Agent", DoubaoAsrConstants.USER_AGENT)
                .header("proto-version", "v2")
                .header("x-custom-keepalive", "true")
                .header("Host", "frontier-audio-ime-ws.doubao.com")
                .build()
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(DoubaoAsrSocket(requestId, credentials.token, webSocket, responseChannel))
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    responseChannel.trySend(DoubaoAsrProtocol.parseResponse(bytes.toByteArray()))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    responseChannel.trySend(
                        DoubaoAsrResponse(
                            type = DoubaoAsrResponseType.ERROR,
                            errorMessage = t.message.orEmpty(),
                        ),
                    )
                    responseChannel.close(t)
                    if (continuation.isActive) continuation.resumeWithException(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    responseChannel.close()
                }
            }
            val webSocket = okHttpClient.newWebSocket(request, listener)
            continuation.invokeOnCancellation {
                webSocket.cancel()
                responseChannel.close()
            }
        }
}

class DoubaoAsrSocket internal constructor(
    private val requestId: String,
    private val token: String,
    private val webSocket: WebSocket,
    val responses: ReceiveChannel<DoubaoAsrResponse>,
) {
    fun sendStartTask(): Boolean =
        webSocket.send(DoubaoAsrProtocol.buildStartTask(requestId, token).toByteString())

    fun sendStartSession(deviceId: String): Boolean =
        webSocket.send(DoubaoAsrProtocol.buildStartSession(requestId, token, deviceId).toByteString())

    fun sendAudio(packet: DoubaoAudioPacket): Boolean =
        webSocket.send(DoubaoAsrProtocol.buildTaskRequest(requestId, packet).toByteString())

    fun finish(): Boolean =
        webSocket.send(DoubaoAsrProtocol.buildFinishSession(requestId, token).toByteString())

    fun close() {
        webSocket.close(1000, "voice input stopped")
    }

    fun cancel() {
        webSocket.cancel()
    }

    suspend fun await(type: DoubaoAsrResponseType) {
        withTimeout(10_000L) {
            while (true) {
                val response = responses.receive()
                if (response.type == DoubaoAsrResponseType.ERROR) {
                    throw IOException(response.errorMessage.ifBlank { "ASR protocol error" })
                }
                if (response.type == type) return@withTimeout
            }
        }
    }
}
