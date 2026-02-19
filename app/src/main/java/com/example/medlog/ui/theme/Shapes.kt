package com.example.medlog.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive 形状比例。
 *
 * 相比默认值使用更大的圆角弧度，呈现更饱满、更具表现力的视觉风格：
 *  extraSmall → Chip、小徽章
 *  small      → 小菜单、气泡提示
 *  medium     → 卡片、对话框
 *  large      → 底部表单、模态抽屉
 *  extraLarge → 全屏对话框
 */
val MedLogShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
