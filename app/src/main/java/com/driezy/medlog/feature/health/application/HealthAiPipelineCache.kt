package com.driezy.medlog.feature.health.application

import com.driezy.medlog.capability.ai.AiChatClient
import com.driezy.medlog.capability.ai.AiProviderConfig
import com.driezy.medlog.capability.ai.AiProviderException
import com.driezy.medlog.capability.ai.AiStructuredResponse
import com.driezy.medlog.capability.ai.AiStructuredResponseErrorKind
import com.driezy.medlog.capability.ai.AiStructuredResponseStatus
import com.driezy.medlog.capability.ai.AiTokenUsage
import com.driezy.medlog.data.model.AiAnalysisCacheEntry
import com.driezy.medlog.data.model.AiAnalysisKind
import com.driezy.medlog.data.model.AiUsageEvent
import com.driezy.medlog.data.model.AiUsageFeature
import com.driezy.medlog.data.model.AiUsageResult
import com.driezy.medlog.data.model.HealthType
import com.driezy.medlog.data.model.NetworkType
import com.driezy.medlog.data.model.OcrParseResult
import com.driezy.medlog.data.repository.AiCacheKeyBuilder
import com.driezy.medlog.data.repository.AiCacheRepository
import com.driezy.medlog.data.repository.CloudAiProvider
import com.driezy.medlog.data.repository.SettingsPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val IMAGE_OCR_TTL_MS = DAY_MS
private const val HEALTH_INSIGHT_TTL_MS = 12L * 60 * 60 * 1000

data class HealthAiModelIdentity(val provider: String, val model: String)

internal fun AiProviderConfig.toHealthAiModelIdentity(settings: SettingsPreferences): HealthAiModelIdentity =
    when (this) {
        is AiProviderConfig.Mimo -> HealthAiModelIdentity(CloudAiProvider.MIMO.providerName, model)
        is AiProviderConfig.Gemini -> HealthAiModelIdentity(CloudAiProvider.GEMINI.providerName, model)
        is AiProviderConfig.Anthropic -> HealthAiModelIdentity(CloudAiProvider.ANTHROPIC.providerName, model)
        is AiProviderConfig.OpenAiCompatible -> HealthAiModelIdentity(
            provider = providerName.ifBlank {
                settings.openAiCompatibleProviderName.ifBlank { CloudAiProvider.OPENAI_COMPATIBLE.providerName }
            },
            model = model,
        )
    }

object HealthAiPromptVersions {
    const val IMAGE_OCR = 1
    const val HEALTH_INSIGHT = 1
}

