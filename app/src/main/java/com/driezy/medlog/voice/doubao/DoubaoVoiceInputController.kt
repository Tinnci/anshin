package com.driezy.medlog.voice.doubao

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import com.driezy.medlog.di.ApplicationScope
import com.driezy.medlog.voice.VoiceInputController
import com.driezy.medlog.voice.VoiceInputError
import com.driezy.medlog.voice.VoiceInputEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoubaoVoiceInputController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val deviceClient: DoubaoDeviceClient,
    private val webSocketClient: DoubaoAsrWebSocketClient,
) : VoiceInputController {
    private val _events = MutableSharedFlow<VoiceInputEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<VoiceInputEvent> = _events
    private var job: Job? = null

    override fun start() {
        if (job?.isActive == true) return
        job = applicationScope.launch {
            runSession()
        }
    }

    override fun stop() {
        val activeJob = job ?: return
        applicationScope.launch {
            activeJob.cancelAndJoin()
            _events.emit(VoiceInputEvent.Stopped)
        }
        job = null
    }

    private suspend fun runSession() {
        var socket: DoubaoAsrSocket? = null
        try {
            if (!hasMicrophonePermission()) {
                _events.emit(VoiceInputEvent.Failed(VoiceInputError.MISSING_PERMISSION))
                return
            }
            if (!hasNetwork()) {
                _events.emit(VoiceInputEvent.Failed(VoiceInputError.NETWORK_UNAVAILABLE))
                return
            }

            _events.emit(VoiceInputEvent.Connecting)
            val credentials = deviceClient.ensureCredentials().getOrElse {
                _events.emit(VoiceInputEvent.Failed(VoiceInputError.DEVICE_REGISTRATION_FAILED, it.message.orEmpty()))
                return
            }
            if (credentials.token.isBlank()) {
                _events.emit(VoiceInputEvent.Failed(VoiceInputError.TOKEN_UNAVAILABLE))
                return
            }

            socket = webSocketClient.connect(credentials)
            check(socket.sendStartTask()) { "Failed to send StartTask" }
            socket.await(DoubaoAsrResponseType.TASK_STARTED)
            check(socket.sendStartSession(credentials.deviceId)) { "Failed to send StartSession" }
            socket.await(DoubaoAsrResponseType.SESSION_STARTED)

            _events.emit(VoiceInputEvent.Listening)
            val sessionStoppedNormally = coroutineScope {
                val audioJob = launchAudioSender(socket)
                val stoppedNormally = receiveResponses(socket)
                audioJob.cancelAndJoin()
                stoppedNormally
            }
            if (sessionStoppedNormally) {
                _events.emit(VoiceInputEvent.Stopped)
            }
        } catch (cancelled: CancellationException) {
            socket?.let { finishSocket(it) }
            throw cancelled
        } catch (error: Throwable) {
            _events.emit(VoiceInputEvent.Failed(error.toVoiceInputError(), error.message.orEmpty()))
        } finally {
            socket?.close()
        }
    }

    @SuppressLint("MissingPermission")
    private fun CoroutineScope.launchAudioSender(socket: DoubaoAsrSocket): Job = launch(Dispatchers.IO) {
        val minBuffer = AudioRecord.getMinBufferSize(
            DoubaoAudioFrameChunker.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) error("Invalid microphone buffer size")

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            DoubaoAudioFrameChunker.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, DoubaoAudioFrameChunker.PCM_FRAME_BYTES * 4),
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error("AudioRecord initialization failed")
        }

        val chunker = DoubaoAudioFrameChunker()
        val sequencer = DoubaoAudioFrameSequencer()
        val encoder = runCatching { AndroidDoubaoOpusEncoder() }.getOrElse {
            recorder.release()
            throw OpusEncoderUnavailableException(it)
        }
        val silenceEndDetector = DoubaoSilenceEndDetector()
        val readBuffer = ByteArray(DoubaoAudioFrameChunker.PCM_FRAME_BYTES * 2)
        val startedAt = System.currentTimeMillis()

        try {
            recorder.startRecording()
            recording@ while (true) {
                val read = recorder.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                if (read > 0) {
                    chunker.offer(readBuffer.copyOf(read)).forEach { pcmFrame ->
                        if (silenceEndDetector.offer(pcmFrame)) {
                            break@recording
                        }
                        encoder.encode(pcmFrame)?.let { opusFrame ->
                            val elapsed = System.currentTimeMillis() - startedAt
                            socket.sendAudio(sequencer.next(opusFrame, elapsed))
                        }
                    }
                }
            }
        } finally {
            val elapsed = System.currentTimeMillis() - startedAt
            if (sequencer.sentFrameCount > 0) {
                encoder.encode(
                    ByteArray(DoubaoAudioFrameChunker.PCM_FRAME_BYTES),
                    endOfStream = true,
                )?.let { opusFrame ->
                    socket.sendAudio(sequencer.last(opusFrame, elapsed))
                }
                finishSocket(socket)
            }
            encoder.close()
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    private suspend fun receiveResponses(socket: DoubaoAsrSocket): Boolean {
        try {
            while (true) {
                val response = socket.responses.receive()
                when (response.type) {
                    DoubaoAsrResponseType.INTERIM_RESULT,
                    DoubaoAsrResponseType.FINAL_RESULT,
                    -> {
                        if (response.text.isNotBlank()) {
                            _events.emit(VoiceInputEvent.Transcript(response.text, response.isFinal))
                        }
                        if (response.vadFinished) {
                            finishSocket(socket)
                            return true
                        }
                    }
                    DoubaoAsrResponseType.ERROR -> {
                        _events.emit(VoiceInputEvent.Failed(VoiceInputError.PROTOCOL_FAILED, response.errorMessage))
                        return false
                    }
                    DoubaoAsrResponseType.SESSION_FINISHED -> return true
                    else -> Unit
                }
            }
        } catch (_: ClosedReceiveChannelException) {
            return true
        }
    }

    private fun finishSocket(socket: DoubaoAsrSocket) {
        runCatching { socket.finish() }
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasNetwork(): Boolean {
        val manager = ContextCompat.getSystemService(context, ConnectivityManager::class.java) ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun Throwable.toVoiceInputError(): VoiceInputError = when (this) {
        is OpusEncoderUnavailableException -> VoiceInputError.ENCODER_UNAVAILABLE
        is IllegalStateException -> VoiceInputError.PROTOCOL_FAILED
        else -> VoiceInputError.WEBSOCKET_FAILED
    }
}

private class OpusEncoderUnavailableException(cause: Throwable) : RuntimeException(cause)
