package com.driezy.medlog.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.material3.ColorProviders
import com.driezy.medlog.data.repository.ThemeMode
import com.driezy.medlog.data.repository.UserPreferencesRepository
import com.driezy.medlog.data.repository.WidgetColorSource
import com.driezy.medlog.data.repository.WidgetDensityScale
import com.driezy.medlog.data.repository.WidgetTextScale
import com.driezy.medlog.data.repository.WidgetThemeMode
import com.driezy.medlog.ui.theme.MedLogDarkColorScheme
import com.driezy.medlog.ui.theme.MedLogLightColorScheme
import com.driezy.medlog.ui.theme.ThemePalette

internal data class MedLogWidgetAppearance(
    val themeMode: ThemeMode,
    val useDynamicColor: Boolean,
    val themePalette: ThemePalette,
    val sizing: WidgetSizing,
)

internal data class WidgetSizing(val densityFactor: Float, val textFactor: Float) {
    fun dp(value: Int): Dp = (value * densityFactor).dp
    fun sp(value: Int): TextUnit = (value * textFactor).sp
}

@Composable
internal fun MedLogGlanceTheme(appearance: MedLogWidgetAppearance, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDarkTheme = context.isSystemInNightMode()
    val darkTheme = when (appearance.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDarkTheme
    }
    val colors = when {
        appearance.themePalette.allowsDynamicColor &&
            appearance.useDynamicColor &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            appearance.themeMode == ThemeMode.SYSTEM -> {
            ColorProviders(dynamicLightColorScheme(context), dynamicDarkColorScheme(context))
        }
        appearance.themePalette.allowsDynamicColor &&
            appearance.useDynamicColor &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            darkTheme -> {
            ColorProviders(dynamicDarkColorScheme(context))
        }
        appearance.themePalette.allowsDynamicColor &&
            appearance.useDynamicColor &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            ColorProviders(dynamicLightColorScheme(context))
        }
        appearance.themeMode == ThemeMode.SYSTEM && appearance.themePalette == ThemePalette.ANSHIN -> {
            ColorProviders(MedLogLightColorScheme, MedLogDarkColorScheme)
        }
        appearance.themeMode == ThemeMode.SYSTEM -> {
            ColorProviders(
                appearance.themePalette.colorScheme(darkTheme = false),
                appearance.themePalette.colorScheme(darkTheme = true),
            )
        }
        else -> ColorProviders(appearance.themePalette.colorScheme(darkTheme))
    }

    GlanceTheme(colors = colors, content = content)
}

private fun Context.isSystemInNightMode(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

internal fun Preferences.medLogThemeMode(): ThemeMode = this[UserPreferencesRepository.THEME_MODE]
    ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
    ?: ThemeMode.SYSTEM

internal fun Preferences.medLogUseDynamicColor(): Boolean = this[UserPreferencesRepository.USE_DYNAMIC_COLOR] ?: true

internal fun Preferences.medLogThemePalette(): ThemePalette =
    ThemePalette.fromStoredName(this[UserPreferencesRepository.THEME_PALETTE])

internal fun Preferences.medLogWidgetAppearance(): MedLogWidgetAppearance {
    val appThemeMode = medLogThemeMode()
    val appUseDynamicColor = medLogUseDynamicColor()
    val appPalette = medLogThemePalette()
    val widgetThemeMode = WidgetThemeMode.fromStoredName(this[UserPreferencesRepository.WIDGET_THEME_MODE])
    val widgetColorSource = WidgetColorSource.fromStoredName(this[UserPreferencesRepository.WIDGET_COLOR_SOURCE])

    val effectiveThemeMode = when (widgetThemeMode) {
        WidgetThemeMode.SYSTEM -> ThemeMode.SYSTEM
        WidgetThemeMode.APP -> appThemeMode
        WidgetThemeMode.LIGHT -> ThemeMode.LIGHT
        WidgetThemeMode.DARK -> ThemeMode.DARK
    }
    val (useDynamicColor, palette) = when (widgetColorSource) {
        WidgetColorSource.SYSTEM_DYNAMIC -> true to ThemePalette.ANSHIN
        WidgetColorSource.APP_THEME -> appUseDynamicColor to appPalette
        WidgetColorSource.CUSTOM_PALETTE -> false to ThemePalette.fromStoredName(
            this[UserPreferencesRepository.WIDGET_PALETTE],
        )
    }
    val density = WidgetDensityScale.fromStoredName(this[UserPreferencesRepository.WIDGET_DENSITY_SCALE])
    val text = WidgetTextScale.fromStoredName(this[UserPreferencesRepository.WIDGET_TEXT_SCALE])

    return MedLogWidgetAppearance(
        themeMode = effectiveThemeMode,
        useDynamicColor = useDynamicColor,
        themePalette = palette,
        sizing = WidgetSizing(densityFactor = density.factor, textFactor = text.factor),
    )
}
