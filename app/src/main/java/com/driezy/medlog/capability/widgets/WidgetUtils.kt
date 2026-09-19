package com.driezy.medlog.capability.widgets

import com.driezy.medlog.data.repository.reminderZone
import kotlinx.coroutines.flow.first

/** 解析 "HH:mm,HH:mm,..." 字符串为 (小时, 分钟) 对列表 */
internal fun parseReminderTimes(timesStr: String): List<Pair<Int, Int>> = timesStr.split(",").mapNotNull { token ->
    val parts = token.trim().split(":")
    if (parts.size >= 2) {
        val h = parts[0].toIntOrNull() ?: return@mapNotNull null
        val m = parts[1].toIntOrNull() ?: return@mapNotNull null
        Pair(h, m)
    } else {
        null
    }
}

internal data class WidgetDose(val first: Long, val second: String, val third: Int, val scheduledAtMs: Long)
internal data class WidgetPlan(val total: Int, val handled: Int, val pending: List<WidgetDose>, val minuteOfDay: Int)

internal suspend fun WidgetEntryPoint.todayPlan(): WidgetPlan {
    val clock = clock()
    val prefs = preferences().settingsFlow.first()
    val zone = prefs.reminderZone(clock.zone)
    val today = java.time.LocalDate.now(clock.withZone(zone))
    val meds = medicationRepository().getAllMedications().first()
    val logs = logRepository().getLogsForRangeOnce(
        0L,
        today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
    )
    val plans = com.driezy.medlog.feature.medications.application.FuturePlanCalculator(clock).calculate(
        meds,
        1,
        clock.instant(),
        zone,
        revisions = medicationRepository().observePlanRevisions().first(),
        logs = logs,
    )
    val pending = plans.groupBy { it.medication.id }.flatMap { (id, slots) ->
        val matching = com.driezy.medlog.feature.medications.application.matchDoseLogsToSlots(
            slots.map { it.scheduledAt.toEpochMilli() },
            logs.filter {
                it.medicationId == id &&
                    java.time.Instant.ofEpochMilli(it.scheduledTimeMs).atZone(zone).toLocalDate() == today
            },
        )
        slots.mapIndexedNotNull { i, slot ->
            if (matching[i]?.status in
                setOf(
                    com.driezy.medlog.data.model.LogStatus.TAKEN,
                    com.driezy.medlog.data.model.LogStatus.PARTIAL,
                    com.driezy.medlog.data.model.LogStatus.SKIPPED,
                )
            ) {
                null
            } else {
                WidgetDose(
                    id,
                    slot.medication.name,
                    slot.scheduledAt.atZone(zone).toLocalTime().toSecondOfDay() / 60,
                    slot.scheduledAt.toEpochMilli(),
                )
            }
        }
    }.sortedBy { it.scheduledAtMs }
    return WidgetPlan(
        plans.size,
        plans.size - pending.size,
        pending,
        clock.instant().atZone(zone).toLocalTime().toSecondOfDay() / 60,
    )
}
