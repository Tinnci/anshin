package com.example.medlog.data.repository

import app.cash.turbine.test
import com.example.medlog.data.model.SymptomLog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeSymptomRepositoryTest {

    private lateinit var repo: FakeSymptomRepository

    private fun log(
        medicationId: Long = 1L,
        symptoms: String = "头痛",
        recordedAt: Long = System.currentTimeMillis(),
    ) = SymptomLog(medicationId = medicationId, symptoms = symptoms, recordedAt = recordedAt)

    @Before
    fun setUp() {
        repo = FakeSymptomRepository()
    }

    @Test
    fun `insert emits new item via getAllLogs`() = runTest {
        repo.insert(log())

        repo.getAllLogs().test {
            assertEquals(1, awaitItem().size)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `insert assigns unique incremental ids`() = runTest {
        val id1 = repo.insert(log())
        val id2 = repo.insert(log())
        assertTrue(id2 > id1)
    }

    @Test
    fun `getLogsForMedication filters by medicationId`() = runTest {
        repo.insert(log(medicationId = 1))
        repo.insert(log(medicationId = 2))
        repo.insert(log(medicationId = 1))

        repo.getLogsForMedication(1L).test {
            assertEquals(2, awaitItem().size)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getLogsForDateRange filters by timestamp`() = runTest {
        repo.insert(log(recordedAt = 100))
        repo.insert(log(recordedAt = 200))
        repo.insert(log(recordedAt = 300))

        repo.getLogsForDateRange(150, 250).test {
            assertEquals(1, awaitItem().size)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `delete removes from list`() = runTest {
        repo.insert(log())

        repo.getAllLogs().test {
            val items = awaitItem()
            assertEquals(1, items.size)

            repo.delete(items.first())
            assertEquals(0, awaitItem().size)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `deleteById removes by id`() = runTest {
        val id = repo.insert(log())

        repo.getAllLogs().test {
            assertEquals(1, awaitItem().size)

            repo.deleteById(id)
            assertEquals(0, awaitItem().size)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `update modifies existing log`() = runTest {
        repo.insert(log(symptoms = "头痛"))

        repo.getAllLogs().test {
            val original = awaitItem().first()
            repo.update(original.copy(symptoms = "头晕"))
            assertEquals("头晕", awaitItem().first().symptoms)
            cancelAndConsumeRemainingEvents()
        }
    }
}
