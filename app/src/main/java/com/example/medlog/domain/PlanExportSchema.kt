package com.example.medlog.domain

import com.example.medlog.data.model.Medication
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 服药计划导出/导入的序列化模型（SSOT）。
 *
 * 设计目标：
 * - 字段名尽量短（单/双字母），以降低 JSON 体积
 * - 默认值字段在序列化时省略（encodeDefaults = false）
 * - 版本化（v 字段）以兼容未来 schema 迭代
 * - 压缩管道：JSON → gzip → URL-safe Base64 → "anshin:v1:<data>"
 *
 * 容量估算：10 种典型药品 ≈ 900B JSON → ≈400B gzip → ≈530B base64 → QR可行
 */

/** 单个药品导出条目（字段名为短别名以节省体积） */
@Serializable
data class MedExportEntry(
    @SerialName("n")  val name: String,
    @SerialName("d")  val dose: Double,              // 兼容旧 dose 字段 = doseQuantity
    @SerialName("u")  val doseUnit: String,
    @SerialName("tp") val timePeriod: String,
    @SerialName("rt") val reminderTimes: String,
    @SerialName("rh") val reminderHour: Int,
    @SerialName("rm") val reminderMinute: Int,
    @SerialName("dq") val doseQuantity: Double = 1.0,
    @SerialName("ft") val frequencyType: String = "daily",
    @SerialName("cat") val category: String = "",
    @SerialName("form") val form: String = "tablet",
    @SerialName("prn") val isPRN: Boolean = false,
    @SerialName("hp")  val isHighPriority: Boolean = false,
    @SerialName("fi")  val frequencyInterval: Int = 1,
    @SerialName("fd")  val frequencyDays: String = "1,2,3,4,5,6,7",
    @SerialName("sd")  val startDate: String = "",   // "YYYY-MM-DD"
    @SerialName("ed")  val endDate: String? = null,  // "YYYY-MM-DD" or null
    @SerialName("stk") val stock: Double? = null,
    @SerialName("rt2") val refillThreshold: Double? = null,
    @SerialName("rrd") val refillReminderDays: Int = 0,
    @SerialName("notes") val notes: String = "",
    @SerialName("mdx") val maxDailyDose: Double? = null,
    @SerialName("ih")  val intervalHours: Int = 0,
)

/** 导出包顶层结构 */
@Serializable
data class PlanExport(
    @SerialName("v")   val version: Int = 1,
    @SerialName("app") val app: String = "anshin",
    @SerialName("meds") val meds: List<MedExportEntry>,
)

/** 导入行为：合并、替换或取消 */
enum class ImportMode { MERGE, REPLACE }

/**
 * 编解码器：在 Medication 列表与压缩 URI 串之间相互转换。
 *
 * 编码格式：`anshin:v1:<url-safe-base64-no-padding>`
 * 解码时：若前缀不匹配则返回 null（兼容二维码直接扫描普通文本的误触）。
 */
object PlanExportCodec {

    private val sdf: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val jsonSerializer = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false      // 省略默认值以减小体积
        prettyPrint = false
        explicitNulls = false       // 省略 null 字段
    }

    const val SCHEME = "anshin:v1:"

    /** 将活跃药品列表编码为压缩 URI 串；失败时返回 null */
    fun encode(medications: List<Medication>): String? = runCatching {
        val entries = medications.filter { !it.isArchived }.map { it.toEntry() }
        val payload = jsonSerializer.encodeToString(PlanExport(meds = entries))
        val gzipped = gzip(payload.toByteArray(Charsets.UTF_8))
        SCHEME + Base64.getUrlEncoder().withoutPadding().encodeToString(gzipped)
    }.getOrNull()

    /** 解码压缩 URI 串为 PlanExport；前缀不匹配或格式错误时返回 null */
    fun decode(encoded: String): PlanExport? = runCatching {
        if (!encoded.startsWith(SCHEME)) return null
        val b64 = encoded.removePrefix(SCHEME)
        val gzipped = Base64.getUrlDecoder().decode(b64)
        val jsonStr = gunzip(gzipped).toString(Charsets.UTF_8)
        jsonSerializer.decodeFromString<PlanExport>(jsonStr)
    }.getOrNull()

    /**
     * 编码后的字符串是否足够短以在 QR 码中显示。
     * QR Version 40 / Error Correction L 可容纳约 2953 字节 binary。
     * 以 2900 作为保守阈值。
     */
    fun canDisplayAsQr(encoded: String): Boolean = encoded.length <= 2900

    /** 估算编码字节数，用于 UI 提示 */
    fun estimatedBytes(encoded: String): Int {
        if (!encoded.startsWith(SCHEME)) return 0
        return encoded.length - SCHEME.length
    }

    // ── 内部转换 ──────────────────────────────────────────────────────────────

    private fun Medication.toEntry() = MedExportEntry(
        name           = name,
        dose           = doseQuantity,          // 兼容旧字段
        doseUnit       = doseUnit,
        timePeriod     = timePeriod,
        reminderTimes  = reminderTimes,
        reminderHour   = reminderHour,
        reminderMinute = reminderMinute,
        doseQuantity   = doseQuantity,
        frequencyType  = frequencyType,
        category       = category,
        form           = form,
        isPRN          = isPRN,
        isHighPriority = isHighPriority,
        frequencyInterval = frequencyInterval,
        frequencyDays  = frequencyDays,
        startDate      = sdf.format(Date(startDate)),
        endDate        = endDate?.let { sdf.format(Date(it)) },
        stock          = stock,
        refillThreshold = refillThreshold,
        refillReminderDays = refillReminderDays,
        notes          = notes,
        maxDailyDose   = maxDailyDose,
        intervalHours  = intervalHours,
    )

    fun MedExportEntry.toMedication(): Medication = Medication(
        name           = name,
        dose           = dose,
        doseUnit       = doseUnit,
        timePeriod     = timePeriod,
        reminderTimes  = reminderTimes,
        reminderHour   = reminderHour,
        reminderMinute = reminderMinute,
        doseQuantity   = doseQuantity,
        frequencyType  = frequencyType,
        category       = category,
        form           = form,
        isPRN          = isPRN,
        isHighPriority = isHighPriority,
        frequencyInterval = frequencyInterval,
        frequencyDays  = frequencyDays,
        startDate      = runCatching { sdf.parse(startDate)?.time ?: System.currentTimeMillis() }
                            .getOrDefault(System.currentTimeMillis()),
        endDate        = endDate?.let { runCatching { sdf.parse(it)?.time }.getOrNull() },
        stock          = stock,
        refillThreshold = refillThreshold,
        refillReminderDays = refillReminderDays,
        notes          = notes,
        maxDailyDose   = maxDailyDose,
        intervalHours  = intervalHours,
    )

    // ── 压缩工具 ──────────────────────────────────────────────────────────────

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}
