package com.driezy.medlog.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import kotlin.math.roundToInt

@Immutable
internal data class SettingsLayoutProfile(val constrained: Boolean, val showSupportingText: Boolean)

@Composable
internal fun rememberSettingsLayoutProfile(): SettingsLayoutProfile {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val screenWidthDp = (windowInfo.containerSize.width / density.density).roundToInt()
    val screenHeightDp = (windowInfo.containerSize.height / density.density).roundToInt()
    return settingsLayoutProfile(
        fontScale = fontScale,
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp,
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