class CachedHealthImageAnalyzer(
    private val aiChatClient: AiChatClient,
    private val cacheRepository: AiCacheRepository,
    private val identity: HealthAiModelIdentity,
    private val networkType: NetworkType = NetworkType.OFFLINE_UNKNOWN,
) {
    private val analyzer = HealthImageAnalyzer(aiChatClient)

    suspend fun analyze(request: HealthImageAnalysisRequest, nowMillis: Long): OcrParseResult {
        val inputHash = AiCacheKeyBuilder.sha256(request.imageBytes)
        val cacheKey = AiCacheKeyBuilder.build(
            kind = AiAnalysisKind.IMAGE_OCR,
            provider = identity.provider,
            model = identity.model,
            promptVersion = HealthAiPromptVersions.IMAGE_OCR,
            inputHash = inputHash,
            locale = request.locale,
        )

        cacheRepository.getFresh(cacheKey, nowMillis)
            ?.responseJson
            ?.let(HealthAiCachePayloadCodec::decodeImageOcr)
            ?.let { cached ->
                recordUsage(AiUsageFeature.IMAGE_OCR, inputHash, cacheHit = true, result = AiUsageResult.SUCCESS)
                return cached
            }

        return runCatching {
            val structured = analyzer.analyzeStructured(request)
            val result = structured.parsed
                ?: throw AiProviderException(
                    providerName = identity.provider,
                    statusCode = null,
                    errorKind = structured.errorKind,
                    message = "Image OCR response did not contain usable metrics: ${structured.errorKind}.",
                )
            structured
        }.onSuccess { structured ->
            val result = structured.parsed!!
            cacheRepository.put(
                AiAnalysisCacheEntry(
                    cacheKey = cacheKey,
                    kind = AiAnalysisKind.IMAGE_OCR,
                    provider = identity.provider,
                    model = identity.model,
                    promptVersion = HealthAiPromptVersions.IMAGE_OCR,
                    inputHash = inputHash,
                    locale = request.locale,
                    responseJson = HealthAiCachePayloadCodec.encodeImageOcrStructured(structured),
                    createdAt = nowMillis,
                    expiresAt = nowMillis + IMAGE_OCR_TTL_MS,
                ),
                nowMillis = nowMillis,
            )
            recordUsage(
                feature = AiUsageFeature.IMAGE_OCR,
                inputHash = inputHash,
                cacheHit = false,
                result = AiUsageResult.SUCCESS,
                errorCategory = structured.errorKind?.name,
            )
        }.onFailure { error ->
            recordUsage(
                feature = AiUsageFeature.IMAGE_OCR,
                inputHash = inputHash,
                cacheHit = false,
                result = AiUsageResult.ERROR,
                errorCategory = structuredErrorCategory(error),
            )
        }.getOrThrow().parsed!!
    }

    private suspend fun recordUsage(
        feature: AiUsageFeature,
        inputHash: String,
        cacheHit: Boolean,
        result: AiUsageResult,
        errorCategory: String? = null,
    ) {
        cacheRepository.recordUsage(
            AiUsageEvent(
                feature = feature,
                provider = identity.provider,
                model = identity.model,
                networkType = networkType,
                cacheHit = cacheHit,
                result = result,
                errorCategory = errorCategory,
                inputHashPrefix = inputHash,
            ),
        )
    }
}

