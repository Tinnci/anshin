package com.driezy.medlog.device.bpx1

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Bpx1DeviceConfiguration(
    val macAddress: String = "",
    val hasBindKey: Boolean = false,
    val autoImport: Boolean = true,
) {
    val isConfigured: Boolean get() = Bpx1Protocol.isValidMac(macAddress) && hasBindKey
}

interface Bpx1DeviceStore {
    val configuration: StateFlow<Bpx1DeviceConfiguration>

    suspend fun getBindKey(): ByteArray?
    suspend fun save(macAddress: String, bindKey: ByteArray?)
    suspend fun setAutoImport(enabled: Boolean)
    suspend fun clear()
}

@Singleton
class AndroidKeystoreBpx1DeviceStore @Inject constructor(
    @ApplicationContext context: Context,
) : Bpx1DeviceStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _configuration = MutableStateFlow(loadConfiguration())

    override val configuration: StateFlow<Bpx1DeviceConfiguration> = _configuration

    override suspend fun getBindKey(): ByteArray? {
        val encrypted = preferences.getString(KEY_BIND_KEY, null) ?: return null
        val bindKey = runCatching { decrypt(encrypted) }
            .onFailure { Log.w(TAG, "Failed to decrypt the BPX1 bind key.", it) }
            .getOrNull()
            ?.let(Bpx1Protocol::decodeBindKey)
        if (bindKey == null) {
            preferences.edit { remove(KEY_BIND_KEY) }
            publish()
        }
        return bindKey
    }

    override suspend fun save(macAddress: String, bindKey: ByteArray?) {
        val normalizedMac = Bpx1Protocol.normalizeMac(macAddress)
        require(Bpx1Protocol.isValidMac(normalizedMac)) { "Invalid BPX1 MAC address." }
        require(bindKey == null || bindKey.size == 16) { "The BPX1 bind key must be 16 bytes." }
        preferences.edit {
            putString(KEY_MAC_ADDRESS, normalizedMac)
            if (bindKey != null) putString(KEY_BIND_KEY, encrypt(bindKey.toHex()))
        }
        publish()
    }

    override suspend fun setAutoImport(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_AUTO_IMPORT, enabled) }
        publish()
    }

    override suspend fun clear() {
        preferences.edit {
            remove(KEY_MAC_ADDRESS)
            remove(KEY_BIND_KEY)
        }
        publish()
    }

    private fun loadConfiguration() = Bpx1DeviceConfiguration(
        macAddress = preferences.getString(KEY_MAC_ADDRESS, "").orEmpty(),
        hasBindKey = !preferences.getString(KEY_BIND_KEY, null).isNullOrBlank(),
        autoImport = preferences.getBoolean(KEY_AUTO_IMPORT, true),
    )

    private fun publish() {
        _configuration.value = loadConfiguration()
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
        require(parts.size == 3 && parts[0] == VERSION) { "Unsupported BPX1 key payload." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_BITS, decoder.decode(parts[1])),
        )
        return cipher.doFinal(decoder.decode(parts[2])).toString(Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val TAG = "Bpx1DeviceStore"
        const val PREFERENCES_NAME = "medlog_bpx1_device"
        const val KEY_MAC_ADDRESS = "mac_address"
        const val KEY_BIND_KEY = "bind_key"
        const val KEY_AUTO_IMPORT = "auto_import"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "medlog_bpx1_bind_key_aes_gcm"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION = "v1"
        const val GCM_TAG_BITS = 128
        const val KEY_SIZE_BITS = 256
        val encoder: Base64.Encoder = Base64.getEncoder()
        val decoder: Base64.Decoder = Base64.getDecoder()
    }
}

class InMemoryBpx1DeviceStore(
    initial: Bpx1DeviceConfiguration = Bpx1DeviceConfiguration(),
    bindKey: ByteArray? = null,
) : Bpx1DeviceStore {
    private val _configuration = MutableStateFlow(initial.copy(hasBindKey = bindKey != null || initial.hasBindKey))
    private var key = bindKey?.copyOf()

    override val configuration: StateFlow<Bpx1DeviceConfiguration> = _configuration

    override suspend fun getBindKey(): ByteArray? = key?.copyOf()

    override suspend fun save(macAddress: String, bindKey: ByteArray?) {
        if (bindKey != null) key = bindKey.copyOf()
        _configuration.value = _configuration.value.copy(
            macAddress = Bpx1Protocol.normalizeMac(macAddress),
            hasBindKey = key != null,
        )
    }

    override suspend fun setAutoImport(enabled: Boolean) {
        _configuration.value = _configuration.value.copy(autoImport = enabled)
    }

    override suspend fun clear() {
        key = null
        _configuration.value = _configuration.value.copy(macAddress = "", hasBindKey = false)
    }
}

private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
