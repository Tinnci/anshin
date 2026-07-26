package com.driezy.medlog.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity

@Immutable
internal data class SettingsLayoutProfile(val constrained: Boolean, val showSupportingText: Boolean)

@Composable
internal fun rememberSettingsLayoutProfile(): SettingsLayoutProfile {
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    return settingsLayoutProfile(
        fontScale = fontScale,
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp,
    )
}

internal fun settingsLayoutProfile(fontScale: Float, screenWidthDp: Int, screenHeightDp: Int): SettingsLayoutProfile {
    val constrained = fontScale >= 1.3f ||
        screenWidthDp < 380 ||
        screenHeightDp < 700

    return SettingsLayoutProfile(
        constrained = constrained,
        showSupportingText = !constrained,
    )
}
