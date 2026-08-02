package com.driezy.medlog.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.driezy.medlog.data.model.AiAnalysisCacheEntry
import com.driezy.medlog.data.model.AiUsageEvent
import com.driezy.medlog.data.model.HealthRecord
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.model.MedicationLog
import com.driezy.medlog.data.model.SymptomLog

@Database(
    entities = [
        Medication::class,
        MedicationLog::class,
        SymptomLog::class,
        HealthRecord::class,
        AiAnalysisCacheEntry::class,
        AiUsageEvent::class,
    ],
    version = DatabaseSchema.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MedLogDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationLogDao(): MedicationLogDao
    abstract fun symptomLogDao(): SymptomLogDao
    abstract fun healthRecordDao(): HealthRecordDao
    abstract fun aiAnalysisCacheDao(): AiAnalysisCacheDao
    abstract fun aiUsageEventDao(): AiUsageEventDao

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
                    """.trimIndent(),
                )
            }
        }

        /** v8 → v9: 为 medications.isArchived 添加索引（加速已存档/未存档过滤查询） */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_medications_isArchived ON medications (isArchived)",
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
                    "CREATE INDEX IF NOT EXISTS index_medication_logs_medicationId_scheduledTimeMs ON medication_logs (medicationId, scheduledTimeMs)",
                )
            }
        }

        /** v12 → v13: 新增 AI 结果缓存和轻量本地审计表 */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ai_analysis_cache (
                        cacheKey TEXT NOT NULL PRIMARY KEY,
                        kind TEXT NOT NULL,
                        provider TEXT NOT NULL,
                        model TEXT NOT NULL,
                        promptVersion INTEGER NOT NULL,
                        inputHash TEXT NOT NULL,
                        locale TEXT NOT NULL,
                        responseJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        expiresAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_analysis_cache_kind ON ai_analysis_cache (kind)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_ai_analysis_cache_expiresAt ON ai_analysis_cache (expiresAt)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_ai_analysis_cache_kind_createdAt ON ai_analysis_cache (kind, createdAt)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ai_usage_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        feature TEXT NOT NULL,
                        provider TEXT NOT NULL,
                        model TEXT NOT NULL,
                        networkType TEXT NOT NULL,
                        cacheHit INTEGER NOT NULL,
                        result TEXT NOT NULL,
                        errorCategory TEXT,
                        inputHashPrefix TEXT,
                        latencyMs INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_usage_events_timestamp ON ai_usage_events (timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_usage_events_feature ON ai_usage_events (feature)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_usage_events_result ON ai_usage_events (result)")
            }
        }

        /** v13 → v14: 健康记录增加来源 provenance，用于区分手动、本地 OCR、云端 OCR 和导入记录 */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE health_records ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL'")
                db.execSQL("ALTER TABLE health_records ADD COLUMN sourceFeature TEXT")
                db.execSQL("ALTER TABLE health_records ADD COLUMN sourceProvider TEXT")
                db.execSQL("ALTER TABLE health_records ADD COLUMN sourceModel TEXT")
                db.execSQL("ALTER TABLE health_records ADD COLUMN sourceConfidence REAL")
                db.execSQL("ALTER TABLE health_records ADD COLUMN sourceCacheKey TEXT")
                db.execSQL("ALTER TABLE health_records ADD COLUMN confirmedAt INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_health_records_source ON health_records (source)")
            }
        }

        /** v14 → v15: medication_logs 增加修订元数据，用于区分当天编辑与过期后补改。 */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medication_logs ADD COLUMN createdAtMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE medication_logs ADD COLUMN updatedAtMs INTEGER")
                db.execSQL("ALTER TABLE medication_logs ADD COLUMN revisionType TEXT NOT NULL DEFAULT 'ORIGINAL'")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_medication_logs_revisionType ON medication_logs (revisionType)",
                )
            }
        }

        /** v15 → v16: one medication can have at most one log for a scheduled occurrence. */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM medication_logs
                    WHERE id NOT IN (
                        SELECT MAX(id)
                        FROM medication_logs
                        GROUP BY medicationId, scheduledTimeMs
                    )
                    """.trimIndent(),
                )
                db.execSQL("DROP INDEX IF EXISTS index_medication_logs_medicationId_scheduledTimeMs")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_medication_logs_medicationId_scheduledTimeMs
                    ON medication_logs (medicationId, scheduledTimeMs)
                    """.trimIndent(),
                )
            }
        }
    }
}
