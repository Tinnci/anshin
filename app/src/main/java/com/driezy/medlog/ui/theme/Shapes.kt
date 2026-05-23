package com.driezy.medlog.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 形状比例。
 *
 *  extraSmall → Chip、小徽章
 *  small      → 小菜单、气泡提示
 *  medium     → 卡片、对话框
 *  large      → 底部表单、模态抽屉
 *  extraLarge → 全屏对话框
 */
val MedLogShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
