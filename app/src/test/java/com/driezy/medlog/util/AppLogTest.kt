package com.driezy.medlog.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLogTest {

    @Test
    fun normalizeTag_keepsShortTagsUnchanged() {
        assertEquals("HomeVM", AppLog.normalizeTag("HomeVM"))
        assertEquals("12345678901234567890123", AppLog.normalizeTag("12345678901234567890123"))
    }

    @Test
    fun normalizeTag_truncatesTagsExceedingMaxLength() {
        assertEquals("OcrFrameInputPreprocess", AppLog.normalizeTag("OcrFrameInputPreprocessor"))
    }

    @Test
    fun logMethods_doNotThrowInJvmUnitTests() {
        AppLog.v("AppLogTest") { "verbose" }
        AppLog.d("AppLogTest") { "debug" }
        AppLog.i("AppLogTest") { "info" }
        AppLog.w("AppLogTest", "warning", IllegalStateException("test"))
        AppLog.e("AppLogTest", "error", IllegalArgumentException("test"))
    }
}
