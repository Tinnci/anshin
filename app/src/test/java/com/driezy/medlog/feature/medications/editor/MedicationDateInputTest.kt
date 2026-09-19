package com.driezy.medlog.feature.medications.editor

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class MedicationDateInputTest {
    @Test fun `picker dates round trip without shifting days in positive or negative offsets`() {
        val date = LocalDate.of(2026, 3, 8)
        val picked = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        for (zone in listOf(ZoneId.of("Asia/Shanghai"), ZoneId.of("America/New_York"))) {
            val stored = picked.toMedicationDateMillis(zone)
            assertEquals(date, Instant.ofEpochMilli(stored).atZone(zone).toLocalDate())
            assertEquals(picked, stored.toDatePickerMillis(zone))
        }
    }

    @Test fun `end date includes the starting day even if the start instant was midday`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val day = LocalDate.of(2026, 9, 19)
        val state = AddMedicationUiState(
            name = "Medication",
            doseUnit = "tablet",
            dateZoneId = zone.id,
            startDate = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            endDate = day.atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        assertNull(state.validationError())
    }
}
