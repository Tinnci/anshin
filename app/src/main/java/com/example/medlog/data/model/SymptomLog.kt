package com.example.medlog.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 症状/副作用日记记录
 *
 * 每条记录代表用户在某一时刻记录的身体感受，可选关联具体药品。
 */
@Entity(tableName = "symptom_logs")
data class SymptomLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 记录时间（毫秒时间戳） */
    val recordedAt: Long = System.currentTimeMillis(),

    /** 整体感受评分（1=很差 / 2=较差 / 3=一般 / 4=良好 / 5=很好） */
    val overallRating: Int = 3,

    /** 症状标签列表（逗号分隔），如"头痛,恶心,疲倦" */
    val symptoms: String = "",

    /** 副作用标签列表（逗号分隔），如"胃部不适,口干" */
    val sideEffects: String = "",

    /** 用户自由填写的备注 */
    val note: String = "",

    /** 可选关联的药品 id（-1 表示不关联） */
    val medicationId: Long = -1L,

    /** 可选关联的药品名称（冗余存储，避免关联查询） */
    val medicationName: String = "",
) {
    /** 症状标签列表（拆分后） */
    val symptomList: List<String>
        get() = symptoms.split(",").map { it.trim() }.filter { it.isNotBlank() }

    /** 副作用标签列表（拆分后） */
    val sideEffectList: List<String>
        get() = sideEffects.split(",").map { it.trim() }.filter { it.isNotBlank() }
}