class CachedHealthInsightGenerator(
    private val aiChatClient: AiChatClient,
    private val cacheRepository: AiCacheRepository,
    private val identity: HealthAiModelIdentity,
    private val networkType: NetworkType = NetworkType.OFFLINE_UNKNOWN,
) {
    suspend fun generate(
        context: HealthInsightContext,
        profile: HealthPromptProfile = HealthPromptProfile(),
        nowMillis: Long,
    ): List<HealthInsight> {
        val inputHash = AiCacheKeyBuilder.sha256(context.cacheFingerprint(profile).toByteArray())
        val cacheKey = AiCacheKeyBuilder.build(
            kind = AiAnalysisKind.HEALTH_INSIGHT,
            provider = identity.provider,
            model = identity.model,
            promptVersion = HealthAiPromptVersions.HEALTH_INSIGHT,
            inputHash = inputHash,
            locale = profile.locale,
        )

        cacheRepository.getFresh(cacheKey, nowMillis)
            ?.responseJson
            ?.let(HealthAiCachePayloadCodec::decodeHealthInsights)
            ?.let { cached ->
                recordUsage(inputHash, cacheHit = true, result = AiUsageResult.SUCCESS)
                return cached
            }

        var structuredForCache: AiStructuredResponse<List<HealthInsight>>? = null
        var parsedErrorCategory: String? = null
        return runCatching {
            val response = aiChatClient.generate(HealthInsightPromptBuilder.buildRequest(context, profile))
            val structured = HealthInsightResponseParser.parseStructured(
                text = response.text,
                finishReason = response.finishReason,
                usage = response.usage,
            )
            structuredForCache = structured
            parsedErrorCategory = structured.errorKind?.name
            structured.parsed
                ?: throw AiProviderException(
                    providerName = identity.provider,
                    statusCode = null,
                    errorKind = structured.errorKind,
                    message = "Health insight response did not contain valid insights JSON: ${structured.errorKind}.",
                )
        }.onSuccess { insights ->
            cacheRepository.put(
                AiAnalysisCacheEntry(
                    cacheKey = cacheKey,
                    kind = AiAnalysisKind.HEALTH_INSIGHT,
                    provider = identity.provider,
                    model = identity.model,
                    promptVersion = HealthAiPromptVersions.HEALTH_INSIGHT,
                    inputHash = inputHash,
                    locale = profile.locale,
                    responseJson = HealthAiCachePayloadCodec.encodeHealthInsightsStructured(
                        structuredForCache ?: AiStructuredResponse(
                            rawText = "",
                            parsed = insights,
                            status = AiStructuredResponseStatus.SUCCESS,
                            schemaVersion = HealthAiPromptVersions.HEALTH_INSIGHT,
                        ),
                    ),
                    createdAt = nowMillis,
                    expiresAt = nowMillis + HEALTH_INSIGHT_TTL_MS,
                ),
                nowMillis = nowMillis,
            )
            recordUsage(inputHash, cacheHit = false, result = AiUsageResult.SUCCESS)
        }.onFailure { error ->
            recordUsage(
                inputHash = inputHash,
                cacheHit = false,
                result = AiUsageResult.ERROR,
                errorCategory = parsedErrorCategory ?: structuredErrorCategory(error),
            )
        }.getOrThrow()
    }

    private suspend fun recordUsage(
        inputHash: String,
        cacheHit: Boolean,
        result: AiUsageResult,
        errorCategory: String? = null,
    ) {
        cacheRepository.recordUsage(
            AiUsageEvent(
                feature = AiUsageFeature.HEALTH_INSIGHT,
                provider = identity.provider,
                model = identity.model,
                networkType = networkType,
                cacheHit = cacheHit,
                result = result,
                errorCategory = errorCategory,
                inputHashPrefix = inputHash,
            ),
        )
    }
}

private class AiStructuredResponseException(val errorKind: AiStructuredResponseErrorKind?, message: String) :
    RuntimeException(message)

private fun structuredErrorCategory(error: Throwable): String =
    (error as? AiStructuredResponseException)?.errorKind?.name
        ?: (error as? AiProviderException)?.errorKind?.name
        ?: error::class.simpleName
        ?: AiStructuredResponseErrorKind.UNKNOWN.name

object HealthInsightResponseParser {
    fun parse(text: String): List<HealthInsight>? = parseStructured(text).parsed

