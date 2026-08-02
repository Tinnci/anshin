package com.driezy.medlog.domain.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

@JvmInline
value class MedicationId(val value: Long) {
    init {
        require(value > 0) { "MedicationId must be a persisted positive identifier" }
    }
}

data class DoseOccurrenceId(val medicationId: MedicationId, val scheduledAt: Instant)

sealed interface ScheduleRecurrence {
    data object Daily : ScheduleRecurrence

    data class EveryDays(val days: Int) : ScheduleRecurrence {
        init {
            require(days > 0) { "Recurrence interval must be positive" }
        }
    }

    data class Weekdays(val days: Set<DayOfWeek>) : ScheduleRecurrence {
        init {
            require(days.isNotEmpty()) { "At least one weekday is required" }
        }
    }
}

enum class RoutineAnchor {
    MORNING,
    AFTER_BREAKFAST,
    BEFORE_LUNCH,
    AFTER_LUNCH,
    BEFORE_DINNER,
    AFTER_DINNER,
    EVENING,
    BEDTIME,
    BEFORE_BREAKFAST,
    AFTERNOON,
}

sealed interface MedicationSchedule {
    data class ExactTimes(val times: List<LocalTime>, val recurrence: ScheduleRecurrence) : MedicationSchedule {
        init {
            require(times.isNotEmpty()) { "At least one exact time is required" }
        }
    }

    data class RoutineAnchored(
        val anchor: RoutineAnchor,
        val resolvedTime: LocalTime,
        val recurrence: ScheduleRecurrence,
    ) : MedicationSchedule

    data class Interval(val every: Duration) : MedicationSchedule {
        init {
            require(!every.isZero && !every.isNegative) { "Interval must be positive" }
        }
    }

    data object AsNeeded : MedicationSchedule
}
