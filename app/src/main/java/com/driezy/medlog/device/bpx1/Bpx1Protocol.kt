package com.driezy.medlog.device.bpx1

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Public MiOT identity for the iHealth BPX1 blood-pressure monitor. */
object Bpx1Protocol {
    const val MODEL = "ihealth.bpm.bpx1"
    const val PRODUCT_ID = 8120
    const val MIBEACON_SERVICE_UUID = "0000fe95-0000-1000-8000-00805f9b34fb"
    const val STANDARD_BLOOD_PRESSURE_SERVICE_UUID = "00001810-0000-1000-8000-00805f9b34fb"
    const val BIND_KEY_HEX_LENGTH = 32

    private val macRegex = Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")
    private val supportedSeparatorRegex = Regex("[:\\-\\s]")
    private val unsupportedInputRegex = Regex("[^0-9A-Fa-f:\\-\\s]")

    fun normalizeMac(raw: String): String {
        val trimmed = raw.trim()
        if (unsupportedInputRegex.containsMatchIn(trimmed)) return trimmed.uppercase()
        val compact = trimmed.replace(supportedSeparatorRegex, "").uppercase()
        if (compact.length != 12) return trimmed.uppercase()
        return compact.chunked(2).joinToString(":")
    }

    fun isValidMac(raw: String): Boolean = macRegex.matches(normalizeMac(raw))

    fun normalizeBindKey(raw: String): String = raw.trim().let { trimmed ->
        if (unsupportedInputRegex.containsMatchIn(trimmed)) {
            trimmed.lowercase()
        } else {
            trimmed.replace(supportedSeparatorRegex, "").lowercase()
        }
    }

