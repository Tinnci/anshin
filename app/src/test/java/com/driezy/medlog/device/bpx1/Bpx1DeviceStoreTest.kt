package com.driezy.medlog.device.bpx1

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Bpx1DeviceStoreTest {
    @Test
    fun `retains a stored key only when saving the same device`() = runTest {
        val key = ByteArray(16) { it.toByte() }
        val store = InMemoryBpx1DeviceStore(
            initial = Bpx1DeviceConfiguration(macAddress = "AA:BB:CC:DD:EE:FF"),
            bindKey = key,
        )

        store.save("aa-bb-cc-dd-ee-ff", null)

        assertEquals("AA:BB:CC:DD:EE:FF", store.configuration.value.macAddress)
        assertArrayEquals(key, store.getBindKey())
    }

    @Test
    fun `refuses to associate a stored key with another device`() = runTest {
        val store = InMemoryBpx1DeviceStore(
            initial = Bpx1DeviceConfiguration(macAddress = "AA:BB:CC:DD:EE:FF"),
            bindKey = ByteArray(16),
        )

        var rejected = false
        try {
            store.save("11:22:33:44:55:66", null)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertTrue(rejected)
        assertEquals("AA:BB:CC:DD:EE:FF", store.configuration.value.macAddress)
    }
}
