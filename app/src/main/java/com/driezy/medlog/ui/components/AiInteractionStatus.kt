package com.driezy.medlog.ui.components

import android.animation.ValueAnimator
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.feature.health.application.AiExecutionMode
import com.driezy.medlog.feature.health.application.AiExecutionStatus
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

enum class AiInteractionVisualState {
    RUNNING,
    CLOUD_SUCCESS,
    LOCAL_FALLBACK,
    LOCAL_ONLY,
}

data class AiInteractionPresentation(
    val visualState: AiInteractionVisualState,
    val animated: Boolean,
    @param:StringRes val labelRes: Int,
) {
    companion object {
        fun from(status: AiExecutionStatus, isRunning: Boolean = false): AiInteractionPresentation {
            if (isRunning) {
                return AiInteractionPresentation(
                    visualState = AiInteractionVisualState.RUNNING,
                    animated = true,
                    labelRes = R.string.ai_status_running,
                )
            }
            return when (status.mode) {
                AiExecutionMode.CLOUD_SUCCESS -> AiInteractionPresentation(
                    visualState = AiInteractionVisualState.CLOUD_SUCCESS,
                    animated = false,
                    labelRes = R.string.ai_status_cloud_success,
                )
                AiExecutionMode.CLOUD_UNAVAILABLE_FALLBACK,
                AiExecutionMode.CLOUD_FAILED_FALLBACK,
                -> AiInteractionPresentation(
                    visualState = AiInteractionVisualState.LOCAL_FALLBACK,
                    animated = false,
                    labelRes = R.string.ai_status_local_fallback,
                )
                AiExecutionMode.LOCAL_ONLY -> AiInteractionPresentation(
                    visualState = AiInteractionVisualState.LOCAL_ONLY,
                    animated = false,
                    labelRes = R.string.ai_status_local_only,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AiInteractionStatusPill(status: AiExecutionStatus, isRunning: Boolean, modifier: Modifier = Modifier) {
    val presentation = AiInteractionPresentation.from(status, isRunning)
    val targetContainer = when (presentation.visualState) {
        AiInteractionVisualState.RUNNING -> MaterialTheme.colorScheme.primaryContainer
        AiInteractionVisualState.CLOUD_SUCCESS -> MaterialTheme.colorScheme.tertiaryContainer
        AiInteractionVisualState.LOCAL_FALLBACK -> MaterialTheme.colorScheme.surfaceContainerHigh
        AiInteractionVisualState.LOCAL_ONLY -> MaterialTheme.colorScheme.surfaceContainer
    }
    val targetContent = when (presentation.visualState) {
        AiInteractionVisualState.RUNNING -> MaterialTheme.colorScheme.onPrimaryContainer
        AiInteractionVisualState.CLOUD_SUCCESS -> MaterialTheme.colorScheme.onTertiaryContainer
        AiInteractionVisualState.LOCAL_FALLBACK -> MaterialTheme.colorScheme.onSurfaceVariant
        AiInteractionVisualState.LOCAL_ONLY -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val motionScheme = MaterialTheme.motionScheme
    val containerColor by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "ai_status_container",
    )
    val contentColor by animateColorAsState(
        targetValue = targetContent,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "ai_status_content",
    )
    val pulse = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    LaunchedEffect(presentation.animated, motionScheme, animationsEnabled) {
        if (presentation.animated && animationsEnabled) {
            while (true) {
                pulse.animateTo(1.08f, animationSpec = motionScheme.fastSpatialSpec<Float>())
                rotation.animateTo(180f, animationSpec = motionScheme.defaultSpatialSpec<Float>())
                pulse.animateTo(0.94f, animationSpec = motionScheme.defaultSpatialSpec<Float>())
                rotation.animateTo(360f, animationSpec = motionScheme.defaultSpatialSpec<Float>())
                rotation.snapTo(0f)
            }
        } else {
            pulse.snapTo(1f)
            rotation.snapTo(0f)
        }
    }
    val labelMotion = motionScheme.fastEffectsSpec<Float>()

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MedLogIcon(
                icon = if (presentation.visualState == AiInteractionVisualState.LOCAL_FALLBACK) {
                    MedLogIcons.Info
                } else {
                    MedLogIcons.AutoAwesome
                },
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        if (presentation.animated) {
                            scaleX = pulse.value
                            scaleY = pulse.value
                            rotationZ = rotation.value
                        }
                    },
            )
            Spacer(Modifier.width(MedLogSpacing.Tiny))
            AnimatedContent(
                targetState = presentation.labelRes,
                transitionSpec = {
                    fadeIn(labelMotion).togetherWith(fadeOut(labelMotion))
                },
                label = "ai_status_label",
            ) { labelRes ->
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
