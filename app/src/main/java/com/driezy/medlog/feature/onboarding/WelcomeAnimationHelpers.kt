package com.driezy.medlog.feature.onboarding

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun rememberSpringEntry(
    isCurrentPage: Boolean,
    initialScale: Float = 0.5f,
    delayMs: Long = 0L,
): Pair<Float, Float> {
    val scale = remember { Animatable(initialScale) }
    val alpha = remember { Animatable(0f) }
    val motionScheme = MaterialTheme.motionScheme
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    LaunchedEffect(isCurrentPage, animationsEnabled) {
        if (isCurrentPage && animationsEnabled) {
            if (delayMs > 0) delay(delayMs)
            launch { scale.animateTo(1f, motionScheme.slowSpatialSpec()) }
            launch { alpha.animateTo(1f, motionScheme.defaultEffectsSpec()) }
        } else {
            scale.snapTo(if (isCurrentPage) 1f else initialScale)
            alpha.snapTo(if (isCurrentPage) 1f else 0f)
        }
    }
    return scale.value to alpha.value
}

/** 弹簧上滑 + 淡入（用于标题/正文入场） */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberSlideEntry(
    isCurrentPage: Boolean,
    initialOffsetY: Float = 40f,
    delayMs: Long = 0L,
): Pair<Float, Float> {
    val offsetY = remember { Animatable(initialOffsetY) }
    val alpha = remember { Animatable(0f) }
    val motionScheme = MaterialTheme.motionScheme
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    LaunchedEffect(isCurrentPage, animationsEnabled) {
        if (isCurrentPage && animationsEnabled) {
            if (delayMs > 0) delay(delayMs)
            launch { offsetY.animateTo(0f, motionScheme.defaultSpatialSpec()) }
            launch { alpha.animateTo(1f, motionScheme.defaultEffectsSpec()) }
        } else {
            offsetY.snapTo(if (isCurrentPage) 0f else initialOffsetY)
            alpha.snapTo(if (isCurrentPage) 1f else 0f)
        }
    }
    return offsetY.value to alpha.value
}
