package com.driezy.medlog.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MedLogDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MedLogDatabase::class.java,
    )

    @After
    fun deleteDatabase() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrateEarliestSupportedVersion5ThroughEveryHistoricalMigration() {
        createVersion5Database()

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            DatabaseSchema.VERSION,
            true,
            MedLogDatabase.MIGRATION_5_6,
            MedLogDatabase.MIGRATION_6_7,
            MedLogDatabase.MIGRATION_7_8,
            MedLogDatabase.MIGRATION_8_9,
            MedLogDatabase.MIGRATION_9_10,
            MedLogDatabase.MIGRATION_10_11,
            MedLogDatabase.MIGRATION_11_12,
            MedLogDatabase.MIGRATION_12_13,
            MedLogDatabase.MIGRATION_13_14,
            MedLogDatabase.MIGRATION_14_15,
            MedLogDatabase.MIGRATION_15_16,
            MedLogDatabase.MIGRATION_16_17,
        ).use { database ->
            database.query("SELECT name, intervalHours, refillReminderDays FROM medications WHERE id = 1").use {
                check(it.moveToFirst())
                assertEquals("legacy medication", it.getString(0))
                assertEquals(0, it.getInt(1))
                assertEquals(0, it.getInt(2))
            }
            database.query(
                "SELECT status, actualDoseQuantity, revisionType FROM medication_logs WHERE id = 1",
            ).use {
                check(it.moveToFirst())
                assertEquals("TAKEN", it.getString(0))
                assertEquals(true, it.isNull(1))
                assertEquals("ORIGINAL", it.getString(2))
            }
        }
    }

    @Test
    fun migrate12To16PreservesHealthRowsAndValidatesSchema() {
        helper.createDatabase(TEST_DATABASE, 12).use { database ->
            database.execSQL(
                """
                INSERT INTO health_records (
                    id,
                    type,
                    value,
                    secondaryValue,
                    timestamp,
                    notes
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(7L, "BLOOD_PRESSURE", 120.0, 80.0, 1_717_000_000_000L, "legacy row"),
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            DatabaseSchema.VERSION,
            true,
            MedLogDatabase.MIGRATION_12_13,
            MedLogDatabase.MIGRATION_13_14,
            MedLogDatabase.MIGRATION_14_15,
            MedLogDatabase.MIGRATION_15_16,
            MedLogDatabase.MIGRATION_16_17,
        ).use { database ->
            database.query(
                "SELECT id, type, value, secondaryValue, timestamp, notes FROM health_records WHERE id = 7",
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(7L, cursor.getLong(0))
                assertEquals("BLOOD_PRESSURE", cursor.getString(1))
                assertEquals(120.0, cursor.getDouble(2), 0.0)
                assertEquals(80.0, cursor.getDouble(3), 0.0)
                assertEquals(1_717_000_000_000L, cursor.getLong(4))
                assertEquals("legacy row", cursor.getString(5))
            }
        }
    }

    @Test
    fun migrate15To16DeduplicatesOccurrencesAndCreatesUniqueIndex() {
        helper.createDatabase(TEST_DATABASE, 15).use { database ->
            database.execSQL("PRAGMA foreign_keys = OFF")
            repeat(2) { index ->
                database.execSQL(
                    """
                    INSERT INTO medication_logs (
                        id, medicationId, scheduledTimeMs, actualTakenTimeMs, status, notes,
                        actualDoseQuantity, createdAtMs, updatedAtMs, revisionType
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any?>(index + 1L, 42L, 1_717_000_000_000L, null, "TAKEN", "", null, 0L, null, "ORIGINAL"),
                )
            }
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            DatabaseSchema.VERSION,
            true,
            MedLogDatabase.MIGRATION_15_16,
            MedLogDatabase.MIGRATION_16_17,
        ).use { database ->
            database.query(
                "SELECT COUNT(*), MAX(id) FROM medication_logs WHERE medicationId = 42 AND scheduledTimeMs = 1717000000000",
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertEquals(2L, cursor.getLong(1))
            }
            database.query("PRAGMA index_list('medication_logs')").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val uniqueIndex = cursor.getColumnIndexOrThrow("unique")
                var occurrenceIndexIsUnique = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == "index_medication_logs_medicationId_scheduledTimeMs") {
                        occurrenceIndexIsUnique = cursor.getInt(uniqueIndex) == 1
                    }
                }
                assertEquals(true, occurrenceIndexIsUnique)
            }
        }
    }

    private fun SupportSQLiteDatabase.use(block: (SupportSQLiteDatabase) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }

    private fun createVersion5Database() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DATABASE), null).use { database ->
            database.execSQL(
                """
                CREATE TABLE medications (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    dose REAL NOT NULL,
                    doseUnit TEXT NOT NULL,
                    category TEXT NOT NULL,
                    form TEXT NOT NULL,
                    isHighPriority INTEGER NOT NULL,
                    frequencyType TEXT NOT NULL,
                    frequencyInterval INTEGER NOT NULL,
                    frequencyDays TEXT NOT NULL,
                    timePeriod TEXT NOT NULL,
                    reminderTimes TEXT NOT NULL,
                    reminderHour INTEGER NOT NULL,
                    reminderMinute INTEGER NOT NULL,
                    doseQuantity REAL NOT NULL,
                    isPRN INTEGER NOT NULL,
                    maxDailyDose REAL,
                    startDate INTEGER NOT NULL,
                    endDate INTEGER,
                    stock REAL,
                    refillThreshold REAL,
                    notes TEXT NOT NULL,
                    isCustomDrug INTEGER NOT NULL,
                    isArchived INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    isTcm INTEGER NOT NULL,
                    fullPath TEXT NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TABLE medication_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    medicationId INTEGER NOT NULL,
                    scheduledTimeMs INTEGER NOT NULL,
                    actualTakenTimeMs INTEGER,
                    status TEXT NOT NULL,
                    notes TEXT NOT NULL,
                    FOREIGN KEY(medicationId) REFERENCES medications(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE INDEX index_medication_logs_medicationId ON medication_logs (medicationId)",
            )
            database.execSQL(
                "CREATE INDEX index_medication_logs_scheduledTimeMs ON medication_logs (scheduledTimeMs)",
            )
            database.execSQL(
                """
                CREATE TABLE symptom_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    recordedAt INTEGER NOT NULL,
                    overallRating INTEGER NOT NULL,
                    symptoms TEXT NOT NULL,
                    sideEffects TEXT NOT NULL,
                    note TEXT NOT NULL,
                    medicationId INTEGER NOT NULL,
                    medicationName TEXT NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO medications VALUES (
                    1, 'legacy medication', 1.0, 'tablet', '', 'tablet', 0,
                    'daily', 1, '1,2,3,4,5,6,7', 'exact', '08:00', 8, 0,
                    1.0, 0, NULL, 1700000000000, NULL, 30.0, 5.0, '', 0, 0,
                    1700000000000, 0, ''
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO medication_logs VALUES (
                    1, 1, 1700000000000, 1700000000000, 'TAKEN', ''
                )
                """.trimIndent(),
            )
            database.version = 5
        }
    }

    private companion object {
        const val TEST_DATABASE = "medlog-migration-test"
    }
}
