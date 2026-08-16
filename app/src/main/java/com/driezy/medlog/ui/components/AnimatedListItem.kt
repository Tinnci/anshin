package com.driezy.medlog.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * 列表项入场动画包装器：按索引依次淡入+上移。
 *
 * @param index        列表中的位置（控制 stagger 延迟）
 * @param motionScheme Material 动画配置
 * @param staggerDelay 项间延迟毫秒数
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedListItem(
    index: Int,
    motionScheme: MotionScheme = MaterialTheme.motionScheme,
    staggerDelay: Long = 60L,
    content: @Composable () -> Unit,
) {
    val animatedAlpha = remember { Animatable(0f) }
    val animatedOffset = remember { Animatable(24f) }
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }

    LaunchedEffect(animationsEnabled, index, staggerDelay) {
        if (animationsEnabled) {
            delay(index * staggerDelay)
            animatedAlpha.animateTo(1f, animationSpec = motionScheme.defaultEffectsSpec())
        } else {
            animatedAlpha.snapTo(1f)
        }
    }
    LaunchedEffect(animationsEnabled, index, staggerDelay) {
        if (animationsEnabled) {
            delay(index * staggerDelay)
            animatedOffset.animateTo(0f, animationSpec = motionScheme.defaultSpatialSpec())
        } else {
            animatedOffset.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier.graphicsLayer {
            alpha = animatedAlpha.value
            translationY = animatedOffset.value
        },
    ) {
        content()
    }
}
