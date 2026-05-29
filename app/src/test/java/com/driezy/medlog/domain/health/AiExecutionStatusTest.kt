package com.driezy.medlog.domain.health

import com.driezy.medlog.ai.AiProviderException
import com.driezy.medlog.ai.AiStructuredResponseErrorKind
import org.junit.Assert.assertEquals
import org.junit.Test

class AiExecutionStatusTest {

    @Test
    fun `image availability reasons map to internal fallback reasons`() {
        assertEquals(
            AiFallbackReason.API_KEY_MISSING,
            AiFallbackReason.from(HealthCloudImageAnalysisUnavailableReason.API_KEY_MISSING),
        )
        assertEquals(
            AiFallbackReason.WIFI_REQUIRED,
            AiFallbackReason.from(HealthCloudImageAnalysisUnavailableReason.WIFI_REQUIRED),
        )
        assertEquals(
            AiFallbackReason.OPENAI_COMPATIBLE_BASE_URL_MISSING,
            AiFallbackReason.from(HealthCloudImageAnalysisUnavailableReason.OPENAI_COMPATIBLE_BASE_URL_MISSING),
        )
    }

    @Test
    fun `insight availability reasons map to internal fallback reasons`() {
        assertEquals(
            AiFallbackReason.FEATURE_DISABLED,
            AiFallbackReason.from(HealthCloudInsightUnavailableReason.HEALTH_INSIGHTS_DISABLED),
        )
        assertEquals(
            AiFallbackReason.CLOUD_AI_DISABLED,
            AiFallbackReason.from(HealthCloudInsightUnavailableReason.CLOUD_AI_DISABLED),
        )
    }

    @Test
    fun `provider errors preserve status code as internal failure status`() {
        val status = AiExecutionStatus.providerError(
            AiProviderException(
                providerName = "Gemini",
                statusCode = 429,
                message = "quota",
            ),
        )

        assertEquals(AiExecutionMode.CLOUD_FAILED_FALLBACK, status.mode)
        assertEquals(AiFallbackReason.PROVIDER_ERROR, status.reason)
        assertEquals(429, status.providerStatusCode)
        assertEquals("Gemini", status.providerName)
    }

    @Test
    fun `structured response errors map to response format fallback`() {
        val status = AiExecutionStatus.providerError(
            AiProviderException(
                providerName = "Gemini",
                statusCode = null,
                message = "schema invalid",
                errorKind = AiStructuredResponseErrorKind.SCHEMA_INVALID,
            ),
        )

        assertEquals(AiExecutionMode.CLOUD_FAILED_FALLBACK, status.mode)
        assertEquals(AiFallbackReason.RESPONSE_FORMAT_INVALID, status.reason)
        assertEquals(AiStructuredResponseErrorKind.SCHEMA_INVALID.name, status.errorCategory)
        assertEquals("Gemini", status.providerName)
    }

    @Test
    fun `failed from throwable maps provider structured errors`() {
        val status = AiExecutionStatus.failedFrom(
            AiProviderException(
                providerName = "MiMo",
                statusCode = null,
                message = "empty",
                errorKind = AiStructuredResponseErrorKind.EMPTY_RESPONSE,
            ),
        )

        assertEquals(AiExecutionMode.CLOUD_FAILED_FALLBACK, status.mode)
        assertEquals(AiFallbackReason.RESPONSE_FORMAT_INVALID, status.reason)
        assertEquals(AiStructuredResponseErrorKind.EMPTY_RESPONSE.name, status.errorCategory)
        assertEquals("MiMo", status.providerName)
    }
}
