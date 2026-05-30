package com.driezy.medlog.voice.doubao

import android.media.MediaCodec
import android.media.MediaFormat
import java.io.Closeable

class AndroidDoubaoOpusEncoder : Closeable {
    private val codec: MediaCodec = MediaCodec.createEncoderByType(OPUS_MIME_TYPE)
    private val bufferInfo = MediaCodec.BufferInfo()

    init {
        val format = MediaFormat.createAudioFormat(
            OPUS_MIME_TYPE,
            DoubaoAudioFrameChunker.SAMPLE_RATE,
            DoubaoAudioFrameChunker.CHANNELS,
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 24_000)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    fun encode(pcmFrame: ByteArray, endOfStream: Boolean = false): ByteArray? {
        val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
        if (inputIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputIndex)
            inputBuffer?.clear()
            inputBuffer?.put(pcmFrame)
            codec.queueInputBuffer(
                inputIndex,
                0,
                pcmFrame.size,
                System.nanoTime() / 1_000L,
                if (endOfStream) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0,
            )
        }

        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    val data = if (
                        outputBuffer != null &&
                        bufferInfo.size > 0 &&
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                    ) {
                        ByteArray(bufferInfo.size).also {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            outputBuffer.get(it)
                        }
                    } else {
                        null
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (data != null) return data
                }
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return null
            }
        }
    }

    override fun close() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }

    companion object {
        private const val OPUS_MIME_TYPE = "audio/opus"
        private const val TIMEOUT_US = 10_000L
    }
}
