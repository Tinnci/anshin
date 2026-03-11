package com.example.medlog.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 可复用的处理中遮罩层。
 *
 * @param visible  是否可见
 * @param text     进度文案
 * @param elevated 是否使用卡片样式（true = 居中卡片，false = 全屏文字叠加）
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProcessingOverlay(
    visible: Boolean,
    text: String,
    elevated: Boolean = true,
) {
    val motionScheme = MaterialTheme.motionScheme

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motionScheme.defaultEffectsSpec()),
        exit = fadeOut(motionScheme.fastEffectsSpec()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                .pointerInput(Unit) { /* 消费触摸，阻止穿透 */ },
            contentAlignment = Alignment.Center,
        ) {
            if (elevated) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 6.dp,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 32.dp),
                    ) {
                        LoadingIndicator(modifier = Modifier.size(56.dp))
                        Text(
                            text = text,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LoadingIndicator(modifier = Modifier.size(56.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }
    }
}
