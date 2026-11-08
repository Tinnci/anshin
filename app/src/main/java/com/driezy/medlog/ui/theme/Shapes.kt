package com.driezy.medlog.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive (M3E) 形状比例。
 *
 *  extraSmall (16.dp) → OutlinedTextField 容器默认轮廓、药片与输入框圆角
 *  small (12.dp)      → FilterChip、SuggestionChip、AssistChip 柔和圆角
 *  medium (16.dp)     → 选项卡片、菜单、对话框
 *  large (20.dp)      → 表单分块、次级卡片
 *  extraLarge (28.dp) → 底部表单、主视窗卡片、全屏模态
 */
val MedLogShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
