package com.driezy.medlog.ui.screen.home

import com.driezy.medlog.data.model.MedicationLog
import kotlin.math.abs

private const val LEGACY_DOSE_LOG_WINDOW_MS = 4 * 3_600_000L

/**
 * Assigns at most one log to each scheduled slot.
 *
 * New logs use the exact planned timestamp. The nearest-slot fallback keeps logs created by
 * earlier app versions visible without allowing one legacy log to mark two nearby slots handled.
 */
internal fun matchDoseLogsToSlots(scheduledTimeMs: List<Long>, logs: List<MedicationLog>): List<MedicationLog?> {
    if (scheduledTimeMs.isEmpty()) return emptyList()

    val matches = MutableList<MedicationLog?>(scheduledTimeMs.size) { null }
    val exactSlotTimes = scheduledTimeMs.toSet()

    scheduledTimeMs.forEachIndexed { slotIndex, slotMs ->
        matches[slotIndex] = logs
            .asSequence()
            .filter { it.scheduledTimeMs == slotMs }
            .maxWithOrNull(compareBy<MedicationLog>({ it.actualTakenTimeMs ?: Long.MIN_VALUE }, { it.id }))
    }

    val fallbackPairs = logs
        .asSequence()
        .filterNot { it.scheduledTimeMs in exactSlotTimes }
        .flatMap { log ->
            scheduledTimeMs.asSequence().mapIndexed { slotIndex, slotMs ->
                Triple(abs(log.scheduledTimeMs - slotMs), slotIndex, log)
            }
        }
        .filter { (distance, _, _) -> distance < LEGACY_DOSE_LOG_WINDOW_MS }
        .sortedWith(
            compareBy<Triple<Long, Int, MedicationLog>>(
                { it.first },
                { it.second },
                { -(it.third.actualTakenTimeMs ?: it.third.scheduledTimeMs) },
            ),
        )
        .toList()

    val assignedLogIds = mutableSetOf<Long>()
    val assignedLogInstances = mutableSetOf<MedicationLog>()
    fallbackPairs.forEach { (_, slotIndex, log) ->
        val alreadyAssigned = if (log.id != 0L) log.id in assignedLogIds else log in assignedLogInstances
        if (matches[slotIndex] == null && !alreadyAssigned) {
            matches[slotIndex] = log
            if (log.id != 0L) assignedLogIds += log.id else assignedLogInstances += log
        }
    }
    return matches
}
