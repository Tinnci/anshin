package com.example.medlog.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.medlog.data.model.Medication
import com.example.medlog.data.model.MedicationLog
import com.example.medlog.data.model.SymptomLog

@Database(
    entities = [Medication::class, MedicationLog::class, SymptomLog::class],
    version = 7,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MedLogDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationLogDao(): MedicationLogDao
    abstract fun symptomLogDao(): SymptomLogDao

    companion object {
        @Volatile private var INSTANCE: MedLogDatabase? = null

        /** v5 → v6: 添加 intervalHours 列（间隔给药小时数） */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN intervalHours INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v6 → v7: 添加 refillReminderDays 列（按天数估算备货提醒） */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN refillReminderDays INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Widget 和其他非-DI 场景下的单例访问器。
         * Hilt 应用内仍由 [AppModule] 提供注入版本。
         */
        fun getInstance(context: Context): MedLogDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room
                    .databaseBuilder(context.applicationContext, MedLogDatabase::class.java, "medlog.db")
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
