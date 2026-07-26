package com.driezy.medlog.domain.health

import com.driezy.medlog.ai.AiChatClient
import com.driezy.medlog.ai.AiChatContentPart
import com.driezy.medlog.ai.AiChatMessage
import com.driezy.medlog.ai.AiChatRequest
import com.driezy.medlog.ai.AiStructuredResponse
import com.driezy.medlog.ai.AiStructuredResponseErrorKind
import com.driezy.medlog.ai.AiStructuredResponseStatus
import com.driezy.medlog.ai.AiTokenUsage
import com.driezy.medlog.data.model.HealthType
import com.driezy.medlog.data.model.OcrParseResult
import com.driezy.medlog.data.model.ParsedHealthMetric
import com.driezy.medlog.ui.ocr.HealthMetricParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject

data class HealthImageAnalysisRequest(val imageBytes: ByteArray, val mimeType: String, val locale: String = "zh-CN") {
    init {
        require(mimeType.startsWith("image/")) {
            "Health image analysis requires an image/* MIME type."
        }
    }
}

class HealthImageAnalyzer(private val aiChatClient: AiChatClient) {
    suspend fun analyze(request: HealthImageAnalysisRequest): OcrParseResult =
        analyzeStructured(request).parsed ?: emptyOcrParseResult()

    suspend fun analyzeStructured(request: HealthImageAnalysisRequest): AiStructuredResponse<OcrParseResult> {
        val response = aiChatClient.generate(request.toAiRequest())
        return HealthImageAnalysisParser.parseStructured(
            text = response.text,
            finishReason = response.finishReason,
            usage = response.usage,
        )
    }

    private fun HealthImageAnalysisRequest.toAiRequest(): AiChatRequest = AiChatRequest(
        messages = listOf(
            AiChatMessage.system(
                "你是健康设备屏幕识别器，只提取图中的读数，不做诊断或医疗建议。",
            ),
            AiChatMessage.user(
                parts = listOf(
                    AiChatContentPart.text(prompt(locale)),
                    AiChatContentPart.imageBytes(imageBytes, mimeType),
                ),
            ),
        ),
        temperature = 0.0,
        maxOutputTokens = 900,
    )

    private fun prompt(locale: String): String =
        """
        Locale: $locale
        Analyze the image and extract health readings from device screens, labels, or handwritten logs.
        Return JSON only:
        {
          "metrics": [
            {
              "type": "BLOOD_PRESSURE|BLOOD_GLUCOSE|WEIGHT|BODY_FAT|HEART_RATE|TEMPERATURE|SPO2",
              "value": number,
              "secondaryValue": number|null,
              "unit": "mmHg|mmol/L|kg|%|bpm|°C",
              "rawText": "visible text",
              "confidence": 0.0
            }
          ],
          "texts": ["visible OCR text lines"]
        }
        If uncertain, include visible text in texts and omit the metric. Do not infer hidden values.
        """.trimIndent()
}

object HealthImageAnalysisParser {
    fun parse(text: String): OcrParseResult = parseStructured(text).parsed ?: emptyOcrParseResult()

    fun parseStructured(
        text: String,
        finishReason: String? = null,
        usage: AiTokenUsage? = null,
    ): AiStructuredResponse<OcrParseResult> {
        if (text.isBlank()) {
            return AiStructuredResponse(
                rawText = text,
                status = AiStructuredResponseStatus.FAILED,
                schemaVersion = HealthAiPromptVersions.IMAGE_OCR,
                warnings = listOf("Empty AI response."),
                errorKind = AiStructuredResponseErrorKind.EMPTY_RESPONSE,
                finishReason = finishReason,
                usage = usage,
            )
        }
        val jsonText = extractJsonObject(text)
        val fromJson = jsonText?.let { parseJsonResult(it) }
        if (fromJson != null && (fromJson.metrics.isNotEmpty() || fromJson.rawTexts.isNotEmpty())) {
            return AiStructuredResponse(
                rawText = text,
                rawJson = jsonText,
                parsed = fromJson,
                status = AiStructuredResponseStatus.SUCCESS,
                schemaVersion = HealthAiPromptVersions.IMAGE_OCR,
                finishReason = finishReason,
                usage = usage,
            )
        }
        val fallbackTexts = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val fallback = HealthMetricParser.parseAll(fallbackTexts)
        val hasFallback = fallback.metrics.isNotEmpty() || fallback.rawTexts.isNotEmpty()
        return AiStructuredResponse(
            rawText = text,
            rawJson = jsonText,
            parsed = fallback.takeIf { hasFallback },
            status = if (hasFallback) AiStructuredResponseStatus.PARTIAL else AiStructuredResponseStatus.FAILED,
            schemaVersion = HealthAiPromptVersions.IMAGE_OCR,
            warnings = listOf(
                if (jsonText == null) {
                    "JSON not found; used fallback text parser."
                } else {
                    "JSON schema invalid or empty; used fallback text parser."
                },
            ),
            errorKind = if (jsonText == null) {
                AiStructuredResponseErrorKind.JSON_NOT_FOUND
            } else {
                AiStructuredResponseErrorKind.SCHEMA_INVALID
            },
            finishReason = finishReason,
            usage = usage,
        )
    }

