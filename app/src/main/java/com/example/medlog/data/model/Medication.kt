package com.example.medlog.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用药记录实体
 * 对应数据库 medications 表
 */
@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dose: Double,
    val doseUnit: String,           // 片 / 粒 / ml
    val category: String,
    val timePeriod: String = "exact", // exact / morning / afterBreakfast / …
    val reminderHour: Int = 8,
    val reminderMinute: Int = 0,
    val daysOfWeek: String = "1,2,3,4,5,6,7",  // 1=周一…7=周日，逗号分隔
    val stock: Double? = null,
    val refillThreshold: Double? = null,
    val isArchived: Boolean = false,
    val isCustomDrug: Boolean = false,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
