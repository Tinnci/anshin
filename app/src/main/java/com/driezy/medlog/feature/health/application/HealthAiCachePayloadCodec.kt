package com.driezy.medlog.feature.health.application

import com.driezy.medlog.capability.ai.AiStructuredResponse
import com.driezy.medlog.capability.ai.AiStructuredResponseStatus
import com.driezy.medlog.data.model.ExtractedNumber
import com.driezy.medlog.data.model.HealthType
import com.driezy.medlog.data.model.OcrParseResult
import com.driezy.medlog.data.model.ParsedHealthMetric
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object HealthAiCachePayloadCodec {
    private const val SCHEMA_VERSION = 1
    private const val IMAGE_OCR_KIND = "IMAGE_OCR"
    private const val HEALTH_INSIGHT_KIND = "HEALTH_INSIGHT"

    fun encodeImageOcr(result: OcrParseResult): String = encodeImageOcrStructured(
        AiStructuredResponse(
            rawText = "",
            parsed = result,
            status = AiStructuredResponseStatus.SUCCESS,
            schemaVersion = HealthAiPromptVersions.IMAGE_OCR,
        ),
    )

    fun encodeImageOcrStructured(response: AiStructuredResponse<OcrParseResult>): String {
        val result = response.parsed ?: OcrParseResult(emptyList(), emptyList(), emptyList())
        return json.encodeToString(
            ImageOcrPayload(
                responseStatus = response.status.name,
                responseErrorKind = response.errorKind?.name,
                responseWarnings = response.warnings,
                metrics = result.metrics.map { it.toDto() },
                candidates = result.candidates.map { it.toDto() },
                rawTexts = result.rawTexts,
            ),
        )
    }

    fun decodeMetadata(payloadJson: String): AiCachePayloadMetadata? = runCatching {
        val payload = json.decodeFromString<PayloadMetadataDto>(payloadJson)
        AiCachePayloadMetadata(
            status = payload.responseStatus,
            errorKind = payload.responseErrorKind,
            warnings = payload.responseWarnings,
        )
    }.getOrNull()

    fun decodeImageOcr(payloadJson: String): OcrParseResult? = runCatching {
        val payload = json.decodeFromString<ImageOcrPayload>(payloadJson)
        if (payload.schemaVersion != SCHEMA_VERSION || payload.kind != IMAGE_OCR_KIND) return null
        OcrParseResult(
            metrics = payload.metrics.mapNotNull { it.toMetricOrNull() },
            candidates = payload.candidates.map { it.toCandidate() },
            rawTexts = payload.rawTexts,
        )
    }.getOrNull()

    fun encodeHealthInsights(insights: List<HealthInsight>): String = encodeHealthInsightsStructured(
        AiStructuredResponse(
            rawText = "",
            parsed = insights,
            status = AiStructuredResponseStatus.SUCCESS,
            schemaVersion = HealthAiPromptVersions.HEALTH_INSIGHT,
        ),
    )

    fun encodeHealthInsightsStructured(response: AiStructuredResponse<List<HealthInsight>>): String =
        json.encodeToString(
            HealthInsightPayload(
                responseStatus = response.status.name,
                responseErrorKind = response.errorKind?.name,
                responseWarnings = response.warnings,
                insights = response.parsed.orEmpty().map { it.toDto() },
            ),
        )

    fun decodeHealthInsights(payloadJson: String): List<HealthInsight>? = runCatching {
        val payload = json.decodeFromString<HealthInsightPayload>(payloadJson)
        if (payload.schemaVersion != SCHEMA_VERSION || payload.kind != HEALTH_INSIGHT_KIND) return null
        payload.insights.mapNotNull { it.toInsightOrNull() }
    }.getOrNull()

    private fun ParsedHealthMetric.toDto(): MetricDto = MetricDto(
        type = type.name,
        value = value,
        secondaryValue = secondaryValue,
        rawText = rawText,
        confidence = confidence,
    )

    private fun MetricDto.toMetricOrNull(): ParsedHealthMetric? {
        val healthType = HealthType.entries.firstOrNull { it.name == type } ?: return null
        return ParsedHealthMetric(
            type = healthType,
            value = value,
            secondaryValue = secondaryValue,
            rawText = rawText,
            confidence = confidence,
        )
    }

    private fun ExtractedNumber.toDto(): CandidateDto = CandidateDto(
        value = value,
        pairedValue = pairedValue,
        rawText = rawText,
        confidence = confidence,
    )

    private fun CandidateDto.toCandidate(): ExtractedNumber = ExtractedNumber(
        value = value,
        pairedValue = pairedValue,
        rawText = rawText,
        confidence = confidence,
    )

    private fun HealthInsight.toDto(): InsightDto = InsightDto(
        id = id,
        kind = kind.name,
        severity = severity.name,
        title = title,
        body = body,
        relatedType = relatedType?.name,
    )

    private fun InsightDto.toInsightOrNull(): HealthInsight? {
        val insightKind = HealthInsightKind.entries.firstOrNull { it.name == kind } ?: return null
        val insightSeverity = HealthInsightSeverity.entries.firstOrNull { it.name == severity } ?: return null
        val healthType = relatedType?.let { type ->
            HealthType.entries.firstOrNull { it.name == type } ?: return null
        }
        if (title.isBlank() || body.isBlank()) return null
        return HealthInsight(
            id = id,
            kind = insightKind,
            severity = insightSeverity,
            title = title,
            body = body,
            relatedType = healthType,
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
}

data class AiCachePayloadMetadata(
    val status: String? = null,
    val errorKind: String? = null,
    val warnings: List<String> = emptyList(),
)

@Serializable
private data class ImageOcrPayload(
    val schemaVersion: Int = 1,
    val kind: String = "IMAGE_OCR",
    val responseStatus: String? = null,
    val responseErrorKind: String? = null,
    val responseWarnings: List<String> = emptyList(),
    val metrics: List<MetricDto> = emptyList(),
    val candidates: List<CandidateDto> = emptyList(),
    val rawTexts: List<String> = emptyList(),
)

@Serializable
private data class HealthInsightPayload(
    val schemaVersion: Int = 1,
    val kind: String = "HEALTH_INSIGHT",
    val responseStatus: String? = null,
    val responseErrorKind: String? = null,
    val responseWarnings: List<String> = emptyList(),
    val insights: List<InsightDto> = emptyList(),
)

@Serializable
private data class PayloadMetadataDto(
    val responseStatus: String? = null,
    val responseErrorKind: String? = null,
    val responseWarnings: List<String> = emptyList(),
)

@Serializable
private data class MetricDto(
    val type: String,
    val value: Double,
    val secondaryValue: Double? = null,
    val rawText: String,
    val confidence: Float = 0f,
)

@Serializable
private data class CandidateDto(
    val value: Double,
    val pairedValue: Double? = null,
    val rawText: String,
    val confidence: Float = 0f,
)

@Serializable
private data class InsightDto(
    val id: String,
    val kind: String,
    val severity: String,
    val title: String,
    val body: String,
    @SerialName("relatedType")
    val relatedType: String? = null,
)
