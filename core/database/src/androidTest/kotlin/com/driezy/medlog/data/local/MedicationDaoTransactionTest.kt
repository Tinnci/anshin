package com.driezy.medlog.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.driezy.medlog.data.model.Medication
import kotlinx.coroutines.flow.first
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

    @Test
    fun planChangesKeepOldScheduleAndStockWritesDoNotCreateRevisions() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MedLogDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val dao = database.medicationDao()
            val id = dao.insertMedication(medication("existing").copy(startDate = 0, stock = 10.0))
            val original = dao.getMedicationById(id)!!
            dao.updatePlan(original.copy(reminderTimes = "20:00", doseQuantity = 2.0), 1000)
            dao.updateStock(id, 8.0)
            val revisions = dao.observePlanRevisions().first()
            assertEquals(1, revisions.size)
            assertEquals("08:00", revisions.single().reminderTimes)
            assertEquals(1.0, revisions.single().doseQuantity, 0.0)
            assertEquals(1000L, revisions.single().effectiveUntilMs)
            assertEquals(1000L, dao.getMedicationById(id)!!.planEffectiveFromMs)
            dao.updatePlan(dao.getMedicationById(id)!!.copy(isArchived = true), 2000)
            dao.updatePlan(dao.getMedicationById(id)!!.copy(isArchived = false), 3000)
            assertEquals(listOf(false, false, true), dao.observePlanRevisions().first().map { it.isArchived })
            assertEquals(8.0, dao.getMedicationById(id)!!.stock!!, 0.0)
        } finally {
            database.close()
        }
    }

    @Test
    fun planRevisionAndCurrentScheduleRollbackTogether() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MedLogDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val dao = database.medicationDao()
            val id = dao.insertMedication(medication("existing"))
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_update BEFORE UPDATE ON medications BEGIN SELECT RAISE(ABORT, 'simulated failure'); END",
            )
            assertTrue(
                runCatching {
                    dao.updatePlan(dao.getMedicationById(id)!!.copy(reminderTimes = "20:00"), 1000)
                }.isFailure,
            )
            assertTrue(dao.observePlanRevisions().first().isEmpty())
            assertEquals("08:00", dao.getMedicationById(id)!!.reminderTimes)
        } finally {
            database.close()
        }
    }

    private fun medication(name: String) = Medication(name = name, dose = 1.0, doseUnit = "tablet")
}
