package com.driezy.medlog.ai

import com.driezy.medlog.data.repository.CloudAiProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiApiKeyStoreTest {

    @Test
    fun `store reports availability and returns exact key`() = runTest {
        val store = InMemoryAiApiKeyStore()

        store.setApiKey(CloudAiProvider.MIMO, "mimo-secret")

        assertTrue(store.hasApiKey(CloudAiProvider.MIMO))
        assertEquals("mimo-secret", store.getApiKey(CloudAiProvider.MIMO))
    }

    @Test
    fun `blank key clears provider key`() = runTest {
        val store = InMemoryAiApiKeyStore()
        store.setApiKey(CloudAiProvider.GEMINI, "gemini-secret")

        store.setApiKey(CloudAiProvider.GEMINI, "   ")

        assertFalse(store.hasApiKey(CloudAiProvider.GEMINI))
        assertNull(store.getApiKey(CloudAiProvider.GEMINI))
    }

    @Test
    fun `clearing one provider does not remove other provider keys`() = runTest {
        val store = InMemoryAiApiKeyStore()
        store.setApiKey(CloudAiProvider.MIMO, "mimo-secret")
        store.setApiKey(CloudAiProvider.ANTHROPIC, "anthropic-secret")

        store.clearApiKey(CloudAiProvider.MIMO)

        assertFalse(store.hasApiKey(CloudAiProvider.MIMO))
        assertTrue(store.hasApiKey(CloudAiProvider.ANTHROPIC))
        assertEquals("anthropic-secret", store.getApiKey(CloudAiProvider.ANTHROPIC))
    }

    @Test
    fun `available providers flow updates when keys change`() = runTest {
        val store = InMemoryAiApiKeyStore()

        store.availableProviders.test {
            assertEquals(emptySet<CloudAiProvider>(), awaitItem())

            store.setApiKey(CloudAiProvider.MIMO, "mimo-secret")
            assertEquals(setOf(CloudAiProvider.MIMO), awaitItem())

            store.setApiKey(CloudAiProvider.GEMINI, "gemini-secret")
            assertEquals(setOf(CloudAiProvider.MIMO, CloudAiProvider.GEMINI), awaitItem())

            store.clearApiKey(CloudAiProvider.MIMO)
            assertEquals(setOf(CloudAiProvider.GEMINI), awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }
}
