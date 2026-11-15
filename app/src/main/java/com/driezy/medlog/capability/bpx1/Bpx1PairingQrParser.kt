package com.driezy.medlog.capability.bpx1

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class Bpx1PairingQrPayload(val macAddress: String?, val bindKeyHex: String)

/**
 * Parses BPX1 pairing QR payloads from:
 * 1. Deep-link / URL query parameters (`anshin://bpx1?mac=...&key=...`)
 * 2. JSON objects (`{"mac":"...","key":"..."}` / `bindKey` / `ble_key`)
 * 3. Raw Xiaomi token-extractor text (`MAC: ...` and `BLE KEY: ...`)
 * 4. Bare 32-char hex BindKey strings
 */
object Bpx1PairingQrParser {
    private val macRegex = Regex("""\b(?:[0-9A-Fa-f]{2}[:\-]){5}[0-9A-Fa-f]{2}\b""")
    private val hex32Regex = Regex("""[0-9a-fA-F]{32}""")

    fun parse(raw: String): Bpx1PairingQrPayload? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        parseUri(trimmed)?.let { return it }
        parseJson(trimmed)?.let { return it }

        val extractedMac = macRegex.find(trimmed)?.value?.let(Bpx1Protocol::normalizeMac)
            ?.takeIf(Bpx1Protocol::isValidMac)
        val extractedKey = extractBindKeyHex(trimmed) ?: return null
        return Bpx1PairingQrPayload(macAddress = extractedMac, bindKeyHex = extractedKey)
    }

    fun extractBindKeyHex(rawText: String): String? {
        val normalized = rawText.replace(Regex("[:\\-\\s]"), "")
        return hex32Regex.find(normalized)?.value?.lowercase()
    }

    private fun parseUri(raw: String): Bpx1PairingQrPayload? {
        if (!raw.contains("://")) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val query = uri.rawQuery ?: return null
        val params = query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name()).lowercase()
            val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name())
            key to value
        }.toMap()

        val rawKey = params["key"] ?: params["bindkey"] ?: params["bind_key"] ?: params["ble_key"] ?: return null
        val bindKeyHex = extractBindKeyHex(rawKey) ?: return null
        val mac = params["mac"]?.let(Bpx1Protocol::normalizeMac)?.takeIf(Bpx1Protocol::isValidMac)
        return Bpx1PairingQrPayload(macAddress = mac, bindKeyHex = bindKeyHex)
    }

    private fun parseJson(raw: String): Bpx1PairingQrPayload? {
        if (!raw.startsWith("{")) return null
        val obj = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val rawKey = obj["key"]?.jsonPrimitive?.contentOrNull
            ?: obj["bindKey"]?.jsonPrimitive?.contentOrNull
            ?: obj["bind_key"]?.jsonPrimitive?.contentOrNull
            ?: obj["ble_key"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val bindKeyHex = extractBindKeyHex(rawKey) ?: return null
        val rawMac = obj["mac"]?.jsonPrimitive?.contentOrNull
            ?: obj["macAddress"]?.jsonPrimitive?.contentOrNull
        val mac = rawMac?.let(Bpx1Protocol::normalizeMac)?.takeIf(Bpx1Protocol::isValidMac)
        return Bpx1PairingQrPayload(macAddress = mac, bindKeyHex = bindKeyHex)
    }
}
