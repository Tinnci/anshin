package com.example.medlog.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.medlog.data.model.Medication
import com.example.medlog.data.model.MedicationLog

@Database(
    entities = [Medication::class, MedicationLog::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MedLogDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationLogDao(): MedicationLogDao
}