    private fun parseJsonResult(jsonText: String): OcrParseResult? = runCatching {
        val root = json.parseToJsonElement(jsonText).jsonObject
        val rawTexts = root.arrayOrNull("texts")
            ?.mapNotNull { it.asStringOrNull() }
            .orEmpty()
        val metrics = (root.arrayOrNull("metrics") ?: root.arrayOrNull("readings"))
            ?.mapNotNull { it.toMetricOrNull() }
            .orEmpty()
            .distinctBy { it.type to it.rawText }
        val texts = rawTexts.ifEmpty { metrics.map { it.rawText }.filter { it.isNotBlank() } }
        OcrParseResult(
            metrics = metrics,
            candidates = HealthMetricParser.extractNumbers(texts),
            rawTexts = texts,
        )
    }.getOrNull()

    private fun JsonElement.toMetricOrNull(): ParsedHealthMetric? {
        val obj = this as? JsonObject ?: return null
        val type = obj.metricType() ?: return null
        val value = obj.numberOrNull("value") ?: obj.valueStringNumberOrNull() ?: return null
        val secondary = obj.numberOrNull("secondaryValue") ?: obj.numberOrNull("secondary")
        if (!HealthMetricParser.isValuePlausible(value, type)) return null
        if (type == HealthType.BLOOD_PRESSURE &&
            secondary != null &&
            !HealthMetricParser.isValuePlausible(secondary, type)
        ) {
            return null
        }
        return ParsedHealthMetric(
            type = type,
            value = value,
            secondaryValue = secondary,
            rawText = obj.stringOrNull("rawText")
                ?: obj.stringOrNull("text")
                ?: obj.stringOrNull("label")
                ?: type.formatValue(value, secondary),
            confidence = obj.floatOrNull("confidence") ?: 0.75f,
        )
    }

    private fun JsonObject.metricType(): HealthType? {
        val explicit = stringOrNull("type") ?: stringOrNull("dataType") ?: stringOrNull("metric")
        explicit?.let { raw ->
            HealthType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }?.let { return it }
        }
        val label = listOfNotNull(
            stringOrNull("label"),
            stringOrNull("name"),
            stringOrNull("rawText"),
            stringOrNull("text"),
        ).joinToString(" ")
        return inferType(label)
    }

    private fun inferType(label: String): HealthType? {
        val normalized = label.lowercase()
        return when {
            listOf("血压", "bp", "blood pressure", "sys", "dia").any { it in normalized } -> HealthType.BLOOD_PRESSURE
            listOf("血糖", "glucose", "blood sugar", "mmol").any { it in normalized } -> HealthType.BLOOD_GLUCOSE
            listOf("体脂", "body fat", "fat rate", "bf").any { it in normalized } -> HealthType.BODY_FAT
            listOf("体重", "weight", "kg", "公斤").any { it in normalized } -> HealthType.WEIGHT
            listOf("心率", "heart rate", "pulse", "bpm").any { it in normalized } -> HealthType.HEART_RATE
            listOf("体温", "temperature", "temp", "°c", "℃").any { it in normalized } -> HealthType.TEMPERATURE
            listOf("血氧", "spo2", "spo₂", "oxygen").any { it in normalized } -> HealthType.SPO2
            else -> null
        }
    }

    private fun JsonObject.valueStringNumberOrNull(): Double? = stringOrNull("value")
        ?.let { NUMBER.find(it)?.value?.toDoubleOrNull() }

    private fun JsonObject.arrayOrNull(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.numberOrNull(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.floatOrNull(key: String): Float? = (this[key] as? JsonPrimitive)?.floatOrNull

    private fun JsonElement.asStringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun extractJsonObject(text: String): String? {
        FENCED_JSON.find(text)?.let { return it.groupValues[1] }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val FENCED_JSON = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""", RegexOption.IGNORE_CASE)
    private val NUMBER = Regex("""-?\d+(?:\.\d+)?""")
}

private fun emptyOcrParseResult(): OcrParseResult =
    OcrParseResult(metrics = emptyList(), candidates = emptyList(), rawTexts = emptyList())
