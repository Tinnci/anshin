package com.driezy.medlog.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.material3.ColorProviders
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.data.repository.UserPreferencesRepository
import com.driezy.medlog.ui.theme.MedLogDarkColorScheme
import com.driezy.medlog.ui.theme.MedLogLightColorScheme
import com.driezy.medlog.ui.theme.ThemePalette

@Composable
internal fun MedLogGlanceTheme(
    themeMode: ThemeMode,
    useDynamicColor: Boolean,
    themePalette: ThemePalette = ThemePalette.ANSHIN,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDarkTheme = context.isSystemInNightMode()
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDarkTheme
    }
    val colors = when {
        themePalette.allowsDynamicColor && useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && themeMode == ThemeMode.SYSTEM -> {
            ColorProviders(dynamicLightColorScheme(context), dynamicDarkColorScheme(context))
        }
        themePalette.allowsDynamicColor && useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> {
            ColorProviders(dynamicDarkColorScheme(context))
        }
        themePalette.allowsDynamicColor && useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            ColorProviders(dynamicLightColorScheme(context))
        }
        themeMode == ThemeMode.SYSTEM && themePalette == ThemePalette.ANSHIN -> {
            ColorProviders(MedLogLightColorScheme, MedLogDarkColorScheme)
        }
        themeMode == ThemeMode.SYSTEM -> {
            ColorProviders(themePalette.colorScheme(darkTheme = false), themePalette.colorScheme(darkTheme = true))
        }
        else -> ColorProviders(themePalette.colorScheme(darkTheme))
    }

    GlanceTheme(colors = colors, content = content)
}

private fun Context.isSystemInNightMode(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

internal fun Preferences.medLogThemeMode(): ThemeMode =
    this[UserPreferencesRepository.THEME_MODE]
        ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
        ?: ThemeMode.SYSTEM

internal fun Preferences.medLogUseDynamicColor(): Boolean =
    this[UserPreferencesRepository.USE_DYNAMIC_COLOR] ?: true

internal fun Preferences.medLogThemePalette(): ThemePalette =
    ThemePalette.fromStoredName(this[UserPreferencesRepository.THEME_PALETTE])
