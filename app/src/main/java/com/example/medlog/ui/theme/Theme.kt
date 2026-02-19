package com.example.medlog.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val lightColorScheme = lightColorScheme(
    primary                  = primaryLight,
    onPrimary                = onPrimaryLight,
    primaryContainer         = primaryContainerLight,
    onPrimaryContainer       = onPrimaryContainerLight,
    secondary                = secondaryLight,
    onSecondary              = onSecondaryLight,
    secondaryContainer       = secondaryContainerLight,
    onSecondaryContainer     = onSecondaryContainerLight,
    tertiary                 = tertiaryLight,
    onTertiary               = onTertiaryLight,
    tertiaryContainer        = tertiaryContainerLight,
    onTertiaryContainer      = onTertiaryContainerLight,
    error                    = errorLight,
    onError                  = onErrorLight,
    errorContainer           = errorContainerLight,
    onErrorContainer         = onErrorContainerLight,
    background               = backgroundLight,
    onBackground             = onBackgroundLight,
    surface                  = surfaceLight,
    onSurface                = onSurfaceLight,
    surfaceVariant           = surfaceVariantLight,
    onSurfaceVariant         = onSurfaceVariantLight,
    outline                  = outlineLight,
    outlineVariant           = outlineVariantLight,
    inverseSurface           = inverseSurfaceLight,
    inverseOnSurface         = inverseOnSurfaceLight,
    inversePrimary           = inversePrimaryLight,
)

private val darkColorScheme = darkColorScheme(
    primary                  = primaryDark,
    onPrimary                = onPrimaryDark,
    primaryContainer         = primaryContainerDark,
    onPrimaryContainer       = onPrimaryContainerDark,
    secondary                = secondaryDark,
    onSecondary              = onSecondaryDark,
    secondaryContainer       = secondaryContainerDark,
    onSecondaryContainer     = onSecondaryContainerDark,
    tertiary                 = tertiaryDark,
    onTertiary               = onTertiaryDark,
    tertiaryContainer        = tertiaryContainerDark,
    onTertiaryContainer      = onTertiaryContainerDark,
    error                    = errorDark,
    onError                  = onErrorDark,
    errorContainer           = errorContainerDark,
    onErrorContainer         = onErrorContainerDark,
    background               = backgroundDark,
    onBackground             = onBackgroundDark,
    surface                  = surfaceDark,
    onSurface                = onSurfaceDark,
    surfaceVariant           = surfaceVariantDark,
    onSurfaceVariant         = onSurfaceVariantDark,
    outline                  = outlineDark,
    outlineVariant           = outlineVariantDark,
    inverseSurface           = inverseSurfaceDark,
    inverseOnSurface         = inverseOnSurfaceDark,
    inversePrimary           = inversePrimaryDark,
)

@Composable
fun MedLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,   // Material You — Android 12+
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme
        else      -> lightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = MedLogTypography,
        content     = content,
    )
}
