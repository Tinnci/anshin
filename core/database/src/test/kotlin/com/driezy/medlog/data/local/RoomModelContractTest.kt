package com.driezy.medlog.data.local

import com.driezy.medlog.data.model.HealthRecord
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.model.MedicationLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RoomModelContractTest {
    @Test
    fun `database module publishes stable persistence entities without application resources`() {
        val medication = Medication(name = "Test", dose = 1.0, doseUnit = "tablet")
        val log = MedicationLog(medicationId = 7L, scheduledTimeMs = 1_000L)
        val record = HealthRecord(type = "WEIGHT", value = 70.0, timestamp = 2_000L)

        assertFalse(medication.isArchived)
        assertEquals(7L, log.medicationId)
        assertEquals("WEIGHT", record.type)
        assertEquals(17, DatabaseSchema.VERSION)
    }
}
