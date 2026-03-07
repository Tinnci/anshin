package com.example.medlog.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.medlog.data.model.HealthRecord
import com.example.medlog.data.model.Medication
import com.example.medlog.data.model.MedicationLog
import com.example.medlog.data.model.SymptomLog

@Database(
    entities = [Medication::class, MedicationLog::class, SymptomLog::class, HealthRecord::class],
    version = 12,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MedLogDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationLogDao(): MedicationLogDao
    abstract fun symptomLogDao(): SymptomLogDao
    abstract fun healthRecordDao(): HealthRecordDao

    companion object {
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

        /** v7 → v8: 新增 health_records 表（健康体征记录） */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS health_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        value REAL NOT NULL,
                        secondaryValue REAL,
                        timestamp INTEGER NOT NULL,
                        notes TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** v8 → v9: 为 medications.isArchived 添加索引（加速已存档/未存档过滤查询） */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_medications_isArchived ON medications (isArchived)"
                )
            }
        }

        /** v9 → v10: 为 symptom_logs 和 health_records 添加查询索引 */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_symptom_logs_recordedAt ON symptom_logs (recordedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_symptom_logs_medicationId ON symptom_logs (medicationId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_health_records_type ON health_records (type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_health_records_timestamp ON health_records (timestamp)")
            }
        }

        /** v10 → v11: medication_logs 新增 actualDoseQuantity（部分服用剂量）列 */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medication_logs ADD COLUMN actualDoseQuantity REAL")
            }
        }

        /** v11 → v12: medication_logs 添加复合索引 (medicationId, scheduledTimeMs) 以加速多条件查询 */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_medication_logs_medicationId_scheduledTimeMs ON medication_logs (medicationId, scheduledTimeMs)"
                )
            }
        }
    }
}
