package com.driezy.medlog.capability.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.core.content.edit
import com.driezy.medlog.data.repository.CloudAiProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidKeystoreAiApiKeyStore @Inject constructor(@ApplicationContext context: Context) : AiApiKeyStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _availableProviders = MutableStateFlow(loadAvailableProviders())

    override val availableProviders: StateFlow<Set<CloudAiProvider>> = _availableProviders

    override fun hasApiKey(provider: CloudAiProvider): Boolean =
        !preferences.getString(provider.preferenceKey(), null).isNullOrBlank()

    override suspend fun getApiKey(provider: CloudAiProvider): String? {
        val encrypted = preferences.getString(provider.preferenceKey(), null) ?: return null
        return runCatching { decrypt(encrypted) }
            .onFailure { Log.w(TAG, "Failed to decrypt API key for ${provider.name}", it) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    override suspend fun setApiKey(provider: CloudAiProvider, apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) {
            clearApiKey(provider)
            return
        }
        preferences.edit {
            putString(provider.preferenceKey(), encrypt(trimmed))
        }
        publishAvailability()
    }

    override suspend fun clearApiKey(provider: CloudAiProvider) {
        preferences.edit {
            remove(provider.preferenceKey())
        }
        publishAvailability()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey(), SecureRandom())
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return listOf(
            VERSION,
            encoder.encodeToString(cipher.iv),
            encoder.encodeToString(cipherText),
        ).joinToString(":")
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(":")
        require(parts.size == 3 && parts[0] == VERSION) {
            "Unsupported AI API key payload."
        }
        val iv = decoder.decode(parts[1])
        val cipherText = decoder.decode(parts[2])
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText).toString(Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun CloudAiProvider.preferenceKey(): String = "api_key_${name.lowercase()}"

    private fun loadAvailableProviders(): Set<CloudAiProvider> = CloudAiProvider.entries
        .filter { hasApiKey(it) }
        .toSet()

    private fun publishAvailability() {
        _availableProviders.value = loadAvailableProviders()
    }

    private companion object {
        const val TAG = "AiApiKeyStore"
        const val PREFERENCES_NAME = "medlog_ai_api_keys"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "medlog_ai_api_key_aes_gcm"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION = "v1"
        const val GCM_TAG_BITS = 128
        const val KEY_SIZE_BITS = 256
        val encoder: Base64.Encoder = Base64.getEncoder()
        val decoder: Base64.Decoder = Base64.getDecoder()
    }
}
