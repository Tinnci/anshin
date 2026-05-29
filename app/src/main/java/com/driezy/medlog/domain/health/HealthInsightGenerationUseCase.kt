package com.driezy.medlog.domain.health

import android.util.Log
import com.driezy.medlog.ai.AiApiKeyStore
import com.driezy.medlog.ai.AiChatClientFactory
import com.driezy.medlog.ai.AiCloudConfigResolver
import com.driezy.medlog.ai.AiProviderException
import com.driezy.medlog.data.model.HealthRecord
import com.driezy.medlog.data.repository.AiCacheRepository
import com.driezy.medlog.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HealthInsightGeneration"

data class HealthInsightGenerationResult(
    val insights: List<HealthInsight>,
    val executionStatus: AiExecutionStatus,
)

@Singleton
class HealthInsightGenerationUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val apiKeyStore: AiApiKeyStore,
    private val cacheRepository: AiCacheRepository,
    private val networkStatusProvider: AiNetworkStatusProvider,
) {
    suspend fun generate(
        records: List<HealthRecord>,
        userHeightCm: Float,
        locale: String = Locale.getDefault().toLanguageTag(),
    ): List<HealthInsight> =
        generateWithStatus(records, userHeightCm, locale).insights

    suspend fun generateWithStatus(
        records: List<HealthRecord>,
        userHeightCm: Float,
        locale: String = Locale.getDefault().toLanguageTag(),
    ): HealthInsightGenerationResult = withContext(Dispatchers.IO) {
        generateInternal(records, userHeightCm, locale)
    }

    private suspend fun generateInternal(
        records: List<HealthRecord>,
        userHeightCm: Float,
        locale: String,
    ): HealthInsightGenerationResult {
        val context = HealthIntelligenceEngine.buildContext(
            records = records,
            userHeightCm = userHeightCm,
        )
        val localInsights = HealthIntelligenceEngine.generateLocalInsights(context)
        val settings = preferencesRepository.settingsFlow.first()
        val networkType = networkStatusProvider.currentNetworkType()
        val availability = HealthCloudInsightGenerationGate.evaluate(
            settings = settings,
            availableProviders = apiKeyStore.availableProviders.value,
            networkType = networkType,
        )
        if (context.metrics.isEmpty()) {
            return HealthInsightGenerationResult(
                insights = localInsights,
                executionStatus = AiExecutionStatus.unavailable(AiFallbackReason.NO_HEALTH_CONTEXT),
            )
        }
        if (!availability.isAvailable) {
            val reason = availability.reason
                ?.let(AiFallbackReason::from)
                ?: AiFallbackReason.UNKNOWN_ERROR
            return HealthInsightGenerationResult(
                insights = localInsights,
                executionStatus = AiExecutionStatus.unavailable(reason),
            )
        }

        val apiKey = apiKeyStore.getApiKey(settings.cloudAiProvider)
            ?: return HealthInsightGenerationResult(
                insights = localInsights,
                executionStatus = AiExecutionStatus.unavailable(AiFallbackReason.API_KEY_MISSING),
            )
        val config = AiCloudConfigResolver.toProviderConfig(settings, apiKey)

        return runCatching {
            CachedHealthInsightGenerator(
                aiChatClient = AiChatClientFactory.create(config),
                cacheRepository = cacheRepository,
                identity = config.toHealthAiModelIdentity(settings),
                networkType = networkType,
            ).generate(
                context = context,
                profile = HealthPromptProfile(locale = locale),
            )
        }.fold(
            onSuccess = { insights ->
                if (insights.isEmpty()) {
                    HealthInsightGenerationResult(
                        insights = localInsights,
                        executionStatus = AiExecutionStatus.failed(AiFallbackReason.RESPONSE_FORMAT_INVALID),
                    )
                } else {
                    HealthInsightGenerationResult(
                        insights = insights,
                        executionStatus = AiExecutionStatus.CloudSuccess,
                    )
                }
            },
            onFailure = { error ->
                Log.w(TAG, "Cloud health insight generation failed; using local insights", error)
                HealthInsightGenerationResult(
                    insights = localInsights,
                    executionStatus = when (error) {
                        is AiProviderException -> AiExecutionStatus.providerError(error)
                        else -> AiExecutionStatus.failed(AiFallbackReason.UNKNOWN_ERROR, error)
                    },
                )
            },
        )
    }
}
