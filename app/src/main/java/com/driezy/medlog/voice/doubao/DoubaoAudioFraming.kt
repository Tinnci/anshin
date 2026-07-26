package com.driezy.medlog.voice.doubao

import kotlin.math.sqrt

class DoubaoAudioFrameChunker {
    private val pending = ArrayList<Byte>(PCM_FRAME_BYTES)

    fun offer(bytes: ByteArray): List<ByteArray> {
        if (bytes.isEmpty()) return emptyList()
        pending.addAll(bytes.toList())

        val frames = mutableListOf<ByteArray>()
        while (pending.size >= PCM_FRAME_BYTES) {
            val frame = ByteArray(PCM_FRAME_BYTES)
            for (i in frame.indices) frame[i] = pending[i]
            repeat(PCM_FRAME_BYTES) { pending.removeAt(0) }
            frames += frame
        }
        return frames
    }

    fun flushRemainder(): ByteArray {
        val remainder = pending.toByteArray()
        pending.clear()
        return remainder
    }

    companion object {
        const val SAMPLE_RATE: Int = 16_000
        const val CHANNELS: Int = 1
        const val BITS_PER_SAMPLE: Int = 16
        const val FRAME_DURATION_MS: Int = 20
        const val PCM_FRAME_BYTES: Int = SAMPLE_RATE * FRAME_DURATION_MS / 1_000 * CHANNELS * (BITS_PER_SAMPLE / 8)
    }
}

class DoubaoSilenceEndDetector(
    private val trailingSilenceMs: Int = DEFAULT_TRAILING_SILENCE_MS,
    private val initialSilenceMs: Int = DEFAULT_INITIAL_SILENCE_MS,
    private val rmsThreshold: Int = DEFAULT_RMS_THRESHOLD,
    private val frameDurationMs: Int = DoubaoAudioFrameChunker.FRAME_DURATION_MS,
) {
    private var hasSpeech = false
    private var trailingSilenceMsSeen = 0
    private var totalMsSeen = 0

    fun offer(pcmFrame: ByteArray): Boolean {
        totalMsSeen += frameDurationMs
        val isSilent = pcmFrame.rms() < rmsThreshold
        if (isSilent) {
            if (hasSpeech) trailingSilenceMsSeen += frameDurationMs
        } else {
            hasSpeech = true
            trailingSilenceMsSeen = 0
        }
        return if (hasSpeech) {
            trailingSilenceMsSeen >= trailingSilenceMs
        } else {
            totalMsSeen >= initialSilenceMs
        }
    }

    private fun ByteArray.rms(): Double {
        if (size < 2) return 0.0
        var sumSquares = 0.0
        var samples = 0
        var index = 0
        while (index + 1 < size) {
            val low = this[index].toInt() and 0xff
            val high = this[index + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toInt()
            sumSquares += sample.toDouble() * sample
            samples += 1
            index += 2
        }
        return sqrt(sumSquares / samples)
    }

    companion object {
        const val DEFAULT_TRAILING_SILENCE_MS: Int = 2_000
        const val DEFAULT_INITIAL_SILENCE_MS: Int = 8_000
        const val DEFAULT_RMS_THRESHOLD: Int = 500
    }
}

enum class DoubaoFrameState(val protoValue: Int) {
    FIRST(1),
    MIDDLE(3),
    LAST(9),
}

data class DoubaoAudioPacket(val data: ByteArray, val frameState: DoubaoFrameState, val timestampMs: Long) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DoubaoAudioPacket
        return data.contentEquals(other.data) &&
            frameState == other.frameState &&
            timestampMs == other.timestampMs
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + frameState.hashCode()
        result = 31 * result + timestampMs.hashCode()
        return result
    }
}

class DoubaoAudioFrameSequencer {
    var sentFrameCount = 0
        private set

    fun next(opusFrame: ByteArray, timestampMs: Long): DoubaoAudioPacket {
        val state = if (sentFrameCount == 0) DoubaoFrameState.FIRST else DoubaoFrameState.MIDDLE
        sentFrameCount += 1
        return DoubaoAudioPacket(opusFrame, state, timestampMs)
    }

    fun last(opusFrame: ByteArray, timestampMs: Long): DoubaoAudioPacket =
        DoubaoAudioPacket(opusFrame, DoubaoFrameState.LAST, timestampMs)
}
