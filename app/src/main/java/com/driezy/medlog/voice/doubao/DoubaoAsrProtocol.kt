package com.driezy.medlog.voice.doubao

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object DoubaoAsrProtocol {
    private const val SERVICE_NAME = "ASR"
    private val json = Json { encodeDefaults = true }

    fun buildStartTask(requestId: String, token: String): ByteArray =
        ProtoWriter().apply {
            writeString(2, token)
            writeString(3, SERVICE_NAME)
            writeString(5, "StartTask")
            writeString(8, requestId)
        }.toByteArray()

    fun buildStartSession(requestId: String, token: String, deviceId: String): ByteArray {
        val payload = json.encodeToString(DoubaoSessionConfig.serializer(), DoubaoSessionConfig(deviceId = deviceId))
        return ProtoWriter().apply {
            writeString(2, token)
            writeString(3, SERVICE_NAME)
            writeString(5, "StartSession")
            writeString(6, payload)
            writeString(8, requestId)
        }.toByteArray()
    }

    fun buildTaskRequest(requestId: String, packet: DoubaoAudioPacket): ByteArray =
        ProtoWriter().apply {
            writeString(3, SERVICE_NAME)
            writeString(5, "TaskRequest")
            writeString(6, "{\"extra\":{},\"timestamp_ms\":${packet.timestampMs}}")
            writeBytes(7, packet.data)
            writeString(8, requestId)
            writeInt32(9, packet.frameState.protoValue)
        }.toByteArray()

    fun buildFinishSession(requestId: String, token: String): ByteArray =
        ProtoWriter().apply {
            writeString(2, token)
            writeString(3, SERVICE_NAME)
            writeString(5, "FinishSession")
            writeString(8, requestId)
        }.toByteArray()

    fun parseResponse(bytes: ByteArray): DoubaoAsrResponse {
        val fields = ProtoReader(bytes).readFields()
        val messageType = fields[4]?.stringValue.orEmpty()
        val statusMessage = fields[6]?.stringValue.orEmpty()
        val resultJson = fields[7]?.stringValue.orEmpty()

        return when (messageType) {
            "TaskStarted" -> DoubaoAsrResponse(type = DoubaoAsrResponseType.TASK_STARTED)
            "SessionStarted" -> DoubaoAsrResponse(type = DoubaoAsrResponseType.SESSION_STARTED)
            "SessionFinished" -> DoubaoAsrResponse(type = DoubaoAsrResponseType.SESSION_FINISHED)
            "TaskFailed", "SessionFailed" -> DoubaoAsrResponse(
                type = DoubaoAsrResponseType.ERROR,
                errorMessage = diagnosticMessage(
                    messageType = messageType,
                    statusMessage = statusMessage,
                    resultJson = resultJson,
                ),
            )
            else -> parseResultJson(resultJson)
        }
    }

    private fun parseResultJson(resultJson: String): DoubaoAsrResponse {
        if (resultJson.isBlank()) return DoubaoAsrResponse(type = DoubaoAsrResponseType.UNKNOWN)

        val json = runCatching { Json.parseToJsonElement(resultJson).jsonObject }.getOrElse { error ->
            return DoubaoAsrResponse(
                type = DoubaoAsrResponseType.ERROR,
                errorMessage = diagnosticMessage(
                    messageType = "Result",
                    statusMessage = "Invalid result JSON: ${error.message.orEmpty()}",
                    resultJson = resultJson,
                ),
            )
        }
        val extra = json["extra"]?.jsonObjectOrNull()
        val results = json["results"]?.jsonArrayOrNull()

        if (results == null) {
            return DoubaoAsrResponse(
                type = DoubaoAsrResponseType.HEARTBEAT,
                packetNumber = extra?.get("packet_number")?.jsonPrimitive?.intOrNull ?: -1,
            )
        }

        if (extra?.get("vad_start")?.jsonPrimitive?.booleanOrNull == true) {
            return DoubaoAsrResponse(type = DoubaoAsrResponseType.VAD_START, vadStart = true)
        }

        var text = ""
        var isInterim = true
        var vadFinished = false
        var nonstreamResult = false
        results.forEach { item ->
            val obj = item.jsonObjectOrNull() ?: return@forEach
            obj["text"]?.jsonPrimitive?.contentOrNull?.let { text = it }
            if (obj["is_interim"]?.jsonPrimitive?.booleanOrNull == false) isInterim = false
            if (obj["is_vad_finished"]?.jsonPrimitive?.booleanOrNull == true) vadFinished = true
            if (
                obj["extra"]?.jsonObjectOrNull()
                    ?.get("nonstream_result")
                    ?.jsonPrimitive
                    ?.booleanOrNull == true
            ) {
                nonstreamResult = true
            }
        }

        val isFinal = nonstreamResult || (!isInterim && vadFinished)
        return DoubaoAsrResponse(
            type = if (isFinal) DoubaoAsrResponseType.FINAL_RESULT else DoubaoAsrResponseType.INTERIM_RESULT,
            text = text,
            isFinal = isFinal,
            vadFinished = vadFinished,
        )
    }

    private fun diagnosticMessage(
        messageType: String,
        statusMessage: String,
        resultJson: String,
    ): String = buildList {
        add("messageType=${messageType.ifBlank { "unknown" }}")
        statusMessage.takeIf { it.isNotBlank() }?.let { add("status=$it") }
        resultJson.takeIf { it.isNotBlank() }?.let { add("result=$it") }
    }.joinToString(separator = "; ")
}

