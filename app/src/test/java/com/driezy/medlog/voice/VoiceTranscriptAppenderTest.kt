package com.driezy.medlog.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTranscriptAppenderTest {
    @Test
    fun `interim text replaces previous interim and final text is committed once`() {
        val appender = VoiceTranscriptAppender("原备注")

        assertEquals("原备注今天有点", appender.preview("今天有点"))
        assertEquals("原备注今天有点头痛", appender.preview("今天有点头痛"))
        assertEquals("原备注今天有点头痛", appender.commit("今天有点头痛"))
        assertEquals("原备注今天有点头痛但精神还好", appender.preview("但精神还好"))
    }

    @Test
    fun `adds separator when existing note does not end with whitespace`() {
        val appender = VoiceTranscriptAppender("原备注")

        assertEquals("原备注 新增内容", appender.commit("新增内容", insertSeparator = true))
    }
}
