package com.driezy.medlog.feature.onboarding

import android.animation.ValueAnimator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Pages and actions stop growing here so tablets and landscape keep a readable line length. */
internal val WelcomeMaxContentWidth = 560.dp
internal val WelcomeMaxInlineActionsWidth = 720.dp

@Immutable
internal data class WelcomeLayoutProfile(
    val constrained: Boolean,
    val showIllustration: Boolean,
    val showSupportingText: Boolean,
    val showBrandMark: Boolean = showIllustration,
    val inlineActions: Boolean = false,
    val showStepCount: Boolean = true,
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
        showIllustration = screenHeightDp >= 700,
        showSupportingText = fontScale < 1.6f && screenHeightDp >= 700,
        showBrandMark = screenHeightDp >= 480,
        inlineActions = screenHeightDp < 480,
        showStepCount = fontScale < 1.6f || screenWidthDp >= 380,
        motionEnabled = motionEnabled,
    )
}

internal fun welcomeEntryDelayMs(index: Int, constrained: Boolean, motionEnabled: Boolean): Long {
    if (constrained || !motionEnabled) return 0L
    return (index.coerceAtLeast(0) * 40L).coerceAtMost(120L)
}

@Composable
internal fun rememberWelcomeLayoutProfile(): WelcomeLayoutProfile {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenWidthDp = (windowInfo.containerSize.width / density.density).roundToInt()
    val screenHeightDp = (windowInfo.containerSize.height / density.density).roundToInt()
    return welcomeLayoutProfile(
        fontScale = density.fontScale,
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp,
        motionEnabled = ValueAnimator.areAnimatorsEnabled(),
    )
}

/**
 * Scrolls edge to edge, so overflowing content reaches the action bar instead of being cut off at
 * the page padding, and fades out there to show that more content follows.
 */
@Composable
internal fun WelcomePageScaffold(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    centered: Boolean = false,
    content: @Composable ColumnScope.(WelcomeLayoutProfile) -> Unit,
) {
    val profile = rememberWelcomeLayoutProfile()
    val scrollState = rememberScrollState()
    val spacing = if (profile.constrained) 12.dp else 16.dp
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .heightIn(min = maxHeight)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = WelcomeMaxContentWidth)
                .fillMaxWidth()
                .padding(welcomePagePadding(profile)),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = if (centered) {
                Arrangement.spacedBy(spacing, Alignment.CenterVertically)
            } else {
                Arrangement.spacedBy(spacing)
            },
        ) {
            content(profile)
        }
        if (scrollState.canScrollForward) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                        ),
                    ),
            )
        }
    }
}

/** Avoids stranded last words, such as a single character on the last line of a CJK title. */
@Composable
internal fun welcomeTitleStyle(hero: Boolean = false): TextStyle {
    val style = if (hero) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall
    return style.copy(lineBreak = LineBreak.Heading)
}

@Composable
internal fun welcomeBodyStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Paragraph)

private fun welcomePagePadding(profile: WelcomeLayoutProfile): PaddingValues = PaddingValues(
    horizontal = if (profile.constrained) 20.dp else 28.dp,
    vertical = if (profile.constrained) 16.dp else 28.dp,
)
