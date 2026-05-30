package com.driezy.medlog.voice.doubao

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoubaoDeviceClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val store: DoubaoCredentialStore,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun ensureCredentials(): Result<DoubaoDeviceCredentials> = withContext(Dispatchers.IO) {
        runCatching {
            var credentials = store.load() ?: DoubaoDeviceCredentials().also(store::save)
            if (credentials.deviceId.isBlank()) {
                credentials = registerDevice(credentials)
                store.save(credentials)
            }
            if (credentials.token.isBlank()) {
                credentials = fetchAsrToken(credentials)
                store.save(credentials)
            }
            credentials
        }
    }

    private fun registerDevice(credentials: DoubaoDeviceCredentials): DoubaoDeviceCredentials {
        val url = DoubaoAsrConstants.REGISTER_URL.toHttpUrlWithCommonParams(credentials)
        val body = DeviceRegisterBody(
            header = DeviceRegisterHeader(
                cdid = credentials.cdid,
                openudid = credentials.openudid,
                clientudid = credentials.clientudid,
            ),
            genTime = nowMs(),
        )
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DoubaoAsrConstants.USER_AGENT)
            .post(json.encodeToString(DeviceRegisterBody.serializer(), body).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val response = okHttpClient.newCall(request).execute()
        response.use {
            check(it.isSuccessful) { "Device registration failed: ${it.code}" }
            val responseBody = checkNotNull(it.body) { "Device registration returned empty body" }.string()
            val result = json.decodeFromString(DeviceRegisterResponse.serializer(), responseBody)
            check(result.deviceId != 0L) { "Device registration returned empty device id" }
            return credentials.copy(
                deviceId = result.deviceId.toString(),
                installId = result.installId.toString(),
            )
        }
    }

    private fun fetchAsrToken(credentials: DoubaoDeviceCredentials): DoubaoDeviceCredentials {
        val body = "body=null"
        val url = DoubaoAsrConstants.SETTINGS_URL.toHttpUrlWithCommonParams(credentials, includeDeviceId = true)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DoubaoAsrConstants.USER_AGENT)
            .header("x-ss-stub", body.md5Uppercase())
            .post(body.toRequestBody("text/plain".toMediaType()))
            .build()
        val response = okHttpClient.newCall(request).execute()
        response.use {
            check(it.isSuccessful) { "Settings request failed: ${it.code}" }
            val responseBody = checkNotNull(it.body) { "Settings response returned empty body" }.string()
            val result = json.decodeFromString(SettingsResponse.serializer(), responseBody)
            val token = result.data.settings.asrConfig.appKey
            check(token.isNotBlank()) { "Settings response did not include app_key" }
            return credentials.copy(token = token)
        }
    }

    private fun String.toHttpUrlWithCommonParams(
        credentials: DoubaoDeviceCredentials,
        includeDeviceId: Boolean = false,
    ) = okhttp3.HttpUrl.Builder()
        .scheme(substringBefore("://"))
        .host(substringAfter("://").substringBefore("/"))
        .apply {
            addPathSegments(substringAfter("://").substringAfter("/", ""))
            addQueryParameter("device_platform", DoubaoAsrConstants.DEVICE_PLATFORM)
            addQueryParameter("os", DoubaoAsrConstants.OS)
            addQueryParameter("ssmix", "a")
            addQueryParameter("_rticket", nowMs().toString())
            addQueryParameter("cdid", credentials.cdid)
            addQueryParameter("channel", DoubaoAsrConstants.CHANNEL)
            addQueryParameter("aid", DoubaoAsrConstants.AID.toString())
            addQueryParameter("app_name", DoubaoAsrConstants.APP_NAME)
            addQueryParameter("version_code", DoubaoAsrConstants.VERSION_CODE.toString())
            addQueryParameter("version_name", DoubaoAsrConstants.VERSION_NAME)
            addQueryParameter("manifest_version_code", DoubaoAsrConstants.VERSION_CODE.toString())
            addQueryParameter("update_version_code", DoubaoAsrConstants.VERSION_CODE.toString())
            addQueryParameter("resolution", DoubaoAsrConstants.RESOLUTION)
            addQueryParameter("dpi", DoubaoAsrConstants.DPI)
            addQueryParameter("device_type", DoubaoAsrConstants.DEVICE_TYPE)
            addQueryParameter("device_brand", DoubaoAsrConstants.DEVICE_BRAND)
            addQueryParameter("language", DoubaoAsrConstants.LANGUAGE)
            addQueryParameter("os_api", DoubaoAsrConstants.OS_API)
            addQueryParameter("os_version", DoubaoAsrConstants.OS_VERSION)
            addQueryParameter("ac", "wifi")
            if (includeDeviceId) addQueryParameter("device_id", credentials.deviceId)
        }
        .build()

    private fun nowMs(): Long = System.currentTimeMillis()

    private fun String.md5Uppercase(): String {
        val digest = MessageDigest.getInstance("MD5").digest(toByteArray())
        return digest.joinToString("") { "%02X".format(it) }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class DeviceRegisterBody(
    @SerialName("magic_tag") val magicTag: String = "ss_app_log",
    val header: DeviceRegisterHeader,
    @SerialName("_gen_time") val genTime: Long,
)

@Serializable
private data class DeviceRegisterHeader(
    @SerialName("device_id") val deviceId: Long = 0,
    @SerialName("install_id") val installId: Long = 0,
    val aid: Int = DoubaoAsrConstants.AID,
    @SerialName("app_name") val appName: String = DoubaoAsrConstants.APP_NAME,
    @SerialName("version_code") val versionCode: Int = DoubaoAsrConstants.VERSION_CODE,
    @SerialName("version_name") val versionName: String = DoubaoAsrConstants.VERSION_NAME,
    @SerialName("manifest_version_code") val manifestVersionCode: Int = DoubaoAsrConstants.VERSION_CODE,
    @SerialName("update_version_code") val updateVersionCode: Int = DoubaoAsrConstants.VERSION_CODE,
    val channel: String = DoubaoAsrConstants.CHANNEL,
    @SerialName("package") val packageName: String = DoubaoAsrConstants.PACKAGE,
    @SerialName("device_platform") val devicePlatform: String = DoubaoAsrConstants.DEVICE_PLATFORM,
    val os: String = DoubaoAsrConstants.OS,
    @SerialName("os_api") val osApi: String = DoubaoAsrConstants.OS_API,
    @SerialName("os_version") val osVersion: String = DoubaoAsrConstants.OS_VERSION,
    @SerialName("device_type") val deviceType: String = DoubaoAsrConstants.DEVICE_TYPE,
    @SerialName("device_brand") val deviceBrand: String = DoubaoAsrConstants.DEVICE_BRAND,
    @SerialName("device_model") val deviceModel: String = DoubaoAsrConstants.DEVICE_MODEL,
    val resolution: String = DoubaoAsrConstants.RESOLUTION,
    val dpi: String = DoubaoAsrConstants.DPI,
    val language: String = DoubaoAsrConstants.LANGUAGE,
    val timezone: Int = DoubaoAsrConstants.TIMEZONE,
    val access: String = DoubaoAsrConstants.ACCESS,
    val rom: String = DoubaoAsrConstants.ROM,
    @SerialName("rom_version") val romVersion: String = DoubaoAsrConstants.ROM_VERSION,
    val openudid: String,
    val clientudid: String,
    val cdid: String,
    val region: String = "CN",
    @SerialName("tz_name") val tzName: String = "Asia/Shanghai",
    @SerialName("tz_offset") val tzOffset: Int = 28_800,
    @SerialName("sim_region") val simRegion: String = "cn",
    @SerialName("carrier_region") val carrierRegion: String = "cn",
    @SerialName("cpu_abi") val cpuAbi: String = "arm64-v8a",
    @SerialName("build_serial") val buildSerial: String = "unknown",
    @SerialName("not_request_sender") val notRequestSender: Int = 0,
    @SerialName("sig_hash") val sigHash: String = "",
    @SerialName("google_aid") val googleAid: String = "",
    val mc: String = "",
    @SerialName("serial_number") val serialNumber: String = "",
)

@Serializable
private data class DeviceRegisterResponse(
    @SerialName("device_id") val deviceId: Long = 0,
    @SerialName("install_id") val installId: Long = 0,
)

@Serializable
private data class SettingsResponse(val data: SettingsData)

@Serializable
private data class SettingsData(val settings: Settings)

@Serializable
private data class Settings(
    @SerialName("asr_config") val asrConfig: AsrConfig,
)

@Serializable
private data class AsrConfig(
    @SerialName("app_key") val appKey: String = "",
)
