package com.driezy.medlog.voice.doubao

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

enum class DoubaoFrameState(val protoValue: Int) {
    FIRST(1),
    MIDDLE(3),
    LAST(9),
}

data class DoubaoAudioPacket(
    val data: ByteArray,
    val frameState: DoubaoFrameState,
    val timestampMs: Long,
) {
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
    private var sentFrames = 0

    fun next(opusFrame: ByteArray, timestampMs: Long): DoubaoAudioPacket {
        val state = if (sentFrames == 0) DoubaoFrameState.FIRST else DoubaoFrameState.MIDDLE
        sentFrames += 1
        return DoubaoAudioPacket(opusFrame, state, timestampMs)
    }

    fun last(opusFrame: ByteArray, timestampMs: Long): DoubaoAudioPacket =
        DoubaoAudioPacket(opusFrame, DoubaoFrameState.LAST, timestampMs)
}
