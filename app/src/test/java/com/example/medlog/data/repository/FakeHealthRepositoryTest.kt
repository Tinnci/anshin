package com.example.medlog.data.repository

import app.cash.turbine.test
import com.example.medlog.data.model.HealthRecord
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeHealthRepositoryTest {

    private lateinit var repo: FakeHealthRepository

    private fun record(
        type: String = "HEART_RATE",
        value: Double = 72.0,
        timestamp: Long = System.currentTimeMillis(),
    ) = HealthRecord(type = type, value = value, timestamp = timestamp)

    @Before
    fun setUp() {
        repo = FakeHealthRepository()
    }

    @Test
    fun `addRecord emits new item via getAllRecords`() = runTest {
        repo.addRecord(record())

        repo.getAllRecords().test {
            assertEquals(1, awaitItem().size)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `addRecord assigns unique incremental ids`() = runTest {
        val id1 = repo.addRecord(record())
        val id2 = repo.addRecord(record())
        assertTrue(id2 > id1)
    }

    @Test
    fun `getRecordsByType filters correctly`() = runTest {
        repo.addRecord(record(type = "HEART_RATE"))
        repo.addRecord(record(type = "BLOOD_PRESSURE"))
        repo.addRecord(record(type = "HEART_RATE"))

        repo.getRecordsByType("HEART_RATE").test {
            assertEquals(2, awaitItem().size)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getRecordsInRange filters by timestamp`() = runTest {
        repo.addRecord(record(timestamp = 100))
        repo.addRecord(record(timestamp = 200))
        repo.addRecord(record(timestamp = 300))

        repo.getRecordsInRange(150, 250).test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals(200L, items.first().timestamp)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `deleteRecord removes from list`() = runTest {
        val id = repo.addRecord(record())

        repo.getAllRecords().test {
            val added = awaitItem()
            assertEquals(1, added.size)

            repo.deleteRecord(added.first())
            assertEquals(0, awaitItem().size)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `updateRecord modifies existing`() = runTest {
        repo.addRecord(record(value = 72.0))

        repo.getAllRecords().test {
            val original = awaitItem().first()
            repo.updateRecord(original.copy(value = 80.0))
            assertEquals(80.0, awaitItem().first().value, 0.01)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getLatestRecordPerType returns one per type`() = runTest {
        repo.addRecord(record(type = "HEART_RATE", timestamp = 100))
        repo.addRecord(record(type = "HEART_RATE", timestamp = 200))
        repo.addRecord(record(type = "WEIGHT", timestamp = 150))

        repo.getLatestRecordPerType().test {
            val items = awaitItem()
            assertEquals(2, items.size)
            val hr = items.first { it.type == "HEART_RATE" }
            assertEquals(200L, hr.timestamp)
            cancelAndConsumeRemainingEvents()
        }
    }
}
