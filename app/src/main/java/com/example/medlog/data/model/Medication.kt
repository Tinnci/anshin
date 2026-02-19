package com.example.medlog.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用药记录实体
 * 对应数据库 medications 表 (v2)
 */
@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dose: Double,
    val doseUnit: String,               // 片 / 粒 / ml
    val category: String,
    val form: String = "tablet",        // tablet / capsule / liquid / powder / injection
    val timePeriod: String = "exact",   // 服药时段 key (TimePeriod.key)
    // ── 提醒时间（单提醒，兼容旧逻辑）──────────────────
    val reminderHour: Int = 8,
    val reminderMinute: Int = 0,
    // ── 多提醒 / 服药频率 ──────────────────────────────
    val reminderTimes: String? = null,  // JSON 数组，如 ["08:00","20:00"]
    val frequencyType: String = "daily",    // daily | interval | specific_days
    val frequencyInterval: Int = 1,     // 每隔 N 天（interval 类型）
    val daysOfWeek: String = "1,2,3,4,5,6,7", // 1=周一…7=周日（specific_days 类型）
    // ── 剂量 ──────────────────────────────────────────
    val doseQuantity: Int = 1,          // 每次服几片 / 粒
    val isPRN: Boolean = false,         // 按需服用（prn）
    val maxDailyDose: Double? = null,   // 每日最大剂量
    // ── 库存管理 ──────────────────────────────────────
    val stock: Double? = null,
    val refillThreshold: Double? = null,
    // ── 时间范围 ──────────────────────────────────────
    val startDate: Long? = null,        // 开始日期（毫秒时间戳）
    val endDate: Long? = null,          // 结束日期（毫秒时间戳）
    // ── 其他 ──────────────────────────────────────────
    val isArchived: Boolean = false,
    val isCustomDrug: Boolean = false,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

