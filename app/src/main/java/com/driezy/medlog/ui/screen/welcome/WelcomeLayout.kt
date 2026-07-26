package com.driezy.medlog.ui.screen.welcome

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Immutable
internal data class WelcomeLayoutProfile(
    val constrained: Boolean,
    val showIllustration: Boolean,
    val showSupportingText: Boolean,
    val keepActionsPinned: Boolean = true,
    val motionEnabled: Boolean = true,
)

internal fun welcomeLayoutProfile(
    fontScale: Float,
    screenWidthDp: Int,
    screenHeightDp: Int,
    motionEnabled: Boolean = true,
): WelcomeLayoutProfile {
    val constrained = fontScale >= 1.3f ||
        screenWidthDp < 380 ||
        screenHeightDp < 700
    return WelcomeLayoutProfile(
        constrained = constrained,
        showIllustration = !constrained,
        showSupportingText = fontScale < 1.6f && screenHeightDp >= 700,
        motionEnabled = motionEnabled,
    )
}

internal fun welcomeEntryDelayMs(index: Int, constrained: Boolean, motionEnabled: Boolean): Long {
    if (constrained || !motionEnabled) return 0L
    return (index.coerceAtLeast(0) * 40L).coerceAtMost(120L)
}

@Composable
internal fun rememberWelcomeLayoutProfile(): WelcomeLayoutProfile {
    val configuration = LocalConfiguration.current
    return welcomeLayoutProfile(
        fontScale = LocalDensity.current.fontScale,
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp,
        motionEnabled = ValueAnimator.areAnimatorsEnabled(),
    )
}

@Composable
internal fun WelcomePageScaffold(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.(WelcomeLayoutProfile) -> Unit,
) {
    val profile = rememberWelcomeLayoutProfile()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(if (profile.constrained) 12.dp else 16.dp),
    ) {
        content(profile)
    }
}

internal fun welcomePagePadding(profile: WelcomeLayoutProfile): PaddingValues = PaddingValues(
    horizontal = if (profile.constrained) 20.dp else 28.dp,
    vertical = if (profile.constrained) 16.dp else 28.dp,
)
