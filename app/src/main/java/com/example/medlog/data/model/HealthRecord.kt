package com.example.medlog.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 健康体征记录实体。
 * - 血压：value = 收缩压 (mmHg), secondaryValue = 舒张压; display: "120/80 mmHg"
 * - 血糖：value = mmol/L; secondaryValue = null
 * - 体重：value = kg; secondaryValue = null
 * - 心率：value = bpm; secondaryValue = null
 * - 体温：value = °C; secondaryValue = null
 * - 血氧：value = %; secondaryValue = null
 */
@Entity(tableName = "health_records")
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
)

/**
 * 健康体征类型枚举，包含中文标签、单位、正常范围描述。
 */
enum class HealthType(
    val label: String,
    val unit: String,
    /** 正常下限（主值）*/
    val normalMin: Double,
    /** 正常上限（主值）*/
    val normalMax: Double,
    /** 次值正常下限（血压舒张压）*/
    val normalSecMin: Double? = null,
    /** 次值正常上限（血压舒张压）*/
    val normalSecMax: Double? = null,
) {
    BLOOD_PRESSURE(
        label = "血压",
        unit = "mmHg",
        normalMin = 90.0,
        normalMax = 120.0,
        normalSecMin = 60.0,
        normalSecMax = 80.0,
    ),
    BLOOD_GLUCOSE(
        label = "血糖",
        unit = "mmol/L",
        normalMin = 3.9,
        normalMax = 6.1,
    ),
    WEIGHT(
        label = "体重",
        unit = "kg",
        normalMin = 0.0,
        normalMax = Double.MAX_VALUE,   // weight has no universal "normal" range
    ),
    HEART_RATE(
        label = "心率",
        unit = "bpm",
        normalMin = 60.0,
        normalMax = 100.0,
    ),
    TEMPERATURE(
        label = "体温",
        unit = "°C",
        normalMin = 36.1,
        normalMax = 37.3,
    ),
    SPO2(
        label = "血氧",
        unit = "%",
        normalMin = 95.0,
        normalMax = 100.0,
    );

    /** 判断给定的主值是否在正常范围内 */
    fun isNormal(value: Double): Boolean = value in normalMin..normalMax

    /** 格式化显示值字符串 */
    fun formatValue(value: Double, secondaryValue: Double?): String = when (this) {
        BLOOD_PRESSURE -> if (secondaryValue != null) {
            "${value.toInt()}/${secondaryValue.toInt()} $unit"
        } else {
            "${value.toInt()} $unit"
        }
        TEMPERATURE    -> "%.1f $unit".format(value)
        BLOOD_GLUCOSE  -> "%.1f $unit".format(value)
        WEIGHT         -> "%.1f $unit".format(value)
        else           -> "${value.toInt()} $unit"
    }

    companion object {
        fun fromName(name: String): HealthType = entries.firstOrNull { it.name == name } ?: BLOOD_PRESSURE
    }
}
