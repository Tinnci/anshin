package com.driezy.medlog.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.driezy.medlog.data.repository.AppTextScale
import com.driezy.medlog.data.repository.FontMode
import com.driezy.medlog.data.repository.UiDensityScale

val MedLogLightColorScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceTint = surfaceTintLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
    scrim = scrimLight,
    primaryFixed = primaryFixedLight,
    primaryFixedDim = primaryFixedDimLight,
    onPrimaryFixed = onPrimaryFixedLight,
    onPrimaryFixedVariant = onPrimaryFixedVariantLight,
    secondaryFixed = secondaryFixedLight,
    secondaryFixedDim = secondaryFixedDimLight,
    onSecondaryFixed = onSecondaryFixedLight,
    onSecondaryFixedVariant = onSecondaryFixedVariantLight,
    tertiaryFixed = tertiaryFixedLight,
    tertiaryFixedDim = tertiaryFixedDimLight,
    onTertiaryFixed = onTertiaryFixedLight,
    onTertiaryFixedVariant = onTertiaryFixedVariantLight,
)

val MedLogDarkColorScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceTint = surfaceTintDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
    scrim = scrimDark,
    primaryFixed = primaryFixedDark,
    primaryFixedDim = primaryFixedDimDark,
    onPrimaryFixed = onPrimaryFixedDark,
    onPrimaryFixedVariant = onPrimaryFixedVariantDark,
    secondaryFixed = secondaryFixedDark,
    secondaryFixedDim = secondaryFixedDimDark,
    onSecondaryFixed = onSecondaryFixedDark,
    onSecondaryFixedVariant = onSecondaryFixedVariantDark,
    tertiaryFixed = tertiaryFixedDark,
    tertiaryFixedDim = tertiaryFixedDimDark,
    onTertiaryFixed = onTertiaryFixedDark,
    onTertiaryFixedVariant = onTertiaryFixedVariantDark,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MedLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Opt-in so first launch matches the Anshin icon.
    palette: ThemePalette = ThemePalette.ANSHIN,
    fontMode: FontMode = FontMode.SYSTEM,
    appTextScale: AppTextScale = AppTextScale.STANDARD,
    uiDensityScale: UiDensityScale = UiDensityScale.STANDARD,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        palette.allowsDynamicColor && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> palette.colorScheme(darkTheme)
    }

    val typography = remember(fontMode) { medLogTypography(fontMode) }
    val emphasizedTypography = remember(fontMode) { medLogEmphasizedTypography(fontMode) }
    val editorialTypography = remember(fontMode) { medLogEditorialTypography(fontMode) }
    val currentDensity = LocalDensity.current
    val displayDensity = remember(currentDensity, appTextScale, uiDensityScale) {
        Density(
            density = currentDensity.density * uiDensityScale.factor,
            fontScale = currentDensity.fontScale * appTextScale.factor,
        )
    }

    CompositionLocalProvider(LocalDensity provides displayDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = MedLogShapes,
            motionScheme = MotionScheme.expressive(),
        ) {
            CompositionLocalProvider(
                LocalEmphasizedTypography provides emphasizedTypography,
                LocalEditorialTypography provides editorialTypography,
                content = content,
            )
        }
    }
}

/** Convenient accessor: `MaterialTheme.emphasizedTypography.headlineMedium` */
val MaterialTheme.emphasizedTypography: EmphasizedTypography
    @Composable @ReadOnlyComposable
    get() = LocalEmphasizedTypography.current

val MaterialTheme.editorialTypography: EditorialTypography
    @Composable @ReadOnlyComposable
    get() = LocalEditorialTypography.current
