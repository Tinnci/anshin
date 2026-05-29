package com.driezy.medlog.ui.ocr

import com.driezy.medlog.R
import com.driezy.medlog.domain.health.AiExecutionMode
import com.driezy.medlog.domain.health.AiExecutionStatus
import com.driezy.medlog.domain.health.AiFallbackReason
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthOcrCloudAnalysisPresentationTest {
    @Test
    fun `available cloud action uses stacked status hierarchy`() {
        val presentation = HealthOcrCloudActionPresentation.from(
            canRunCloudAnalysis = true,
            isCloudAnalyzing = false,
            cloudAnalysisFailed = false,
        )

        assertEquals(true, presentation.showPanel)
        assertEquals(HealthOcrCloudStatusPlacement.BELOW_PRIMARY_ACTION, presentation.statusPlacement)
    }

    @Test
    fun `missing api key explains setup action`() {
        val status = AiExecutionStatus.unavailable(AiFallbackReason.API_KEY_MISSING)

        assertEquals(R.string.ocr_cloud_analysis_needs_key, status.cloudAnalysisMessageRes())
    }

    @Test
    fun `wifi requirement explains network constraint`() {
        val status = AiExecutionStatus.unavailable(AiFallbackReason.WIFI_REQUIRED)

        assertEquals(R.string.ocr_cloud_analysis_wifi_required, status.cloudAnalysisMessageRes())
    }

    @Test
    fun `image unsupported explains model limitation`() {
        val status = AiExecutionStatus.unavailable(AiFallbackReason.IMAGE_INPUT_UNSUPPORTED)

        assertEquals(R.string.ocr_cloud_analysis_image_unsupported, status.cloudAnalysisMessageRes())
    }

    @Test
    fun `provider errors use service message`() {
        val status = AiExecutionStatus(
            mode = AiExecutionMode.CLOUD_FAILED_FALLBACK,
            reason = AiFallbackReason.PROVIDER_ERROR,
        )

        assertEquals(R.string.ocr_cloud_analysis_provider_error, status.cloudAnalysisMessageRes())
    }

    @Test
    fun `unknown failures keep generic fallback message`() {
        val status = AiExecutionStatus.failed(AiFallbackReason.UNKNOWN_ERROR)

        assertEquals(R.string.ocr_cloud_analysis_failed, status.cloudAnalysisMessageRes())
    }
}