enum class DoubaoAsrResponseType {
    TASK_STARTED,
    SESSION_STARTED,
    SESSION_FINISHED,
    VAD_START,
    INTERIM_RESULT,
    FINAL_RESULT,
    HEARTBEAT,
    ERROR,
    UNKNOWN,
}

data class DoubaoAsrResponse(
    val type: DoubaoAsrResponseType,
    val text: String = "",
    val isFinal: Boolean = false,
    val vadStart: Boolean = false,
    val vadFinished: Boolean = false,
    val packetNumber: Int = -1,
    val errorMessage: String = "",
)

@Serializable
private data class DoubaoSessionConfig(
    @SerialName("audio_info") val audioInfo: DoubaoAudioInfo = DoubaoAudioInfo(),
    @SerialName("enable_punctuation") val enablePunctuation: Boolean = true,
    @SerialName("enable_speech_rejection") val enableSpeechRejection: Boolean = false,
    val extra: DoubaoSessionExtra,
) {
    constructor(deviceId: String) : this(extra = DoubaoSessionExtra(did = deviceId))
}

@Serializable
private data class DoubaoAudioInfo(
    val channel: Int = 1,
    val format: String = "speech_opus",
    @SerialName("sample_rate") val sampleRate: Int = 16_000,
)

@Serializable
private data class DoubaoSessionExtra(
    @SerialName("app_name") val appName: String = "com.android.chrome",
    @SerialName("cell_compress_rate") val cellCompressRate: Int = 8,
    val did: String,
    @SerialName("enable_asr_threepass") val enableAsrThreepass: Boolean = true,
    @SerialName("enable_asr_twopass") val enableAsrTwopass: Boolean = true,
    @SerialName("input_mode") val inputMode: String = "tool",
)

class ProtoWriter {
    private val bytes = ArrayList<Byte>()

    fun writeString(fieldNumber: Int, value: String) {
        if (value.isEmpty()) return
        writeBytes(fieldNumber, value.encodeToByteArray())
    }

    fun writeBytes(fieldNumber: Int, value: ByteArray) {
        if (value.isEmpty()) return
        writeVarint(((fieldNumber shl 3) or 2).toLong())
        writeVarint(value.size.toLong())
        bytes.addAll(value.toList())
    }

    fun writeInt32(fieldNumber: Int, value: Int) {
        if (value == 0) return
        writeVarint(((fieldNumber shl 3) or 0).toLong())
        writeVarint(value.toLong())
    }

    fun toByteArray(): ByteArray = bytes.toByteArray()

    private fun writeVarint(value: Long) {
        var remaining = value
        while (remaining >= 0x80) {
            bytes += (((remaining and 0x7f) or 0x80).toInt()).toByte()
            remaining = remaining ushr 7
        }
        bytes += remaining.toByte()
    }
}

private data class ProtoField(val bytesValue: ByteArray = byteArrayOf(), val varintValue: Long = 0L) {
    val stringValue: String get() = bytesValue.decodeToString()
}

private class ProtoReader(private val bytes: ByteArray) {
    fun readFields(): Map<Int, ProtoField> {
        val fields = mutableMapOf<Int, ProtoField>()
        var index = 0
        while (index < bytes.size) {
            val tag = readVarint(index)
            index = tag.nextIndex
            val fieldNumber = (tag.value ushr 3).toInt()
            when ((tag.value and 0x07).toInt()) {
                0 -> {
                    val value = readVarint(index)
                    index = value.nextIndex
                    fields[fieldNumber] = ProtoField(varintValue = value.value)
                }
                2 -> {
                    val size = readVarint(index)
                    index = size.nextIndex
                    val value = bytes.copyOfRange(index, index + size.value.toInt())
                    index += size.value.toInt()
                    fields[fieldNumber] = ProtoField(bytesValue = value)
                }
                else -> return fields
            }
        }
        return fields
    }

    private fun readVarint(startIndex: Int): VarintResult {
        var value = 0L
        var shift = 0
        var index = startIndex
        while (index < bytes.size) {
            val byte = bytes[index++].toInt() and 0xff
            value = value or ((byte and 0x7f).toLong() shl shift)
            if ((byte and 0x80) == 0) return VarintResult(value, index)
            shift += 7
        }
        return VarintResult(value, index)
    }

    private data class VarintResult(val value: Long, val nextIndex: Int)
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
private fun JsonElement.jsonArrayOrNull() = runCatching { jsonArray }.getOrNull()
