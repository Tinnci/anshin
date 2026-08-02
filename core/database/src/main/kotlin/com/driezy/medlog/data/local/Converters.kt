package com.driezy.medlog.data.local

import androidx.room.TypeConverter
import com.driezy.medlog.data.model.AiUsageFeature
import com.driezy.medlog.data.model.HealthRecordSource
import com.driezy.medlog.data.model.LogRevisionType
import com.driezy.medlog.data.model.LogStatus

class Converters {
    @TypeConverter
    fun fromLogStatus(value: LogStatus): String = value.name

    @TypeConverter
    fun toLogStatus(value: String): LogStatus = LogStatus.valueOf(value)

    @TypeConverter
    fun fromLogRevisionType(value: LogRevisionType): String = value.name

    @TypeConverter
    fun toLogRevisionType(value: String): LogRevisionType =
        LogRevisionType.entries.firstOrNull { it.name == value } ?: LogRevisionType.ORIGINAL

    @TypeConverter
    fun fromHealthRecordSource(value: HealthRecordSource): String = value.name

    @TypeConverter
    fun toHealthRecordSource(value: String): HealthRecordSource =
        HealthRecordSource.entries.firstOrNull { it.name == value } ?: HealthRecordSource.MANUAL

    @TypeConverter
    fun fromAiUsageFeature(value: AiUsageFeature?): String? = value?.name

    @TypeConverter
    fun toAiUsageFeature(value: String?): AiUsageFeature? =
        value?.let { raw -> AiUsageFeature.entries.firstOrNull { it.name == raw } }
}
