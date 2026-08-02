package com.driezy.medlog.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.driezy.medlog.data.model.Medication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MedicationDaoTransactionTest {
    @Test
    fun replaceActiveMedicationsRollsBackDeletionWhenAnInsertFails() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MedLogDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val dao = database.medicationDao()
            dao.insertMedication(medication("existing"))
            database.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER fail_replacement
                BEFORE INSERT ON medications
                WHEN NEW.name = 'failure'
                BEGIN
                    SELECT RAISE(ABORT, 'simulated insert failure');
                END
                """.trimIndent(),
            )

            val failure = runCatching {
                dao.replaceActiveMedications(listOf(medication("replacement"), medication("failure")))
            }

            assertTrue(failure.isFailure)
            assertEquals(listOf("existing"), dao.getAllMedicationsOnce().map(Medication::name))
        } finally {
            database.close()
        }
    }

    private fun medication(name: String) = Medication(name = name, dose = 1.0, doseUnit = "tablet")
}
