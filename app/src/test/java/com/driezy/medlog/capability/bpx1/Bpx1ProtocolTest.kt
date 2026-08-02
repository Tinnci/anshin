package com.driezy.medlog.capability.bpx1

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Bpx1ProtocolTest {
    @Test
    fun `normalizes common MAC and bind-key formats`() {
        assertEquals("AA:BB:CC:DD:EE:FF", Bpx1Protocol.normalizeMac("aa-bb-cc-dd-ee-ff"))
        assertEquals("AA:BB:CC:DD:EE:FF", Bpx1Protocol.normalizeMac("aa bb cc dd ee ff"))
        assertTrue(Bpx1Protocol.isValidMac("aabbccddeeff"))
        assertFalse(Bpx1Protocol.isValidMac("aabbcc"))
        assertFalse(Bpx1Protocol.isValidMac("aa/bb/cc/dd/ee/ff"))
        assertFalse(Bpx1Protocol.isValidMac("aaXbbXccXddXeeXff"))
        assertEquals(
            "00112233445566778899aabbccddeeff",
            Bpx1Protocol.normalizeBindKey("00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF"),
        )
        assertEquals(16, Bpx1Protocol.decodeBindKey("00112233445566778899aabbccddeeff")?.size)
        assertNull(Bpx1Protocol.decodeBindKey("0011"))
        assertNull(Bpx1Protocol.decodeBindKey("00/11/22/33/44/55/66/77/88/99/aa/bb/cc/dd/ee/ff"))
    }

    @Test
    fun `decodes a BPX1 plaintext measurement object`() {
        val receivedAt = 1_720_000_000_000L
        val serviceData = byteArrayOf(
            0x50, 0x51, // MiBeacon v5: object + MAC + registered
            0xB8.toByte(), 0x1F, // product id 8120
            0x07, // frame counter
            0xFF.toByte(), 0xEE.toByte(), 0xDD.toByte(), 0xCC.toByte(), 0xBB.toByte(), 0xAA.toByte(),
            0x01, 0x30, 0x09, // manufacturer object id + data length
            0x00, 0x00, 0x00, 0x00, // no device time; use receive time
            0x78, 0x00, // 120 mmHg
            0x50, // 80 mmHg
            0x41, // 65 bpm
            0x03, // irregular rhythm + movement
        )

        val advertisement = Bpx1MiBeaconDecoder.decode(
            serviceData = serviceData,
            sourceMac = "AA:BB:CC:DD:EE:FF",
            bindKey = null,
            receivedAtMillis = receivedAt,
        )

        assertNotNull(advertisement)
        assertEquals(Bpx1PayloadStatus.PLAINTEXT, advertisement?.payloadStatus)
        val measurement = requireNotNull(advertisement?.measurement)
        assertEquals(120, measurement.systolic)
        assertEquals(80, measurement.diastolic)
        assertEquals(65, measurement.heartRate)
        assertEquals(receivedAt, measurement.timestampMillis)
        assertTrue(measurement.irregularHeartRhythm)
        assertTrue(measurement.movementDetected)
    }

    @Test
    fun `ignores another Xiaomi product id`() {
        val serviceData = byteArrayOf(0x50, 0x50, 0x01, 0x00, 0x01)

        assertNull(
            Bpx1MiBeaconDecoder.decode(
                serviceData = serviceData,
                sourceMac = "AA:BB:CC:DD:EE:FF",
                bindKey = null,
                receivedAtMillis = 0L,
            ),
        )
    }

    @Test
    fun `decrypts a complete encrypted BPX1 MiBeacon frame`() {
        val frame = hex(
            "5851" + // MiBeacon v5: encrypted + object + MAC + registered
                "b81f" + // product id 8120
                "07" +
                "ffeeddccbbaa" + // little-endian device MAC
                "c0a5ae729ac2c18c9198c344" + // encrypted measurement object
                "010203" + // random counter bytes
                "d21d48c0", // 4-byte authentication tag
        )

        val advertisement = Bpx1MiBeaconDecoder.decode(
            serviceData = frame,
            sourceMac = "AA:BB:CC:DD:EE:FF",
            bindKey = hex("00112233445566778899aabbccddeeff"),
            receivedAtMillis = 1_720_000_000_000L,
        )

        assertEquals(Bpx1PayloadStatus.DECRYPTED, advertisement?.payloadStatus)
        assertEquals(120, advertisement?.measurement?.systolic)
        assertEquals(80, advertisement?.measurement?.diastolic)
        assertEquals(65, advertisement?.measurement?.heartRate)
    }

    @Test
    fun `AES CCM decrypts and authenticates MiBeacon-sized data`() {
        val key = hex("00112233445566778899aabbccddeeff")
        val nonce = hex("aabbccddeeff001122334455")
        val sealed = hex("93a621004b63362c72c91bcca7")
        val ciphertext = sealed.copyOfRange(0, sealed.size - 4)
        val tag = sealed.copyOfRange(sealed.size - 4, sealed.size)

        assertArrayEquals(
            hex("010203040506070809"),
            AesCcm.decrypt(key, nonce, ciphertext, byteArrayOf(0x11), tag),
        )
        assertNull(
            AesCcm.decrypt(
                key,
                nonce,
                ciphertext,
                byteArrayOf(0x11),
                tag.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() },
            ),
        )
    }

    private fun hex(value: String): ByteArray =
        ByteArray(value.length / 2) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}
