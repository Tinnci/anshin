package com.driezy.medlog.ui.screen.health

import com.driezy.medlog.data.model.HealthRecord
import com.driezy.medlog.data.model.HealthType
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthRecordPresentationTest {
    @Test
    fun `health record presentation hides seeded technical notes`() {
        val record = HealthRecord(
            type = HealthType.BLOOD_GLUCOSE.name,
            value = 5.8,
            notes = "seed:standard:health:glucose",
        )

        assertEquals("", record.userVisibleNotes())
    }

    @Test
    fun `health record presentation keeps user notes`() {
        val record = HealthRecord(
            type = HealthType.BLOOD_GLUCOSE.name,
            value = 5.8,
            notes = "饭后两小时",
        )

        assertEquals("饭后两小时", record.userVisibleNotes())
    }
}