    fun decodeBindKey(raw: String): ByteArray? {
        val normalized = normalizeBindKey(raw)
        if (normalized.length != BIND_KEY_HEX_LENGTH) return null
        return runCatching {
            ByteArray(normalized.length / 2) { index ->
                normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }
}

data class Bpx1Measurement(
    val timestampMillis: Long,
    val systolic: Int,
    val diastolic: Int,
    val heartRate: Int,
    val flags: Int,
    val packetCounter: Int,
    val objectId: Int,
) {
    val irregularHeartRhythm: Boolean get() = flags and 0x01 != 0
    val movementDetected: Boolean get() = flags and 0x02 != 0
    val atrialFibrillationFlag: Boolean get() = flags and 0x04 != 0
    val cuffOrUserGroupFlag: Boolean get() = flags and 0x08 != 0
}

enum class Bpx1PayloadStatus {
    NONE,
    PLAINTEXT,
    DECRYPTED,
    KEY_REQUIRED,
    KEY_REJECTED,
    MALFORMED,
}

data class Bpx1Advertisement(
    val productId: Int,
    val packetCounter: Int,
    val registered: Boolean,
    val encrypted: Boolean,
    val payloadStatus: Bpx1PayloadStatus,
    val measurement: Bpx1Measurement? = null,
)

/**
 * Parses Xiaomi FE95 MiBeacon frames and decrypts v4/v5 payloads with the 16-byte bind key.
 *
 * The BPX1's public MiOT spec defines the measurement payload as current time, systolic,
 * diastolic, heart rate and flags. The outer manufacturer object id is not published, so this
 * decoder accepts a sane 9-byte measurement object only after the product id is verified.
 */
object Bpx1MiBeaconDecoder {
    private const val MIN_HEADER_SIZE = 5
    private const val ENCRYPTED_TRAILER_SIZE = 7
    private const val MEASUREMENT_SIZE = 9
    private const val FRAME_VERSION_SHIFT = 12
    private const val FRAME_MAC_INCLUDED = 1 shl 4
    private const val FRAME_CAPABILITY_INCLUDED = 1 shl 5
    private const val FRAME_OBJECT_INCLUDED = 1 shl 6
    private const val FRAME_REGISTERED = 1 shl 8
    private const val FRAME_ENCRYPTED = 1 shl 3
    private const val CAPABILITY_IO_INCLUDED = 1 shl 5
    private val epoch2000Millis = LocalDateTime.of(2000, 1, 1, 0, 0)
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()

    fun decode(
        serviceData: ByteArray,
        sourceMac: String,
        bindKey: ByteArray?,
        receivedAtMillis: Long = System.currentTimeMillis(),
    ): Bpx1Advertisement? {
        if (serviceData.size < MIN_HEADER_SIZE) return null
        val frameControl = serviceData.u16le(0)
        val productId = serviceData.u16le(2)
        if (productId != Bpx1Protocol.PRODUCT_ID) return null

        val version = frameControl ushr FRAME_VERSION_SHIFT
        val counter = serviceData[4].toInt() and 0xFF
        val registered = frameControl and FRAME_REGISTERED != 0
        val encrypted = frameControl and FRAME_ENCRYPTED != 0
        if (version < 2) {
            return Bpx1Advertisement(
                productId = productId,
                packetCounter = counter,
                registered = registered,
                encrypted = encrypted,
                payloadStatus = Bpx1PayloadStatus.MALFORMED,
            )
        }

        var cursor = MIN_HEADER_SIZE
        val canonicalMac = if (frameControl and FRAME_MAC_INCLUDED != 0) {
            if (serviceData.size < cursor + 6) return malformed(productId, counter, registered, encrypted)
            val advertisedMac = serviceData.copyOfRange(cursor, cursor + 6)
            cursor += 6
            advertisedMac.reversedArray().toMac()
        } else {
            Bpx1Protocol.normalizeMac(sourceMac)
        }

        if (!Bpx1Protocol.isValidMac(canonicalMac)) {
            return malformed(productId, counter, registered, encrypted)
        }
        if (frameControl and FRAME_MAC_INCLUDED != 0 &&
            !canonicalMac.equals(Bpx1Protocol.normalizeMac(sourceMac), ignoreCase = true)
        ) {
            return malformed(productId, counter, registered, encrypted)
        }

        if (frameControl and FRAME_CAPABILITY_INCLUDED != 0) {
            if (serviceData.size <= cursor) return malformed(productId, counter, registered, encrypted)
            val capability = serviceData[cursor].toInt() and 0xFF
            cursor += 1
            if (capability and CAPABILITY_IO_INCLUDED != 0) cursor += 1
            if (serviceData.size < cursor) return malformed(productId, counter, registered, encrypted)
        }

        if (frameControl and FRAME_OBJECT_INCLUDED == 0) {
            return Bpx1Advertisement(
                productId = productId,
                packetCounter = counter,
                registered = registered,
                encrypted = encrypted,
                payloadStatus = Bpx1PayloadStatus.NONE,
            )
        }

        val payload: ByteArray
        val payloadStatus: Bpx1PayloadStatus
        if (encrypted) {
            if (bindKey == null) {
                return Bpx1Advertisement(
                    productId = productId,
                    packetCounter = counter,
                    registered = registered,
                    encrypted = true,
                    payloadStatus = Bpx1PayloadStatus.KEY_REQUIRED,
                )
            }
            if (bindKey.size != 16 || serviceData.size < cursor + ENCRYPTED_TRAILER_SIZE + 1) {
                return malformed(productId, counter, registered, encrypted)
            }
            val encryptedEnd = serviceData.size - ENCRYPTED_TRAILER_SIZE
            val ciphertext = serviceData.copyOfRange(cursor, encryptedEnd)
            val random = serviceData.copyOfRange(serviceData.size - 7, serviceData.size - 4)
            val tag = serviceData.copyOfRange(serviceData.size - 4, serviceData.size)
            val nonce = canonicalMac.toMacBytes().reversedArray() +
                serviceData.copyOfRange(2, 5) + random
            payload = AesCcm.decrypt(
                key = bindKey,
                nonce = nonce,
                ciphertext = ciphertext,
                associatedData = byteArrayOf(0x11),
                tag = tag,
            ) ?: return Bpx1Advertisement(
                productId = productId,
                packetCounter = counter,
                registered = registered,
                encrypted = true,
                payloadStatus = Bpx1PayloadStatus.KEY_REJECTED,
            )
            payloadStatus = Bpx1PayloadStatus.DECRYPTED
        } else {
            if (serviceData.size < cursor + 3) return malformed(productId, counter, registered, encrypted)
            payload = serviceData.copyOfRange(cursor, serviceData.size)
            payloadStatus = Bpx1PayloadStatus.PLAINTEXT
        }

        val measurement = parseMeasurementObjects(payload, counter, receivedAtMillis)
        return Bpx1Advertisement(
            productId = productId,
            packetCounter = counter,
            registered = registered,
            encrypted = encrypted,
            payloadStatus = payloadStatus,
            measurement = measurement,
        )
    }

    private fun parseMeasurementObjects(
        payload: ByteArray,
        packetCounter: Int,
        receivedAtMillis: Long,
    ): Bpx1Measurement? {
        var cursor = 0
        while (payload.size >= cursor + 3) {
            val objectId = payload.u16le(cursor)
            val length = payload[cursor + 2].toInt() and 0xFF
            val valueStart = cursor + 3
            val next = valueStart + length
            if (next > payload.size) return null
            if (length == MEASUREMENT_SIZE) {
                val rawTime = payload.u32le(valueStart)
                val systolic = payload.u16le(valueStart + 4)
                val diastolic = payload[valueStart + 6].toInt() and 0xFF
                val heartRate = payload[valueStart + 7].toInt() and 0xFF
                val flags = payload[valueStart + 8].toInt() and 0xFF
                if (systolic in 40..260 &&
                    diastolic in 20..200 &&
                    heartRate in 20..250 &&
                    systolic > diastolic
                ) {
                    return Bpx1Measurement(
                        timestampMillis = resolveTimestamp(rawTime, receivedAtMillis),
                        systolic = systolic,
                        diastolic = diastolic,
                        heartRate = heartRate,
                        flags = flags,
                        packetCounter = packetCounter,
                        objectId = objectId,
                    )
                }
            }
            cursor = next
        }
        return null
    }

    private fun resolveTimestamp(rawSeconds: Long, receivedAtMillis: Long): Long {
        if (rawSeconds == 0L) return receivedAtMillis
        val unixCandidate = rawSeconds * 1_000L
        val epoch2000Candidate = epoch2000Millis + rawSeconds * 1_000L
        val candidates = listOf(unixCandidate, epoch2000Candidate)
        val closest = candidates.minBy { candidate -> kotlin.math.abs(candidate - receivedAtMillis) }
        val tenYearsMillis = 10L * 365 * 24 * 60 * 60 * 1_000
        return if (kotlin.math.abs(closest - receivedAtMillis) <= tenYearsMillis) closest else receivedAtMillis
    }

    private fun malformed(productId: Int, counter: Int, registered: Boolean, encrypted: Boolean) = Bpx1Advertisement(
        productId = productId,
        packetCounter = counter,
        registered = registered,
        encrypted = encrypted,
        payloadStatus = Bpx1PayloadStatus.MALFORMED,
    )
}

/** Minimal AES-CCM implementation for MiBeacon's 4-byte authentication tag. */
internal object AesCcm {
    @Suppress("GetInstance") // ECB is used only as the AES block primitive required by CCM.
    fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        associatedData: ByteArray,
        tag: ByteArray,
    ): ByteArray? {
        if (key.size !in setOf(16, 24, 32) || nonce.size !in 7..13 || tag.size !in 4..16 || tag.size % 2 != 0) {
            return null
        }
        val lengthSize = 15 - nonce.size
        if (!fitsInBytes(ciphertext.size.toLong(), lengthSize)) return null
        val aes = Cipher.getInstance("AES/ECB/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        }
        val plaintext = ByteArray(ciphertext.size)
        var offset = 0
        var counter = 1L
        while (offset < ciphertext.size) {
            val stream = aes.doFinal(counterBlock(nonce, lengthSize, counter))
            val count = minOf(16, ciphertext.size - offset)
            for (index in 0 until count) {
                plaintext[offset + index] = (ciphertext[offset + index].toInt() xor stream[index].toInt()).toByte()
            }
            offset += count
            counter += 1
        }

        val mac = authenticationMac(
            aes = aes,
            nonce = nonce,
            lengthSize = lengthSize,
            plaintext = plaintext,
            associatedData = associatedData,
            tagLength = tag.size,
        )
        val s0 = aes.doFinal(counterBlock(nonce, lengthSize, 0))
        var mismatch = 0
        for (index in tag.indices) {
            mismatch = mismatch or ((mac[index].toInt() xor s0[index].toInt()) xor tag[index].toInt())
        }
        return if (mismatch == 0) plaintext else null
    }

    private fun authenticationMac(
        aes: Cipher,
        nonce: ByteArray,
        lengthSize: Int,
        plaintext: ByteArray,
        associatedData: ByteArray,
        tagLength: Int,
    ): ByteArray {
        val flags = (if (associatedData.isNotEmpty()) 0x40 else 0) or
            (((tagLength - 2) / 2) shl 3) or
            (lengthSize - 1)
        val b0 = ByteArray(16)
        b0[0] = flags.toByte()
        nonce.copyInto(b0, destinationOffset = 1)
        writeBigEndian(plaintext.size.toLong(), b0, 16 - lengthSize, lengthSize)

        var state = aes.doFinal(b0)
        if (associatedData.isNotEmpty()) {
            require(associatedData.size < 0xFF00) { "MiBeacon AAD is unexpectedly large." }
            val encoded = ByteArray(roundToBlock(associatedData.size + 2))
            encoded[0] = (associatedData.size ushr 8).toByte()
            encoded[1] = associatedData.size.toByte()
            associatedData.copyInto(encoded, destinationOffset = 2)
            state = macBlocks(aes, state, encoded)
        }
        if (plaintext.isNotEmpty()) {
            state = macBlocks(aes, state, plaintext.copyOf(roundToBlock(plaintext.size)))
        }
        return state
    }

    private fun macBlocks(aes: Cipher, initial: ByteArray, data: ByteArray): ByteArray {
        var state = initial
        var offset = 0
        while (offset < data.size) {
            val block = ByteArray(16) { index ->
                (state[index].toInt() xor data[offset + index].toInt()).toByte()
            }
            state = aes.doFinal(block)
            offset += 16
        }
        return state
    }

    private fun counterBlock(nonce: ByteArray, lengthSize: Int, counter: Long): ByteArray =
        ByteArray(16).also { block ->
            block[0] = (lengthSize - 1).toByte()
            nonce.copyInto(block, destinationOffset = 1)
            writeBigEndian(counter, block, 16 - lengthSize, lengthSize)
        }

    private fun writeBigEndian(value: Long, target: ByteArray, offset: Int, length: Int) {
        for (index in 0 until length) {
            target[offset + length - 1 - index] = (value ushr (index * 8)).toByte()
        }
    }

    private fun fitsInBytes(value: Long, bytes: Int): Boolean = bytes >= 8 || value < (1L shl (bytes * 8))

    private fun roundToBlock(size: Int): Int = ((size + 15) / 16) * 16
}

private fun ByteArray.u16le(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.u32le(offset: Int): Long =
    ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFF_FFFFL

private fun ByteArray.toMac(): String = joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xFF) }

private fun String.toMacBytes(): ByteArray = split(":").map { it.toInt(16).toByte() }.toByteArray()