    fun parseStructured(
        text: String,
        finishReason: String? = null,
        usage: AiTokenUsage? = null,
    ): AiStructuredResponse<List<HealthInsight>> {
        if (text.isBlank()) {
            return AiStructuredResponse(
                rawText = text,
                status = AiStructuredResponseStatus.FAILED,
                schemaVersion = HealthAiPromptVersions.HEALTH_INSIGHT,
                warnings = listOf("Empty AI response."),
                errorKind = AiStructuredResponseErrorKind.EMPTY_RESPONSE,
                finishReason = finishReason,
                usage = usage,
            )
        }
        val jsonText = extractJsonPayload(text)
            ?: return AiStructuredResponse(
                rawText = text,
                status = AiStructuredResponseStatus.FAILED,
                schemaVersion = HealthAiPromptVersions.HEALTH_INSIGHT,
                warnings = listOf("JSON not found in health insight response."),
                errorKind = AiStructuredResponseErrorKind.JSON_NOT_FOUND,
                finishReason = finishReason,
                usage = usage,
            )
        return runCatching {
            val root = json.parseToJsonElement(jsonText)
            val insightElements = root.findInsightsArrayOrNull()
                ?: return@runCatching AiStructuredResponse(
                    rawText = text,
                    rawJson = jsonText,
                    status = AiStructuredResponseStatus.FAILED,
                    schemaVersion = HealthAiPromptVersions.HEALTH_INSIGHT,
                    warnings = listOf("Health insight JSON schema missing insights array."),
                    errorKind = AiStructuredResponseErrorKind.SCHEMA_INVALID,
                    finishReason = finishReason,
                    usage = usage,
                )
            val insights = insightElements.mapNotNull { it.toInsightOrNull() }
            if (insights.isEmpty()) {
                AiStructuredResponse(
                    rawText = text,
                    rawJson = jsonText,
                    status = AiStructuredResponseStatus.FAILED,
                    schemaVersion = HealthAiPromptVersions.HEALTH_INSIGHT,
                    warnings = listOf("Health insight JSON schema did not contain usable insights."),
                    errorKind = AiStructuredResponseErrorKind.SCHEMA_INVALID,
                    finishReason = finishReason,
                    usage = usage,
                )
            } else if (insights.any { it.containsRestrictedMedicalAdvice() }) {
                AiStructuredResponse(
                    rawText = text,
                    rawJson = jsonText,
                    status = AiStructuredResponseStatus.FAILED,
                    schemaVersion = HealthAiPromptVersions.HEALTH_INSIGHT,
                    warnings = listOf("Health insight JSON contained restricted medical advice."),
                    errorKind = AiStructuredResponseErrorKind.POLICY_VIOLATION,
                    finishReason = finishReason,
                    usage = usage,
                )
            } else {
                AiStructuredResponse(
                    rawText = text,
                    rawJson = jsonText,
                    parsed = insights,
                    status = AiStructuredResponseStatus.SUCCESS,
                    schemaVersion = HealthAiPromptVersions.HEALTH_INSIGHT,
                    finishReason = finishReason,
                    usage = usage,
                )
            }
        }.getOrElse {
            AiStructuredResponse(
                rawText = text,
                rawJson = jsonText,
                status = AiStructuredResponseStatus.FAILED,
                schemaVersion = HealthAiPromptVersions.HEALTH_INSIGHT,
                warnings = listOf("Health insight JSON could not be parsed."),
                errorKind = AiStructuredResponseErrorKind.JSON_INVALID,
                finishReason = finishReason,
                usage = usage,
            )
        }
    }

    private fun JsonElement.toInsightOrNull(): HealthInsight? {
        val obj = this as? JsonObject ?: return null
        val kind = obj.enumOrNull<HealthInsightKind>("kind") ?: return null
        val severity = obj.enumOrNull<HealthInsightSeverity>("severity") ?: return null
        val title = obj.stringOrNull("title")?.takeIf { it.isNotBlank() } ?: return null
        val body = obj.stringOrNull("body")?.takeIf { it.isNotBlank() } ?: return null
        val relatedType = obj.stringOrNull("relatedType")
            ?.let { raw -> HealthType.entries.firstOrNull { it.name == raw } }
        return HealthInsight(
            id = obj.stringOrNull("id")?.takeIf { it.isNotBlank() } ?: stableInsightId(kind, severity, title),
            kind = kind,
            severity = severity,
            title = title,
            body = body,
            relatedType = relatedType,
        )
    }

    private inline fun <reified T : Enum<T>> JsonObject.enumOrNull(key: String): T? =
        stringOrNull(key)?.let { raw -> enumValues<T>().firstOrNull { it.name == raw } }

    private fun JsonObject.arrayOrNull(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonElement.findInsightsArrayOrNull(): JsonArray? = when (this) {
        is JsonObject -> arrayOrNull("insights")
            ?: values.asSequence().mapNotNull { it.findInsightsArrayOrNull() }.firstOrNull()
        is JsonArray -> asSequence().mapNotNull { it.findInsightsArrayOrNull() }.firstOrNull()
        else -> null
    }

    private fun extractJsonPayload(text: String): String? {
        FENCED_JSON.find(text)?.let { return it.groupValues[1] }
        val objectStart = text.indexOf('{')
        val arrayStart = text.indexOf('[')
        val start = listOf(objectStart, arrayStart).filter { it >= 0 }.minOrNull() ?: return null
        val end = if (arrayStart == start) text.lastIndexOf(']') else text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }

    private fun stableInsightId(kind: HealthInsightKind, severity: HealthInsightSeverity, title: String): String =
        "${kind.name.lowercase()}-${severity.name.lowercase()}-${AiCacheKeyBuilder.hashPrefix(
            AiCacheKeyBuilder.sha256(title.toByteArray()),
        )}"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val FENCED_JSON = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""", RegexOption.IGNORE_CASE)
}

