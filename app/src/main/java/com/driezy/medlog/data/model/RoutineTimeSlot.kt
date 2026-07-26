package com.driezy.medlog.data.model

/** A user-defined anchor in the daily routine used to calculate relative reminders. */
enum class RoutineTimeSlot {
    WAKE,
    BREAKFAST,
    LUNCH,
    DINNER,
    BED,
}

/** A validated wall-clock time used by routine settings and onboarding. */
data class RoutineTime(val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23) { "hour must be between 0 and 23" }
        require(minute in 0..59) { "minute must be between 0 and 59" }
    }

    fun format24Hour(): String = "%02d:%02d".format(hour, minute)

    companion object {
        internal fun fromStoredOrDefault(hour: Int?, minute: Int?, default: RoutineTime): RoutineTime =
            if (hour in 0..23 && minute in 0..59) {
                RoutineTime(checkNotNull(hour), checkNotNull(minute))
            } else {
                default
            }
    }
}

/** One complete, type-safe set of routine anchors shared by Settings and Welcome. */
data class RoutineSchedule(
    val wake: RoutineTime = RoutineTime(7, 0),
    val breakfast: RoutineTime = RoutineTime(8, 0),
    val lunch: RoutineTime = RoutineTime(12, 0),
    val dinner: RoutineTime = RoutineTime(18, 0),
    val bed: RoutineTime = RoutineTime(22, 0),
) {
    operator fun get(slot: RoutineTimeSlot): RoutineTime = when (slot) {
        RoutineTimeSlot.WAKE -> wake
        RoutineTimeSlot.BREAKFAST -> breakfast
        RoutineTimeSlot.LUNCH -> lunch
        RoutineTimeSlot.DINNER -> dinner
        RoutineTimeSlot.BED -> bed
    }

    fun withTime(slot: RoutineTimeSlot, time: RoutineTime): RoutineSchedule = when (slot) {
        RoutineTimeSlot.WAKE -> copy(wake = time)
        RoutineTimeSlot.BREAKFAST -> copy(breakfast = time)
        RoutineTimeSlot.LUNCH -> copy(lunch = time)
        RoutineTimeSlot.DINNER -> copy(dinner = time)
        RoutineTimeSlot.BED -> copy(bed = time)
    }
}
