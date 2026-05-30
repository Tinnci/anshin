package com.driezy.medlog.ui.screen.health

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class HealthSheetStateTest {
    @Test
    fun `add edit sheet supports every modal sheet transition target`() {
        assertTrue(HealthRecordSheetEnabledStates.contains(SheetValue.Hidden))
        assertTrue(HealthRecordSheetEnabledStates.contains(SheetValue.PartiallyExpanded))
        assertTrue(HealthRecordSheetEnabledStates.contains(SheetValue.Expanded))
        assertEquals(3, HealthRecordSheetEnabledStates.size)
    }
}