private fun HealthInsight.containsRestrictedMedicalAdvice(): Boolean {
    val text = "$title $body".lowercase(Locale.ROOT)
    return RESTRICTED_HEALTH_ADVICE_PATTERNS.any { it.containsMatchIn(text) }
}

private val RESTRICTED_HEALTH_ADVICE_PATTERNS = listOf(
    Regex("""诊断(为|是|成|出)?"""),
    Regex("""(治疗方案|治疗计划)"""),
    Regex("""(开|换|停|加|减|增加|减少|调整|改变).{0,8}(药|药物|药量|剂量)"""),
    Regex("""(药|药物|药量|剂量).{0,8}(加倍|减半|增加|减少|调整|改变|停用|停掉)"""),
    Regex("""diagnos(e|is|ed|ing)"""),
    Regex("""treatment plan"""),
    Regex(
        """(increase|decrease|double|halve|adjust|change|stop|start|switch).{0,24}(dose|dosage|medication|medicine|drug)""",
    ),
    Regex(
        """(dose|dosage|medication|medicine|drug).{0,24}(increase|decrease|double|halve|adjust|change|stop|start|switch)""",
    ),
    Regex("""(薬|くすり).{0,8}(量|用量).{0,8}(増|減|倍|調整|変え|変更|中止|やめ)"""),
    Regex("""(増|減|倍|調整|変え|変更|中止|やめ).{0,8}(薬|くすり).{0,8}(量|用量)"""),
    Regex("""(약|약물|복용량|용량).{0,8}(늘리|줄이|두 배|절반|조절|변경|중단|시작)"""),
    Regex("""(늘리|줄이|두 배|절반|조절|변경|중단|시작).{0,8}(약|약물|복용량|용량)"""),
)

private fun HealthInsightContext.cacheFingerprint(profile: HealthPromptProfile): String = buildString {
    appendLine("promptVersion=${HealthAiPromptVersions.HEALTH_INSIGHT}")
    appendLine("locale=${profile.locale}")
    appendLine("maxInsights=${profile.maxInsights}")
    appendLine("tone=${profile.tone}")
    appendLine("heightCm=${if (userHeightCm > 0f) userHeightCm.cacheFmt() else "unknown"}")
    appendLine("BMI=${bmi?.cacheFmt() ?: "unknown"}")
    metrics.forEach { metric ->
        appendLine(
            listOf(
                metric.type.name,
                metric.latestValue.cacheFmt(),
                metric.latestSecondaryValue?.cacheFmt() ?: "none",
                metric.latestTimestamp.toString(),
                metric.avg7d?.cacheFmt() ?: "none",
                metric.min7d?.cacheFmt() ?: "none",
                metric.max7d?.cacheFmt() ?: "none",
                metric.count7d.toString(),
                metric.trend.name,
                metric.isAbnormal.toString(),
                metric.sourceMix.name,
                metric.manualCount.toString(),
                metric.localOcrCount.toString(),
                metric.cloudOcrCount.toString(),
                metric.importCount.toString(),
                metric.lowConfidenceCount.toString(),
            ).joinToString("|"),
        )
    }
}

private fun Double.cacheFmt(): String = String.format(Locale.US, "%.1f", this)

private fun Float.cacheFmt(): String = String.format(Locale.US, "%.1f", this)
