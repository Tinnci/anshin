package com.example.medlog.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.medlog.data.model.Medication
import com.example.medlog.data.model.MedicationLog

@Database(
    entities = [Medication::class, MedicationLog::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MedLogDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationLogDao(): MedicationLogDao

    companion object {
        /**
         * v1 → v2: 为 medications 表添加 Flutter 迁移过来的新字段
         * 所有字段都有默认值，现有数据不受影响
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN form TEXT NOT NULL DEFAULT 'tablet'")
                db.execSQL("ALTER TABLE medications ADD COLUMN reminderTimes TEXT")
                db.execSQL("ALTER TABLE medications ADD COLUMN frequencyType TEXT NOT NULL DEFAULT 'daily'")
                db.execSQL("ALTER TABLE medications ADD COLUMN frequencyInterval INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE medications ADD COLUMN doseQuantity INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE medications ADD COLUMN isPRN INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE medications ADD COLUMN maxDailyDose REAL")
                db.execSQL("ALTER TABLE medications ADD COLUMN startDate INTEGER")
                db.execSQL("ALTER TABLE medications ADD COLUMN endDate INTEGER")
            }
        }
    }
}
