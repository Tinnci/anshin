package com.example.medlog.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用药配置实体（SSOT — 唯一真实来源）
 * database: medications v3
 */
@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dose: Double,
    val doseUnit: String,                        // 片 / 粒 / ml ...
    val category: String = "",
    val form: String = "tablet",                 // tablet / capsule / liquid / powder

    // ── 优先级 ───────────────────────────────────────────────────────────────
    val isHighPriority: Boolean = false,

    // ── 频率 ─────────────────────────────────────────────────────────────────
    val frequencyType: String = "daily",         // daily | interval | specific_days
    val frequencyInterval: Int = 1,              // 每隔 N 天（interval 类型）
    val frequencyDays: String = "1,2,3,4,5,6,7",// 周一~周日（specific_days，1~7）

    // ── 时段 & 提醒 ──────────────────────────────────────────────────────────
    val timePeriod: String = "exact",            // TimePeriod.key
    val reminderTimes: String = "08:00",         // 逗号分隔的 HH:mm 列表，exact 类型用
    val reminderHour: Int = 8,                   // 主提醒小时（向后兼容通知调度）
    val reminderMinute: Int = 0,

    // ── 剂量 ─────────────────────────────────────────────────────────────────
    val doseQuantity: Double = 1.0,              // 每次几片 / 粒
    val isPRN: Boolean = false,                  // 按需用药
    val maxDailyDose: Double? = null,            // PRN 日最大量

    // ── 起止日期 ─────────────────────────────────────────────────────────────
    val startDate: Long = System.currentTimeMillis(), // 毫秒时间戳
    val endDate: Long? = null,

    // ── 库存 ─────────────────────────────────────────────────────────────────
    val stock: Double? = null,
    val refillThreshold: Double? = null,

    // ── 其他 ─────────────────────────────────────────────────────────────────
    val notes: String = "",
    val isCustomDrug: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
