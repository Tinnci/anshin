package com.driezy.medlog.voice.doubao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubaoAsrProtocolTest {
    @Test
    fun `builds start session request with doubao ime audio configuration`() {
        val bytes = DoubaoAsrProtocol.buildStartSession(
            requestId = "request-1",
            token = "app-key",
            deviceId = "device-1",
        )
        val fields = decodeProtoFields(bytes)

        assertEquals("app-key", fields[2]?.utf8)
        assertEquals("ASR", fields[3]?.utf8)
        assertEquals("StartSession", fields[5]?.utf8)
        assertEquals("request-1", fields[8]?.utf8)

        val payload = fields[6]?.utf8.orEmpty()
        assertTrue(payload.contains("\"format\":\"speech_opus\""))
        assertTrue(payload.contains("\"sample_rate\":16000"))
        assertTrue(payload.contains("\"enable_asr_threepass\":true"))
        assertTrue(payload.contains("\"did\":\"device-1\""))
    }

    @Test
    fun `builds audio task request without token and with frame state`() {
        val bytes = DoubaoAsrProtocol.buildTaskRequest(
            requestId = "request-1",
            packet = DoubaoAudioPacket(ByteArray(4) { (it + 1).toByte() }, DoubaoFrameState.FIRST, 1234L),
        )
        val fields = decodeProtoFields(bytes)

        assertFalse("TaskRequest should not send token again.", fields.containsKey(2))
        assertEquals("ASR", fields[3]?.utf8)
        assertEquals("TaskRequest", fields[5]?.utf8)
        assertEquals("{\"extra\":{},\"timestamp_ms\":1234}", fields[6]?.utf8)
        assertTrue(fields[7]?.bytes.contentEquals(byteArrayOf(1, 2, 3, 4)))
        assertEquals(1L, fields[9]?.varint)
    }

    @Test
    fun `parses final recognition result`() {
        val responseBytes = encodeResponse(
            messageType = "Result",
            resultJson = """
                {"results":[{"text":"今天感觉还可以。","is_interim":false,"is_vad_finished":true}],"extra":{"packet_number":7}}
            """.trimIndent(),
        )

        val response = DoubaoAsrProtocol.parseResponse(responseBytes)

        assertEquals(DoubaoAsrResponseType.FINAL_RESULT, response.type)
        assertEquals("今天感觉还可以。", response.text)
        assertTrue(response.isFinal)
        assertTrue(response.vadFinished)
    }

    @Test
    fun `parses failed session as error`() {
        val responseBytes = encodeResponse(messageType = "SessionFailed", statusMessage = "bad token")

        val response = DoubaoAsrProtocol.parseResponse(responseBytes)

        assertEquals(DoubaoAsrResponseType.ERROR, response.type)
        assertTrue(response.errorMessage.contains("bad token"))
    }

    @Test
    fun `failed response preserves diagnostic context`() {
        val responseBytes = encodeResponse(
            messageType = "SessionFailed",
            statusMessage = "bad token",
            resultJson = """{"code":401,"message":"expired"}""",
        )

        val response = DoubaoAsrProtocol.parseResponse(responseBytes)

        assertEquals(DoubaoAsrResponseType.ERROR, response.type)
        assertTrue(response.errorMessage.contains("SessionFailed"))
        assertTrue(response.errorMessage.contains("bad token"))
        assertTrue(response.errorMessage.contains("expired"))
    }

    private data class FieldValue(
        val utf8: String? = null,
        val bytes: ByteArray = byteArrayOf(),
        val varint: Long = 0L,
    )

    private fun decodeProtoFields(bytes: ByteArray): Map<Int, FieldValue> {
        val result = mutableMapOf<Int, FieldValue>()
        var index = 0
        while (index < bytes.size) {
            val tag = readVarint(bytes, index)
            index = tag.nextIndex
            val fieldNumber = (tag.value ushr 3).toInt()
            when ((tag.value and 0x07).toInt()) {
                0 -> {
                    val value = readVarint(bytes, index)
                    index = value.nextIndex
                    result[fieldNumber] = FieldValue(varint = value.value)
                }
                2 -> {
                    val size = readVarint(bytes, index)
                    index = size.nextIndex
                    val data = bytes.copyOfRange(index, index + size.value.toInt())
                    index += size.value.toInt()
                    result[fieldNumber] = FieldValue(utf8 = data.decodeToString(), bytes = data)
                }
                else -> error("Unsupported wire type")
            }
        }
        return result
    }

    private data class VarintResult(val value: Long, val nextIndex: Int)

    private fun readVarint(bytes: ByteArray, startIndex: Int): VarintResult {
        var value = 0L
        var shift = 0
        var index = startIndex
        while (index < bytes.size) {
            val byte = bytes[index++].toInt() and 0xff
            value = value or ((byte and 0x7f).toLong() shl shift)
            if ((byte and 0x80) == 0) return VarintResult(value, index)
            shift += 7
        }
        error("Truncated varint")
    }

    private fun encodeResponse(
        messageType: String,
        statusMessage: String = "",
        resultJson: String = "",
    ): ByteArray {
        val writer = ProtoWriter()
        writer.writeString(4, messageType)
        writer.writeInt32(5, if (messageType.endsWith("Failed")) 500 else 0)
        writer.writeString(6, statusMessage)
        writer.writeString(7, resultJson)
        return writer.toByteArray()
    }
}
