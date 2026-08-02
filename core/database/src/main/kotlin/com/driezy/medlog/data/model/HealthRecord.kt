package com.driezy.medlog.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class HealthRecordSource {
    MANUAL,
    LOCAL_OCR,
    CLOUD_OCR,
    IMPORT,
}

/**
 * 健康体征记录实体。
 * - 血压：value = 收缩压 (mmHg), secondaryValue = 舒张压; display: "120/80 mmHg"
 * - 血糖：value = mmol/L; secondaryValue = null
 * - 体重：value = kg; secondaryValue = null
 * - 体脂率：value = %; secondaryValue = null
 * - 心率：value = bpm; secondaryValue = null
 * - 体温：value = °C; secondaryValue = null
 * - 血氧：value = %; secondaryValue = null
 */
@Entity(
    tableName = "health_records",
    indices = [
        Index("type"),
        Index("timestamp"),
        Index("source"),
    ],
)
data class HealthRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** HealthType.name 字符串存储，以支持未来扩展 */
    val type: String,
    /** 主值（收缩压 / 血糖 / 体重 / 心率 / 体温 / 血氧） */
    val value: Double,
    /** 次值（仅血压的舒张压使用，其余为 null） */
    val secondaryValue: Double? = null,
    /** 记录时间戳（毫秒） */
    val timestamp: Long = System.currentTimeMillis(),
    /** 可选备注 */
    val notes: String = "",
    /** 记录来源：手动、端侧 OCR、云端 OCR 或导入。 */
    val source: HealthRecordSource = HealthRecordSource.MANUAL,
    /** 来源功能。OCR/AI 结果可写入，手动记录为 null。 */
    val sourceFeature: AiUsageFeature? = null,
    /** 云端来源 provider 名称，不保存 API key 或原始响应。 */
    val sourceProvider: String? = null,
    /** 云端来源模型名。 */
    val sourceModel: String? = null,
    /** 用户确认前的识别置信度。 */
    val sourceConfidence: Float? = null,
    /** 规范化 AI 缓存 key；缓存过期后也只用于审计关联，不含原图或原始响应。 */
    val sourceCacheKey: String? = null,
    /** 用户确认保存的时间；历史迁移数据可能为空。 */
    val confirmedAt: Long? = null,
)
