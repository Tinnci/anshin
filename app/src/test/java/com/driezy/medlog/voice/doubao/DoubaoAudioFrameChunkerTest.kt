package com.driezy.medlog.voice.doubao

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubaoAudioFrameChunkerTest {
    @Test
    fun `splits pcm into 20 millisecond 16khz mono frames and keeps remainder`() {
        val chunker = DoubaoAudioFrameChunker()
        val input = ByteArray(DoubaoAudioFrameChunker.PCM_FRAME_BYTES * 2 + 17) { it.toByte() }

        val frames = chunker.offer(input)

        assertEquals(2, frames.size)
        assertArrayEquals(input.copyOfRange(0, DoubaoAudioFrameChunker.PCM_FRAME_BYTES), frames[0])
        assertArrayEquals(
            input.copyOfRange(DoubaoAudioFrameChunker.PCM_FRAME_BYTES, DoubaoAudioFrameChunker.PCM_FRAME_BYTES * 2),
            frames[1],
        )
        assertTrue(
            chunker.flushRemainder().contentEquals(
                input.copyOfRange(DoubaoAudioFrameChunker.PCM_FRAME_BYTES * 2, input.size),
            ),
        )
    }

    @Test
    fun `marks first middle and last opus packets`() {
        val frames = DoubaoAudioFrameSequencer()

        assertEquals(0, frames.sentFrameCount)
        assertEquals(DoubaoFrameState.FIRST, frames.next(ByteArray(3), timestampMs = 100).frameState)
        assertEquals(1, frames.sentFrameCount)
        assertEquals(DoubaoFrameState.MIDDLE, frames.next(ByteArray(3), timestampMs = 120).frameState)
        assertEquals(2, frames.sentFrameCount)
        assertEquals(DoubaoFrameState.LAST, frames.last(ByteArray(3), timestampMs = 140).frameState)
        assertEquals(2, frames.sentFrameCount)
    }

    @Test
    fun `detects end of voice after trailing silence`() {
        val detector = DoubaoSilenceEndDetector(
            trailingSilenceMs = 60,
            initialSilenceMs = 200,
        )

        assertTrue(detector.offer(speechFrame(amplitude = 2_000)).not())
        assertTrue(detector.offer(silenceFrame()).not())
        assertTrue(detector.offer(silenceFrame()).not())

        assertTrue(detector.offer(silenceFrame()))
    }

    @Test
    fun `detects abandoned recording after initial silence`() {
        val detector = DoubaoSilenceEndDetector(
            trailingSilenceMs = 60,
            initialSilenceMs = 60,
        )

        assertTrue(detector.offer(silenceFrame()).not())
        assertTrue(detector.offer(silenceFrame()).not())

        assertTrue(detector.offer(silenceFrame()))
    }

    private fun silenceFrame(): ByteArray = ByteArray(DoubaoAudioFrameChunker.PCM_FRAME_BYTES)

    private fun speechFrame(amplitude: Short): ByteArray {
        val frame = ByteArray(DoubaoAudioFrameChunker.PCM_FRAME_BYTES)
        var index = 0
        while (index < frame.size) {
            frame[index] = (amplitude.toInt() and 0xff).toByte()
            frame[index + 1] = ((amplitude.toInt() shr 8) and 0xff).toByte()
            index += 2
        }
        return frame
    }
}
