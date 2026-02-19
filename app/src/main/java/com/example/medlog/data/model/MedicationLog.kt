package com.example.medlog.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 每次服药 / 跳过 / 漏服的日志 */
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
    indices = [Index("medicationId")],
)
data class MedicationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val scheduledTimeMs: Long,    // 计划服药时间戳
    val actualTakenTimeMs: Long?, // 实际服药时间戳，null 表示未记录
    val status: LogStatus,
)

enum class LogStatus {
    TAKEN,   // 已服用
    SKIPPED, // 跳过
    MISSED,  // 漏服
}
