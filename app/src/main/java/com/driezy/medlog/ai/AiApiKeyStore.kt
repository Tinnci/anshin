package com.driezy.medlog.ai

import com.driezy.medlog.data.repository.CloudAiProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface AiApiKeyStore : AiApiKeyAvailability {
    val availableProviders: StateFlow<Set<CloudAiProvider>>
    suspend fun getApiKey(provider: CloudAiProvider): String?
    suspend fun setApiKey(provider: CloudAiProvider, apiKey: String)
    suspend fun clearApiKey(provider: CloudAiProvider)
}

class InMemoryAiApiKeyStore : AiApiKeyStore {
    private val keys = mutableMapOf<CloudAiProvider, String>()
    private val _availableProviders = MutableStateFlow(emptySet<CloudAiProvider>())

    override val availableProviders: StateFlow<Set<CloudAiProvider>> = _availableProviders

    override fun hasApiKey(provider: CloudAiProvider): Boolean = !keys[provider].isNullOrBlank()

    override suspend fun getApiKey(provider: CloudAiProvider): String? = keys[provider]?.takeIf { it.isNotBlank() }

    override suspend fun setApiKey(provider: CloudAiProvider, apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) {
            clearApiKey(provider)
        } else {
            keys[provider] = trimmed
            publishAvailability()
        }
    }

    override suspend fun clearApiKey(provider: CloudAiProvider) {
        keys.remove(provider)
        publishAvailability()
    }

    private fun publishAvailability() {
        _availableProviders.value = keys
            .filterValues { it.isNotBlank() }
            .keys
            .toSet()
    }
}
