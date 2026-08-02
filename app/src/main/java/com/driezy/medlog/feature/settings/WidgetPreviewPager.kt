package com.driezy.medlog.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.driezy.medlog.ui.theme.MedLogSpacing

internal data class WidgetPreviewItem(
    val previewType: WidgetPreviewType,
    val name: String,
    val description: String,
    val sizes: List<String>,
    val canPin: Boolean,
    val showActions: Boolean = true,
)

/**
 * 小组件卡片包含说明、尺寸和操作，不适合会压缩相邻内容的 Hero mask。
 * 整卡分页避免露出不可读的相邻交互内容，页码点提供稳定的位置反馈。
 */
@Composable
internal fun WidgetPreviewPager(items: List<WidgetPreviewItem>, onAdd: (WidgetPreviewType) -> Unit) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { items.size })

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            key = { items[it].previewType },
            pageSpacing = MedLogSpacing.Small,
            modifier = Modifier
                .fillMaxWidth()
                .height(312.dp),
        ) { page ->
            val item = items[page]
            WidgetPickerCard(
                previewType = item.previewType,
                name = item.name,
                description = item.description,
                sizes = item.sizes,
                canPin = item.canPin,
                showActions = item.showActions,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                onAdd = { onAdd(item.previewType) },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small)) {
            items.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                        .background(
                            color = if (index == pagerState.currentPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}
