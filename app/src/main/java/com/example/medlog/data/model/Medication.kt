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
    /**
     * 按时间估算的备货提醒天数（0 = 禁用）。
     * 当预计剩余天数 < refillReminderDays 时，推送"建议提前备货"通知。
     * 适用于知道大致服药周期的用户，无需精确计数库存。
     */
    val refillReminderDays: Int = 0,

    // ── 其他 ─────────────────────────────────────────────────────────────────
    val notes: String = "",
    val isCustomDrug: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),

    // ── 药品分类扩展（v4） ────────────────────────────────────────────────────
    /** 是否为中成药（来自 tcm_drugs_clean.json） */
    val isTcm: Boolean = false,
    /** 完整分类路径，如"消化道及代谢 > 口腔病药物 > 龋齿预防药" */
    val fullPath: String = "",

    // ── 间隔给药（v6） ─────────────────────────────────────────────────────────
    /**
     * 按固定间隔给药的小时数（0 = 禁用，使用时钟时间）。
     * 适用于需要精确间隔的药物（如某些抗生素、旅行跨时区）。
     * 当 intervalHours > 0 时，通知调度将基于上次服药时间 + 间隔
     * 而非当天固定时钟时间。
     */
    val intervalHours: Int = 0,
)
