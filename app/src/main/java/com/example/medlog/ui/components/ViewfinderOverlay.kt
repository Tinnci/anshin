package com.example.medlog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

/**
 * 取景框覆盖层：中央透明矩形 + 四周暗化遮罩 + 圆角边框角标。
 *
 * @param widthFraction 取景框占屏幕宽度的比例
 * @param aspectRatio   取景框宽高比 (width / height)
 */
@Composable
fun ViewfinderOverlay(
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.82f,
    aspectRatio: Float = 1.5f,
) {
    val cornerColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.fillMaxSize()) {
        val frameWidth = size.width * widthFraction
        val frameHeight = frameWidth / aspectRatio
        val left = (size.width - frameWidth) / 2
        val top = (size.height - frameHeight) / 2
        val cornerRadius = 16.dp.toPx()
        val cornerLength = 28.dp.toPx()
        val strokeWidth = 3.dp.toPx()

        // 暗化遮罩（排除中央取景框区域）
        val framePath = Path().apply {
            addRoundRect(
                RoundRect(
                    Rect(left, top, left + frameWidth, top + frameHeight),
                    CornerRadius(cornerRadius),
                ),
            )
        }
        clipPath(framePath, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.45f))
        }

        // 四角角标
        val corners = listOf(
            Offset(left, top),                                           // 左上
            Offset(left + frameWidth, top),                              // 右上
            Offset(left, top + frameHeight),                             // 左下
            Offset(left + frameWidth, top + frameHeight),                // 右下
        )
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)

        for ((i, corner) in corners.withIndex()) {
            val isLeft = i % 2 == 0
            val isTop = i < 2
            val hDir = if (isLeft) 1f else -1f
            val vDir = if (isTop) 1f else -1f

            // 水平线段
            drawLine(
                color = cornerColor,
                start = Offset(corner.x + hDir * cornerRadius * 0.3f, corner.y + vDir * cornerRadius * 0.1f),
                end = Offset(corner.x + hDir * cornerLength, corner.y + vDir * cornerRadius * 0.1f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            // 垂直线段
            drawLine(
                color = cornerColor,
                start = Offset(corner.x + hDir * cornerRadius * 0.1f, corner.y + vDir * cornerRadius * 0.3f),
                end = Offset(corner.x + hDir * cornerRadius * 0.1f, corner.y + vDir * cornerLength),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
