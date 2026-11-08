package com.driezy.medlog.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.core.app.LocaleManagerCompat
import com.driezy.medlog.data.model.Medication

private val seedNamePrefix = Regex("""^Seed\s+\S+\s+(.+)$""")

@Composable
internal fun medicationNameLanguage(): String {
    val context = LocalContext.current
    val resourceLanguage = LocalLocale.current.platformLocale.language
    val preferred = LocaleManagerCompat.getApplicationLocales(context)
    if (!preferred.isEmpty) return preferred[0]?.language ?: resourceLanguage

    val systemLanguage = LocaleManagerCompat.getSystemLocales(context)[0]?.language
    return when (systemLanguage) {
        "zh", "en", "ja", "ko" -> systemLanguage
        else -> resourceLanguage
    }
}

fun Medication.displayName(): String {
    val trimmed = name.trim()
    val stripped = seedNamePrefix.matchEntire(trimmed)?.groupValues?.getOrNull(1)?.trim()
    return stripped?.takeIf { it.isNotBlank() } ?: trimmed
}
