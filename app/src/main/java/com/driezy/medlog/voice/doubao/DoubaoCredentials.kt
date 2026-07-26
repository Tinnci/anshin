package com.driezy.medlog.voice.doubao

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class DoubaoDeviceCredentials(
    val deviceId: String = "",
    val installId: String = "",
    val cdid: String = UUID.randomUUID().toString(),
    val openudid: String = generateOpenudid(),
    val clientudid: String = UUID.randomUUID().toString(),
    val token: String = "",
) {
    val isComplete: Boolean get() = deviceId.isNotBlank() && token.isNotBlank()

    companion object {
        private fun generateOpenudid(): String {
            val bytes = ByteArray(8)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

@Singleton
class DoubaoCredentialStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("doubao_asr_credentials", Context.MODE_PRIVATE)

    fun load(): DoubaoDeviceCredentials? {
        val cdid = prefs.getString("cdid", null) ?: return null
        val openudid = prefs.getString("openudid", null) ?: return null
        val clientudid = prefs.getString("clientudid", null) ?: return null
        return DoubaoDeviceCredentials(
            deviceId = prefs.getString("device_id", "").orEmpty(),
            installId = prefs.getString("install_id", "").orEmpty(),
            cdid = cdid,
            openudid = openudid,
            clientudid = clientudid,
            token = prefs.getString("token", "").orEmpty(),
        )
    }

    fun save(credentials: DoubaoDeviceCredentials) {
        prefs.edit {
            putString("device_id", credentials.deviceId)
            putString("install_id", credentials.installId)
            putString("cdid", credentials.cdid)
            putString("openudid", credentials.openudid)
            putString("clientudid", credentials.clientudid)
            putString("token", credentials.token)
        }
    }
}
