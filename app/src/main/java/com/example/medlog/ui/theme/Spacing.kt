package com.example.medlog.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * M3 布局间距 token — 满足 4dp 网格对齐规范。
 *
 * 参考：https://m3.material.io/foundations/layout/understanding-layout/spacing
 * > Padding is measured in increments of 4dp.
 */
object MedLogSpacing {
    /* ── 基础比例 ─────────────────────────────────────────── */
    /** 2dp：极微间距（行间距微调、分隔线前后） */
    val Hairline = 2.dp
    /** 4dp：最小元素间距 */
    val Tiny     = 4.dp
    /** 8dp：紧凑元素、Chip 间距、列表项间距 */
    val Small    = 8.dp
    /** 12dp：卡片列表间距、中等分组间距 */
    val Medium   = 12.dp
    /** 16dp：屏幕水平 margin、卡片内 padding */
    val Large    = 16.dp
    /** 20dp：加大卡片 padding（如 ProgressHeader） */
    val XMedium  = 20.dp
    /** 24dp：大面板 / 抽屉 padding */
    val XLarge   = 24.dp
    /** 32dp：Welcome 页结构性留白 */
    val XXLarge  = 32.dp
    /** 48dp：空态区域留白 */
    val Huge     = 48.dp

    /* ── 屏幕级 contentPadding 预设 ──────────────────────── */
    /** 列表 + FAB：水平 16dp · 顶部 8dp · 底部 88dp（为 ExtendedFAB 留空间） */
    val ScreenContentWithFab = PaddingValues(
        start = Large, top = Small, end = Large, bottom = 88.dp,
    )
    /** 列表 + FloatingToolbar：水平 16dp · 顶部 8dp · 底部 80dp */
    val ScreenContentWithToolbar = PaddingValues(
        start = Large, top = Small, end = Large, bottom = 80.dp,
    )
    /** 无 FAB/Toolbar 的列表：统一 16dp */
    val ScreenContentDefault = PaddingValues(Large)
}
