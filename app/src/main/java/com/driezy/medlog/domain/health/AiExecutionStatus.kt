package com.driezy.medlog.domain.health

import com.driezy.medlog.ai.AiProviderException

enum class AiExecutionMode {
    LOCAL_ONLY,
    CLOUD_SUCCESS,
    CLOUD_UNAVAILABLE_FALLBACK,
    CLOUD_FAILED_FALLBACK,
}

enum class AiFallbackReason {
    CLOUD_AI_DISABLED,
    FEATURE_DISABLED,
    API_KEY_MISSING,
    WIFI_REQUIRED,
    OPENAI_COMPATIBLE_BASE_URL_MISSING,
    IMAGE_INPUT_UNSUPPORTED,
    NO_HEALTH_CONTEXT,
    PROVIDER_ERROR,
    RESPONSE_FORMAT_INVALID,
    UNKNOWN_ERROR,
    NONE,
    ;

    companion object {
        fun from(reason: HealthCloudImageAnalysisUnavailableReason): AiFallbackReason =
            when (reason) {
                HealthCloudImageAnalysisUnavailableReason.CLOUD_AI_DISABLED -> CLOUD_AI_DISABLED
                HealthCloudImageAnalysisUnavailableReason.IMAGE_ANALYSIS_DISABLED -> FEATURE_DISABLED
                HealthCloudImageAnalysisUnavailableReason.API_KEY_MISSING -> API_KEY_MISSING
                HealthCloudImageAnalysisUnavailableReason.WIFI_REQUIRED -> WIFI_REQUIRED
                HealthCloudImageAnalysisUnavailableReason.OPENAI_COMPATIBLE_BASE_URL_MISSING ->
                    OPENAI_COMPATIBLE_BASE_URL_MISSING
                HealthCloudImageAnalysisUnavailableReason.IMAGE_INPUT_UNSUPPORTED -> IMAGE_INPUT_UNSUPPORTED
            }

        fun from(reason: HealthCloudInsightUnavailableReason): AiFallbackReason =
            when (reason) {
                HealthCloudInsightUnavailableReason.CLOUD_AI_DISABLED -> CLOUD_AI_DISABLED
                HealthCloudInsightUnavailableReason.HEALTH_INSIGHTS_DISABLED -> FEATURE_DISABLED
                HealthCloudInsightUnavailableReason.API_KEY_MISSING -> API_KEY_MISSING
                HealthCloudInsightUnavailableReason.WIFI_REQUIRED -> WIFI_REQUIRED
                HealthCloudInsightUnavailableReason.OPENAI_COMPATIBLE_BASE_URL_MISSING ->
                    OPENAI_COMPATIBLE_BASE_URL_MISSING
            }
    }
}

data class AiExecutionStatus(
    val mode: AiExecutionMode,
    val reason: AiFallbackReason = AiFallbackReason.NONE,
    val providerName: String? = null,
    val providerStatusCode: Int? = null,
    val errorCategory: String? = null,
) {
    companion object {
        val LocalOnly = AiExecutionStatus(AiExecutionMode.LOCAL_ONLY)
        val CloudSuccess = AiExecutionStatus(AiExecutionMode.CLOUD_SUCCESS)

        fun unavailable(reason: AiFallbackReason): AiExecutionStatus =
            AiExecutionStatus(
                mode = AiExecutionMode.CLOUD_UNAVAILABLE_FALLBACK,
                reason = reason,
            )

        fun failed(reason: AiFallbackReason, error: Throwable? = null): AiExecutionStatus =
            AiExecutionStatus(
                mode = AiExecutionMode.CLOUD_FAILED_FALLBACK,
                reason = reason,
                errorCategory = error?.javaClass?.simpleName,
            )

        fun failedFrom(
            error: Throwable,
            fallbackReason: AiFallbackReason = AiFallbackReason.UNKNOWN_ERROR,
        ): AiExecutionStatus =
            when (error) {
                is AiProviderException -> providerError(error)
                else -> failed(fallbackReason, error)
            }

        fun providerError(error: AiProviderException): AiExecutionStatus =
            AiExecutionStatus(
                mode = AiExecutionMode.CLOUD_FAILED_FALLBACK,
                reason = if (error.errorKind != null) {
                    AiFallbackReason.RESPONSE_FORMAT_INVALID
                } else {
                    AiFallbackReason.PROVIDER_ERROR
                },
                providerName = error.providerName,
                providerStatusCode = error.statusCode,
                errorCategory = error.errorKind?.name ?: error.javaClass.simpleName,
            )
    }
}
