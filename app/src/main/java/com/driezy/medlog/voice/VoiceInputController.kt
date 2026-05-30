package com.driezy.medlog.voice

import kotlinx.coroutines.flow.SharedFlow

interface VoiceInputController {
    val events: SharedFlow<VoiceInputEvent>
    fun start()
    fun stop()
}

sealed interface VoiceInputEvent {
    data object Connecting : VoiceInputEvent
    data object Listening : VoiceInputEvent
    data object Stopped : VoiceInputEvent
    data class Transcript(val text: String, val isFinal: Boolean) : VoiceInputEvent
    data class Failed(val error: VoiceInputError, val detail: String = "") : VoiceInputEvent
}

enum class VoiceInputError {
    MISSING_PERMISSION,
    NETWORK_UNAVAILABLE,
    DEVICE_REGISTRATION_FAILED,
    TOKEN_UNAVAILABLE,
    WEBSOCKET_FAILED,
    ENCODER_UNAVAILABLE,
    RECORDER_UNAVAILABLE,
    PROTOCOL_FAILED,
    UNKNOWN,
}

data class VoiceInputUiState(
    val phase: VoiceInputPhase = VoiceInputPhase.IDLE,
    val error: VoiceInputError? = null,
    val detail: String = "",
)

enum class VoiceInputPhase {
    IDLE,
    CONNECTING,
    LISTENING,
    ERROR,
}
