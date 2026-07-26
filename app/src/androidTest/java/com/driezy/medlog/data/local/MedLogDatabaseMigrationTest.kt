package com.driezy.medlog.data.local

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
    fun migrate12To15PreservesHealthRowsAndValidatesSchema() {
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
            15,
            true,
            MedLogDatabase.MIGRATION_12_13,
            MedLogDatabase.MIGRATION_13_14,
            MedLogDatabase.MIGRATION_14_15,
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

    private fun SupportSQLiteDatabase.use(block: (SupportSQLiteDatabase) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }

    private companion object {
        const val TEST_DATABASE = "medlog-migration-test"
    }
}
