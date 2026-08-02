package com.driezy.medlog.feature.medications.home

/** Stable identity for one scheduled dose, including multi-slot medications. */
data class MedicationDoseKey(val medicationId: Long, val timeSlotIndex: Int)

val MedicationWithStatus.doseKey: MedicationDoseKey
    get() = MedicationDoseKey(
        medicationId = medication.id,
        timeSlotIndex = timeSlotIndex,
    )

enum class HomeHeroStatus {
    NO_PLAN,
    ACTION_REQUIRED,
    ALL_TAKEN,
    HANDLED_WITH_EXCEPTIONS,
}

/**
 * UI-facing single source of truth shared by every home Hero style.
 *
 * Progress intentionally counts handled doses, while taken/skipped/partial remain separate.
 * This avoids a completed day looking unfinished after the user skips or partially takes a dose.
 */
data class HomeHeroPresentation(
    val status: HomeHeroStatus,
    val scheduledItems: List<MedicationWithStatus>,
    val pendingItems: List<MedicationWithStatus>,
    val nextPendingItem: MedicationWithStatus?,
    val totalCount: Int,
    val handledCount: Int,
    val takenCount: Int,
    val skippedCount: Int,
    val partialCount: Int,
) {
    val pendingCount: Int = pendingItems.size
    val progressFraction: Float = if (totalCount == 0) {
        0f
    } else {
        handledCount.toFloat() / totalCount
    }

    companion object {
        fun from(items: List<MedicationWithStatus>): HomeHeroPresentation {
            val scheduledItems = items
                .asSequence()
                .filterNot { it.medication.isPRN }
                .sortedWith(
                    compareBy<MedicationWithStatus>(
                        { it.scheduledMinutes() },
                        { it.medication.name },
                        { it.medication.id },
                        { it.timeSlotIndex },
                    ),
                )
                .toList()
            val pendingItems = scheduledItems.filterNot { it.isHandled }
            val takenCount = scheduledItems.count { it.isTaken }
            val skippedCount = scheduledItems.count { it.isSkipped }
            val partialCount = scheduledItems.count { it.isPartial }
            val handledCount = takenCount + skippedCount + partialCount
            val status = when {
                scheduledItems.isEmpty() -> HomeHeroStatus.NO_PLAN
                pendingItems.isNotEmpty() -> HomeHeroStatus.ACTION_REQUIRED
                takenCount == scheduledItems.size -> HomeHeroStatus.ALL_TAKEN
                else -> HomeHeroStatus.HANDLED_WITH_EXCEPTIONS
            }
            return HomeHeroPresentation(
                status = status,
                scheduledItems = scheduledItems,
                pendingItems = pendingItems,
                nextPendingItem = pendingItems.firstOrNull(),
                totalCount = scheduledItems.size,
                handledCount = handledCount,
                takenCount = takenCount,
                skippedCount = skippedCount,
                partialCount = partialCount,
            )
        }
    }
}

internal fun MedicationWithStatus.displayTime(): String = scheduledTime.ifBlank {
    "%02d:%02d".format(medication.reminderHour, medication.reminderMinute)
}

internal fun MedicationWithStatus.scheduledMinuteOfDay(): Int {
    val fallbackHour = medication.reminderHour.coerceIn(0, 23)
    val fallbackMinute = medication.reminderMinute.coerceIn(0, 59)
    val parts = displayTime()
        .split(":")
        .mapNotNull(String::toIntOrNull)
    val hour = parts.getOrElse(0) { fallbackHour }.coerceIn(0, 23)
    val minute = parts.getOrElse(1) { fallbackMinute }.coerceIn(0, 59)
    return hour * 60 + minute
}

private fun MedicationWithStatus.scheduledMinutes(): Int = scheduledMinuteOfDay()
