package com.example.medlog.data.repository

import app.cash.turbine.test
import com.example.medlog.data.model.Medication
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeMedicationRepositoryTest {

    private lateinit var repo: FakeMedicationRepository

    /** 最小化构造的测试用药 */
    private fun medication(name: String = "TestMed", archived: Boolean = false) =
        Medication(name = name, dose = 1.0, doseUnit = "片", isArchived = archived)

    @Before
    fun setUp() {
        repo = FakeMedicationRepository()
    }

    // ── addMedication / getActiveMedications ─────────────────────────────────

    @Test
    fun `addMedication emits new item in active list`() = runTest {
        repo.addMedication(medication("Aspirin"))

        repo.getActiveMedications().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("Aspirin", list.first().name)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `addMedication assigns unique incremental ids`() = runTest {
        val id1 = repo.addMedication(medication("Med A"))
        val id2 = repo.addMedication(medication("Med B"))
        assertTrue(id2 > id1)
    }

    // ── archiveMedication / unarchiveMedication ───────────────────────────────

    @Test
    fun `archiveMedication moves item out of active list`() = runTest {
        val id = repo.addMedication(medication("Vitamin C"))
        repo.archiveMedication(id)

        repo.getActiveMedications().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `archiveMedication adds item to archived list`() = runTest {
        val id = repo.addMedication(medication("Vitamin C"))
        repo.archiveMedication(id)

        repo.getArchivedMedications().test {
            assertEquals(1, awaitItem().size)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `unarchiveMedication restores item to active list`() = runTest {
        val id = repo.addMedication(medication("Metformin"))
        repo.archiveMedication(id)
        repo.unarchiveMedication(id)

        repo.getActiveMedications().test {
            assertEquals(1, awaitItem().size)
            cancelAndConsumeRemainingEvents()
        }
        repo.getArchivedMedications().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    // ── updateMedication ──────────────────────────────────────────────────────

    @Test
    fun `updateMedication replaces existing entry`() = runTest {
        val id = repo.addMedication(medication("Old Name"))
        val updated = repo.getMedicationById(id)!!.copy(name = "New Name")
        repo.updateMedication(updated)

        assertEquals("New Name", repo.getMedicationById(id)?.name)
    }

    // ── deleteMedication ──────────────────────────────────────────────────────

    @Test
    fun `deleteMedication removes item from all lists`() = runTest {
        val id = repo.addMedication(medication("Ibuprofen"))
        val med = repo.getMedicationById(id)!!
        repo.deleteMedication(med)

        assertNull(repo.getMedicationById(id))
        repo.getAllMedications().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    // ── updateStock ───────────────────────────────────────────────────────────

    @Test
    fun `updateStock changes stock value`() = runTest {
        val id = repo.addMedication(medication("Metformin"))
        repo.updateStock(id, 30.0)

        assertEquals(30.0, repo.getMedicationById(id)?.stock)
    }

    @Test
    fun `updateStock does not affect other fields`() = runTest {
        val id = repo.addMedication(medication("Lisinopril"))
        repo.updateStock(id, 15.0)

        val med = repo.getMedicationById(id)!!
        assertEquals("Lisinopril", med.name)
        assertEquals(1.0, med.dose, 0.001)
    }
}
