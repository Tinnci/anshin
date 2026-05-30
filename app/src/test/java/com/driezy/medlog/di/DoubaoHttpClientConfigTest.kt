package com.driezy.medlog.di

import okhttp3.Protocol
import org.junit.Assert.assertEquals
import org.junit.Test

class DoubaoHttpClientConfigTest {
    @Test
    fun `okhttp client uses http 1_1 for doubao compatibility`() {
        val client = DatabaseModule.provideOkHttpClient()

        assertEquals(listOf(Protocol.HTTP_1_1), client.protocols)
        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(15_000, client.readTimeoutMillis)
        assertEquals(15_000, client.writeTimeoutMillis)
    }
}
