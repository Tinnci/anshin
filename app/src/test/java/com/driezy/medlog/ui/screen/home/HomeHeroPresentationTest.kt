package com.driezy.medlog.ui.screen.home

import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.data.model.MedicationLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class HomeHeroPresentationTest {

    @Test
    fun `empty schedule produces no-plan state`() {
        val presentation = HomeHeroPresentation.from(emptyList())

        assertEquals(HomeHeroStatus.NO_PLAN, presentation.status)
        assertEquals(0, presentation.totalCount)
        assertEquals(0f, presentation.progressFraction)
        assertNull(presentation.nextPendingItem)
    }

    @Test
    fun `single pending dose remains the primary action`() {
        val item = item(id = 1L, scheduledTime = "08:00")

        val presentation = HomeHeroPresentation.from(listOf(item))

        assertEquals(HomeHeroStatus.ACTION_REQUIRED, presentation.status)
        assertEquals(1, presentation.pendingCount)
        assertEquals(item.doseKey, presentation.nextPendingItem?.doseKey)
    }

    @Test
    fun `handled progress includes skipped and partial doses without calling them taken`() {
        val items = listOf(
            item(id = 1L, status = LogStatus.TAKEN, scheduledTime = "08:00"),
            item(id = 2L, status = LogStatus.SKIPPED, scheduledTime = "12:00"),
            item(id = 3L, status = LogStatus.PARTIAL, scheduledTime = "18:00"),
            item(id = 4L, scheduledTime = "22:00"),
        )

        val presentation = HomeHeroPresentation.from(items)

        assertEquals(3, presentation.handledCount)
        assertEquals(1, presentation.takenCount)
        assertEquals(1, presentation.skippedCount)
        assertEquals(1, presentation.partialCount)
        assertEquals(1, presentation.pendingCount)
        assertEquals(0.75f, presentation.progressFraction)
        assertEquals(HomeHeroStatus.ACTION_REQUIRED, presentation.status)
    }

    @Test
    fun `all taken and handled-with-exceptions are distinct terminal states`() {
        val allTaken = HomeHeroPresentation.from(
            listOf(
                item(id = 1L, status = LogStatus.TAKEN),
                item(id = 2L, status = LogStatus.TAKEN),
            ),
        )
        val withSkip = HomeHeroPresentation.from(
            listOf(
                item(id = 1L, status = LogStatus.TAKEN),
                item(id = 2L, status = LogStatus.SKIPPED),
            ),
        )

        assertEquals(HomeHeroStatus.ALL_TAKEN, allTaken.status)
        assertEquals(HomeHeroStatus.HANDLED_WITH_EXCEPTIONS, withSkip.status)
        assertEquals(1f, withSkip.progressFraction)
    }

    @Test
    fun `next pending dose is chronological and excludes PRN medication`() {
        val later = item(id = 1L, scheduledTime = "18:00")
        val next = item(id = 2L, scheduledTime = "08:00")
        val prn = item(id = 3L, scheduledTime = "07:00", isPrn = true)

        val presentation = HomeHeroPresentation.from(listOf(later, prn, next))

        assertEquals(next.doseKey, presentation.nextPendingItem?.doseKey)
        assertEquals(2, presentation.totalCount)
    }

    @Test
    fun `dose key distinguishes slots belonging to the same medication`() {
        val morning = item(id = 7L, slot = 0, scheduledTime = "08:00")
        val noon = item(id = 7L, slot = 1, scheduledTime = "12:00")

        assertEquals(MedicationDoseKey(7L, 0), morning.doseKey)
        assertEquals(MedicationDoseKey(7L, 1), noon.doseKey)
        assertEquals(false, morning.doseKey == noon.doseKey)
    }

    @Test
    fun `timeline position keeps the day endpoints and scheduled time scale stable`() {
        assertEquals(0f, timelineFraction(7 * 60))
        assertEquals(0f, timelineFraction(8 * 60))
        assertEquals(0.5f, timelineFraction(15 * 60))
        assertEquals(1f, timelineFraction(22 * 60))
        assertEquals(1f, timelineFraction(23 * 60))
        assertEquals(8 * 60 + 30, item(id = 1L, scheduledTime = "08:30").scheduledMinuteOfDay())
    }

    @Test
    fun `exact logs stay attached to their own slots`() {
        val morningMs = 1_000_000L
        val noonMs = morningMs + 4 * 3_600_000L
        val morningLog = log(id = 1L, scheduledTimeMs = morningMs)
        val noonLog = log(id = 2L, scheduledTimeMs = noonMs)

        val matches = matchDoseLogsToSlots(
            scheduledTimeMs = listOf(morningMs, noonMs),
            logs = listOf(noonLog, morningLog),
        )

        assertSame(morningLog, matches[0])
        assertSame(noonLog, matches[1])
    }

    @Test
    fun `one legacy log cannot handle two nearby slots`() {
        val morningMs = 1_000_000L
        val noonMs = morningMs + 2 * 3_600_000L
        val legacyLog = log(
            id = 3L,
            scheduledTimeMs = morningMs + 30 * 60_000L,
        )

        val matches = matchDoseLogsToSlots(
            scheduledTimeMs = listOf(morningMs, noonMs),
            logs = listOf(legacyLog),
        )

        assertSame(legacyLog, matches[0])
        assertNull(matches[1])
    }

    private fun item(
        id: Long,
        slot: Int = 0,
        scheduledTime: String = "08:00",
        status: LogStatus? = null,
        isPrn: Boolean = false,
    ): MedicationWithStatus {
        val medication = Medication(
            id = id,
            name = "Medication $id",
            dose = 1.0,
            doseUnit = "tablet",
            reminderTimes = scheduledTime,
            isPRN = isPrn,
        )
        val log = status?.let {
            MedicationLog(
                id = id * 10 + slot,
                medicationId = id,
                scheduledTimeMs = id,
                status = it,
            )
        }
        return MedicationWithStatus(
            medication = medication,
            log = log,
            timeSlotIndex = slot,
            scheduledTime = scheduledTime,
        )
    }

    private fun log(id: Long, scheduledTimeMs: Long) = MedicationLog(
        id = id,
        medicationId = 7L,
        scheduledTimeMs = scheduledTimeMs,
        status = LogStatus.TAKEN,
    )
}
