package com.driezy.medlog.ui.components

import com.driezy.medlog.domain.health.AiExecutionMode
import com.driezy.medlog.domain.health.AiExecutionStatus
import com.driezy.medlog.domain.health.AiFallbackReason
import org.junit.Assert.assertEquals
import org.junit.Test

class AiInteractionPresentationTest {

    @Test
    fun `running state takes precedence over last execution status`() {
        val presentation = AiInteractionPresentation.from(
            status = AiExecutionStatus.CloudSuccess,
            isRunning = true,
        )

        assertEquals(AiInteractionVisualState.RUNNING, presentation.visualState)
        assertEquals(true, presentation.animated)
    }

    @Test
    fun `cloud success maps to calm success state`() {
        val presentation = AiInteractionPresentation.from(AiExecutionStatus.CloudSuccess)

        assertEquals(AiInteractionVisualState.CLOUD_SUCCESS, presentation.visualState)
        assertEquals(false, presentation.animated)
    }

    @Test
    fun `unavailable and failed statuses map to fallback state`() {
        val unavailable = AiInteractionPresentation.from(
            AiExecutionStatus.unavailable(AiFallbackReason.API_KEY_MISSING),
        )
        val failed = AiInteractionPresentation.from(
            AiExecutionStatus(
                mode = AiExecutionMode.CLOUD_FAILED_FALLBACK,
                reason = AiFallbackReason.RESPONSE_FORMAT_INVALID,
                errorCategory = "SCHEMA_INVALID",
            ),
        )

        assertEquals(AiInteractionVisualState.LOCAL_FALLBACK, unavailable.visualState)
        assertEquals(AiInteractionVisualState.LOCAL_FALLBACK, failed.visualState)
    }
}
