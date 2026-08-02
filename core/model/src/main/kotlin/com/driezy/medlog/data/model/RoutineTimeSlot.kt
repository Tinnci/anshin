package com.driezy.medlog.data.model

import com.driezy.medlog.domain.model.RoutineAnchor
import java.time.LocalTime

enum class RoutineTimeSlot { WAKE, BREAKFAST, LUNCH, DINNER, BED }

data class RoutineTime(val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23) { "hour must be between 0 and 23" }
        require(minute in 0..59) { "minute must be between 0 and 59" }
    }

    fun format24Hour(): String = "%02d:%02d".format(hour, minute)

    companion object {
        fun fromStoredOrDefault(hour: Int?, minute: Int?, default: RoutineTime): RoutineTime =
            if (hour in 0..23 && minute in 0..59) {
                RoutineTime(checkNotNull(hour), checkNotNull(minute))
            } else {
                default
            }
    }
}

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

/** Resolves semantic medication anchors without exposing persistence keys to business logic. */
fun RoutineSchedule.resolve(anchor: RoutineAnchor): LocalTime = when (anchor) {
    RoutineAnchor.MORNING -> wake.toLocalTime()
    RoutineAnchor.BEFORE_BREAKFAST -> breakfast.toLocalTime().minusMinutes(15)
    RoutineAnchor.AFTER_BREAKFAST -> breakfast.toLocalTime().plusMinutes(15)
    RoutineAnchor.BEFORE_LUNCH -> lunch.toLocalTime().minusMinutes(15)
    RoutineAnchor.AFTER_LUNCH -> lunch.toLocalTime().plusMinutes(15)
    RoutineAnchor.BEFORE_DINNER -> dinner.toLocalTime().minusMinutes(15)
    RoutineAnchor.AFTER_DINNER -> dinner.toLocalTime().plusMinutes(15)
    RoutineAnchor.AFTERNOON -> LocalTime.of(15, 0)
    RoutineAnchor.EVENING -> bed.toLocalTime().minusHours(1)
    RoutineAnchor.BEDTIME -> bed.toLocalTime()
}

fun RoutineTime.toLocalTime(): LocalTime = LocalTime.of(hour, minute)
