package com.driezy.medlog.feature.health.application

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.driezy.medlog.capability.ai.AiApiKeyStore
import com.driezy.medlog.capability.ai.AiChatClientFactory
import com.driezy.medlog.capability.ai.AiCloudConfigResolver
import com.driezy.medlog.data.model.AiAnalysisKind
import com.driezy.medlog.data.model.AiUsageFeature
import com.driezy.medlog.data.model.HealthRecordSource
import com.driezy.medlog.data.model.NetworkType
import com.driezy.medlog.data.model.OcrParseResult
import com.driezy.medlog.data.repository.AiCacheKeyBuilder
import com.driezy.medlog.data.repository.AiCacheRepository
import com.driezy.medlog.data.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthCloudImageAnalysisUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val apiKeyStore: AiApiKeyStore,
    private val cacheRepository: AiCacheRepository,
    private val networkStatusProvider: AiNetworkStatusProvider,
    private val clock: Clock,
) {
    suspend fun availability(): HealthCloudImageAnalysisAvailability {
        val settings = preferencesRepository.settingsFlow.first()
        return HealthCloudImageAnalysisGate.evaluate(
            settings = settings,
            availableProviders = apiKeyStore.availableProviders.value,
            networkType = networkStatusProvider.currentNetworkType(),
        )
    }

    suspend fun analyze(
        imageBytes: ByteArray,
        mimeType: String,
        locale: String = Locale.getDefault().toLanguageTag(),
    ): OcrParseResult = withContext(Dispatchers.IO) {
        val settings = preferencesRepository.settingsFlow.first()
        val networkType = networkStatusProvider.currentNetworkType()
        val availability = HealthCloudImageAnalysisGate.evaluate(
            settings = settings,
            availableProviders = apiKeyStore.availableProviders.value,
            networkType = networkType,
        )
        check(availability.isAvailable) {
            "Cloud image analysis is unavailable: ${availability.reason}"
        }

        val apiKey = apiKeyStore.getApiKey(settings.cloudAiProvider)
            ?: error("Cloud image analysis API key is missing.")
        val config = AiCloudConfigResolver.toProviderConfig(settings, apiKey)
        val identity = config.toHealthAiModelIdentity(settings)
        val inputHash = AiCacheKeyBuilder.sha256(imageBytes)
        val cacheKey = AiCacheKeyBuilder.build(
            kind = AiAnalysisKind.IMAGE_OCR,
            provider = identity.provider,
            model = identity.model,
            promptVersion = HealthAiPromptVersions.IMAGE_OCR,
            inputHash = inputHash,
            locale = locale,
        )
        val result = CachedHealthImageAnalyzer(
            aiChatClient = AiChatClientFactory.create(config),
            cacheRepository = cacheRepository,
            identity = identity,
            networkType = networkType,
        ).analyze(
            HealthImageAnalysisRequest(
                imageBytes = imageBytes,
                mimeType = mimeType,
                locale = locale,
            ),
            nowMillis = clock.millis(),
        )
        result.copy(
            metrics = result.metrics.map { metric ->
                metric.copy(
                    source = HealthRecordSource.CLOUD_OCR,
                    sourceFeature = AiUsageFeature.IMAGE_OCR,
                    sourceProvider = identity.provider,
                    sourceModel = identity.model,
                    sourceCacheKey = cacheKey,
                )
            },
        )
    }
}

@Singleton
class AiNetworkStatusProvider @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun currentNetworkType(): NetworkType {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkType.OFFLINE_UNKNOWN
        val network = connectivityManager.activeNetwork ?: return NetworkType.OFFLINE_UNKNOWN
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.OFFLINE_UNKNOWN
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            else -> NetworkType.OFFLINE_UNKNOWN
        }
    }
}
