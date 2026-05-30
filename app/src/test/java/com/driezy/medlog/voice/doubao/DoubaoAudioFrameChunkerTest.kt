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
        assertTrue(chunker.flushRemainder().contentEquals(input.copyOfRange(DoubaoAudioFrameChunker.PCM_FRAME_BYTES * 2, input.size)))
    }

    @Test
    fun `marks first middle and last opus packets`() {
        val frames = DoubaoAudioFrameSequencer()

        assertEquals(DoubaoFrameState.FIRST, frames.next(ByteArray(3), timestampMs = 100).frameState)
        assertEquals(DoubaoFrameState.MIDDLE, frames.next(ByteArray(3), timestampMs = 120).frameState)
        assertEquals(DoubaoFrameState.LAST, frames.last(ByteArray(3), timestampMs = 140).frameState)
    }
}
