package com.example.medlog.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 单次服药 / 跳过 / 漏服事件（SSOT — 日志不可变） */
@Entity(
    tableName = "medication_logs",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("medicationId"), Index("scheduledTimeMs")],
)
data class MedicationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val scheduledTimeMs: Long,           // 计划服药时间戳（ms）
    val actualTakenTimeMs: Long? = null, // 实际服药时间戳，null 表示未服
    val status: LogStatus = LogStatus.TAKEN,
    val notes: String = "",              // 本次服药备注
)

enum class LogStatus { TAKEN, SKIPPED, MISSED }
