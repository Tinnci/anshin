package com.driezy.medlog.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

// ── 通用设置卡片组（24dp 扁平卡片，含组标题）────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WidgetPickerCard(
    previewType: WidgetPreviewType,
    name: String,
    description: String,
    sizes: List<String>,
    canPin: Boolean,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
    onAdd: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        WidgetPreviewSurface(
            type = previewType,
            name = name,
            showActions = showActions,
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp),
        )
        // 信息区域
        Column(
            modifier = Modifier.padding(horizontal = MedLogSpacing.Medium, vertical = MedLogSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
            ) {
                sizes.forEach { size ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Text(
                            text = size,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 添加按钮
            FilledTonalButton(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MedLogSpacing.Tiny),
            ) {
                MedLogIcon(MedLogIcons.AddToHomeScreen, null, Modifier.size(16.dp))
                Spacer(Modifier.width(MedLogSpacing.Small))
                Text(
                    if (canPin) {
                        stringResource(
                            R.string.settings_widget_add_btn,
                        )
                    } else {
                        stringResource(R.string.settings_widget_grant_btn)
                    },
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun WidgetPreviewSurface(
    type: WidgetPreviewType,
    name: String,
    showActions: Boolean,
    modifier: Modifier = Modifier,
) {
    val spec = remember(type, showActions) { WidgetPreviewSpec.forType(type, showActions) }
    Surface(
        modifier = modifier,
        color = when (type) {
            WidgetPreviewType.STREAK -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = when (type) {
            WidgetPreviewType.STREAK -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier.padding(MedLogSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (type == WidgetPreviewType.STREAK) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                sizesForPreview(type).forEach { size ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Text(
                            text = size,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            when (type) {
                WidgetPreviewType.TODAY -> TodayWidgetPreview(spec)
                WidgetPreviewType.NEXT_DOSE -> NextDoseWidgetPreview(spec)
                WidgetPreviewType.STREAK -> StreakWidgetPreview(spec)
            }
        }
    }
}

private fun sizesForPreview(type: WidgetPreviewType): List<String> = when (type) {
    WidgetPreviewType.TODAY -> listOf("2x2", "4x2")
    WidgetPreviewType.NEXT_DOSE -> listOf("2x2")
    WidgetPreviewType.STREAK -> listOf("4x2")
}

@Composable
private fun TodayWidgetPreview(spec: WidgetPreviewSpec) {
    Text(
        text = spec.primaryText,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
    LinearProgressIndicator(
        progress = { spec.progress ?: 0f },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewPill(text = stringResource(R.string.home_now_group_title), selected = true)
        PreviewPill(text = stringResource(R.string.home_later_group_title), selected = false)
        Spacer(Modifier.weight(1f))
        if (spec.showActionButton) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.widget_action_btn),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun NextDoseWidgetPreview(spec: WidgetPreviewSpec) {
    Text(
        text = stringResource(R.string.widget_next_dose_min_fmt, spec.minutesUntilNext ?: 45),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewPill(text = stringResource(R.string.home_now_group_title), selected = true)
        PreviewPill(text = stringResource(R.string.home_later_group_title), selected = false)
        Spacer(Modifier.weight(1f))
        if (spec.showActionButton) {
            MedLogIcon(
                MedLogIcons.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun StreakWidgetPreview(spec: WidgetPreviewSpec) {
    Text(
        text = pluralStringResource(
            R.plurals.widget_streak_days_fmt,
            spec.primaryText.toInt(),
            spec.primaryText.toInt(),
        ),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.tertiary,
        fontWeight = FontWeight.SemiBold,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        spec.completedDays.forEachIndexed { index, complete ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    index == spec.completedDays.lastIndex -> MaterialTheme.colorScheme.tertiary
                    complete -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                contentColor = when {
                    index == spec.completedDays.lastIndex -> MaterialTheme.colorScheme.onTertiary
                    complete -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Text(
                    text = if (complete) "✓" else "",
                    modifier = Modifier.size(22.dp).wrapContentSize(Alignment.Center),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PreviewPill(text: String, selected: Boolean) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

// ── Switch 行 ─────────────────────────────────────────────────────────────────
