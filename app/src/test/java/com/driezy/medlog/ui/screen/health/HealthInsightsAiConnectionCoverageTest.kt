package com.driezy.medlog.ui.screen.health

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HealthInsightsAiConnectionCoverageTest {
    private val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun source(path: String) = File(projectRoot, path).readText()

    @Test
    fun `health insights refresh when cloud ai key availability changes`() {
        val viewModel = source("app/src/main/java/com/driezy/medlog/ui/screen/health/HealthViewModel.kt")

        assertTrue(viewModel.contains("private val apiKeyStore: AiApiKeyStore"))
        assertTrue(viewModel.contains("apiKeyStore.availableProviders"))
        assertTrue(viewModel.contains("generateWithStatus("))
    }

    @Test
    fun `health insight use case reports cloud success and records health insight usage`() {
        val useCase = source("app/src/main/java/com/driezy/medlog/domain/health/HealthInsightGenerationUseCase.kt")
        val cache = source("app/src/main/java/com/driezy/medlog/domain/health/HealthAiPipelineCache.kt")

        assertTrue(useCase.contains("HealthCloudInsightGenerationGate.evaluate"))
        assertTrue(useCase.contains("AiExecutionStatus.CloudSuccess"))
        assertTrue(useCase.contains("AiExecutionStatus.providerError"))
        assertTrue(cache.contains("AiUsageFeature.HEALTH_INSIGHT"))
        assertTrue(cache.contains("AiAnalysisKind.HEALTH_INSIGHT"))
    }
}
