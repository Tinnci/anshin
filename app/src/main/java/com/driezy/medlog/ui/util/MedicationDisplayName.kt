package com.driezy.medlog.ui.util

import com.driezy.medlog.data.model.Medication

private val seedNamePrefix = Regex("""^Seed\s+\S+\s+(.+)$""")

fun Medication.displayName(): String {
    val trimmed = name.trim()
    val stripped = seedNamePrefix.matchEntire(trimmed)?.groupValues?.getOrNull(1)?.trim()
    return stripped?.takeIf { it.isNotBlank() } ?: trimmed
}
