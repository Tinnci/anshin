package com.driezy.medlog.capability.reminders.application

import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.model.RoutineSchedule
import com.driezy.medlog.data.model.RoutineTime
import com.driezy.medlog.data.repository.MedicationRepository
import com.driezy.medlog.domain.ReminderReconcileReason
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ResyncRemindersUseCaseTest {
    @Test
    fun `routine plans are persisted as one atomic batch before projections reconcile`() = runTest {
        val medications: MedicationRepository = mock()
        val reconciler: ReconcileRemindersUseCase = mock()
        val breakfast = medication(id = 1L, timePeriod = "beforeBreakfast", reminderTimes = "08:00")
        val dinner = medication(id = 2L, timePeriod = "afterDinner", reminderTimes = "18:00")
        whenever(medications.getActiveMedications()).thenReturn(flowOf(listOf(breakfast, dinner)))
        val schedule = RoutineSchedule(
            breakfast = RoutineTime(9, 15),
            dinner = RoutineTime(19, 45),
        )

        ResyncRemindersUseCase(medications, reconciler)(schedule)

        val expected = listOf(
            breakfast.copy(reminderTimes = "09:00", reminderHour = 9, reminderMinute = 0),
            dinner.copy(reminderTimes = "20:00", reminderHour = 20, reminderMinute = 0),
        )
        inOrder(medications, reconciler) {
            verify(medications).updateMedications(expected)
            verify(reconciler).all(ReminderReconcileReason.ROUTINE_CHANGED)
        }
        verify(medications, never()).updateMedication(expected.first())
        verify(medications, never()).updateMedication(expected.last())
    }

    @Test
    fun `exact and as-needed plans are not rewritten`() = runTest {
        val medications: MedicationRepository = mock()
        val reconciler: ReconcileRemindersUseCase = mock()
        whenever(medications.getActiveMedications()).thenReturn(
            flowOf(
                listOf(
                    medication(id = 1L, timePeriod = "exact"),
                    medication(id = 2L, timePeriod = "beforeBreakfast", isPrn = true),
                ),
            ),
        )

        ResyncRemindersUseCase(medications, reconciler)(RoutineSchedule())

        verify(medications, never()).updateMedications(any())
        verify(reconciler).all(ReminderReconcileReason.ROUTINE_CHANGED)
    }

    private fun medication(id: Long, timePeriod: String, reminderTimes: String = "08:00", isPrn: Boolean = false) =
        Medication(
            id = id,
            name = "Medication $id",
            dose = 1.0,
            doseUnit = "tablet",
            timePeriod = timePeriod,
            reminderTimes = reminderTimes,
            isPRN = isPrn,
        )
}
