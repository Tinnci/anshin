package com.example.medlog.data.local

import androidx.room.TypeConverter
import com.example.medlog.data.model.LogStatus

class Converters {
    @TypeConverter
    fun fromLogStatus(value: LogStatus): String = value.name

    @TypeConverter
    fun toLogStatus(value: String): LogStatus = LogStatus.valueOf(value)
}
