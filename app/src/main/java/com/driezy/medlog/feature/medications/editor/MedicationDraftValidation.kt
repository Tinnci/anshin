package com.driezy.medlog.feature.medications.editor

import androidx.annotation.StringRes
import com.driezy.medlog.R
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** The wizard and final save use the same validation rules. */
@StringRes
internal fun AddMedicationUiState.validationError(throughStep: Int = 2): Int? {
    if (name.isBlank()) return R.string.error_name_required
    if (throughStep == 0) return null
    if (!doseQuantity.isFinite() || doseQuantity <= 0 || doseUnit.isBlank()) return R.string.error_dose_invalid
    if (isPRN && maxDailyDose.isNotBlank() && !maxDailyDose.isPositiveNumber()) return R.string.error_daily_dose_invalid
    if (!isPRN) {
        if (intervalHours < 0 || frequencyInterval <= 0) return R.string.error_schedule_invalid
        if (frequencyType == "specific_days" &&
            frequencyDays.split(",").none { it.toIntOrNull() in 1..7 }
        ) {
            return R.string.error_schedule_invalid
        }
        if (reminderTimes.isEmpty() ||
            reminderTimes.size > 20 ||
            reminderTimes.any { runCatching { LocalTime.parse(it) }.isFailure }
        ) {
            return R.string.error_schedule_invalid
        }
    }
    if (throughStep == 1) return null
    val zone = ZoneId.of(dateZoneId)
    if (endDate != null &&
        Instant.ofEpochMilli(endDate).atZone(zone).toLocalDate() <
        Instant.ofEpochMilli(startDate).atZone(zone).toLocalDate()
    ) {
        return R.string.error_date_order
    }
    if (listOf(stock, refillThreshold).any {
            it.isNotBlank() && !it.isNonNegativeNumber()
        }
    ) {
        return R.string.error_stock_invalid
    }
    return null
}

private fun String.isPositiveNumber(): Boolean = toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true
private fun String.isNonNegativeNumber(): Boolean = toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true
