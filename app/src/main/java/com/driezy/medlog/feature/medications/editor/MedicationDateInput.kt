package com.driezy.medlog.feature.medications.editor

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Material date pickers encode calendar dates at UTC midnight, independently of the reminder zone. */
internal fun Long.toDatePickerMillis(zone: ZoneId): Long = Instant.ofEpochMilli(this)
    .atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.toMedicationDateMillis(zone: ZoneId): Long = Instant.ofEpochMilli(this)
    .atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
