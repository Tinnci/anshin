package com.example.medlog.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.medlog.R
import com.example.medlog.data.model.TimePeriod
import com.example.medlog.ui.components.MedicationCard
import kotlinx.coroutines.delay

/**
 * 将同一服药时段的所有药品包裹在一张圆角卡片内。
 *
 * 卡片头部：时段图标 + 时段名 + 待服数 badge + 「一键服用本时段」按钮。
 * 卡片内容：每个药品一行，行间以 HorizontalDivider 分隔；进入动画逐项延迟。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TimePeriodGroupCard(
    timePeriod: TimePeriod,
    items: List<MedicationWithStatus>,
    onToggleTaken: (MedicationWithStatus) -> Unit,
    onSkip: (MedicationWithStatus) -> Unit,
    onTakeAll: () -> Unit,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    autoCollapse: Boolean = true,
) {
    val pendingCount = items.count { !it.isTaken && !it.isSkipped }
    val allDone = pendingCount == 0
    val motionScheme = MaterialTheme.motionScheme
    // allDone 变化时重算展开状态：已全服且开启自动折叠时默认折叠
    var isExpanded by remember(allDone) { mutableStateOf(!allDone || !autoCollapse) }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (allDone)
                MaterialTheme.colorScheme.surfaceContainerLowest
            else
                MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (allDone) 0.dp else 1.dp,
        ),
    ) {
        // ── 卡片头部 ──────────────────────────────────────────
        // 对非精确时段，取首项的提醒时间作为代表性展示时间
        val representativeTime = if (timePeriod.key != "exact") {
            items.firstOrNull()?.medication
                ?.let { "%02d:%02d".format(it.reminderHour, it.reminderMinute) }
        } else null

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = timePeriod.icon,
                contentDescription = null,
                tint = if (allDone) MaterialTheme.colorScheme.outline
                       else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(timePeriod.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (allDone) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.primary,
                )
                if (representativeTime != null) {
                    Text(
                        text = representativeTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (allDone) MaterialTheme.colorScheme.outlineVariant
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 待服数量 Badge（allDone 时显示胶囊徽章）
            if (allDone) {
                SuggestionChip(
                    onClick = { isExpanded = !isExpanded },
                    icon = {
                        Icon(
                            Icons.Rounded.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                    label = {
                        Text(
                            stringResource(R.string.home_period_all_done_chip),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.tertiary,
                    ),
                    border = null,
                    modifier = Modifier.height(28.dp),
                )
                // 展开/折叠指示箭头
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) stringResource(R.string.home_period_collapse) else stringResource(R.string.home_period_expand),
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) { Text("$pendingCount") }
                // 一键服用本时段 — pill 形，比单药按钮更大更显眼
                if (pendingCount >= 1) {
                    Button(
                        onClick = onTakeAll,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier.height(40.dp),
                    ) {
                        Icon(
                            Icons.Rounded.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.home_period_take_all_btn),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // ── 展开时才显示分隔线 + 药品列表 ──────────────────────────────
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                // ── 药品列表 ──────────────────────────────────────────
                items.forEachIndexed { idx, item ->
                    var visible by remember(item.medication.id) { mutableStateOf(false) }
                    LaunchedEffect(item.medication.id) {
                        delay(idx * 30L)   // 组内相邻延迟，避免全局累积延迟
                        visible = true
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                                slideInVertically(motionScheme.defaultSpatialSpec()) { it / 3 },
                    ) {
                        Column {
                            MedicationCard(
                                item = item,
                                onToggleTaken = { onToggleTaken(item) },
                                onSkip = { onSkip(item) },
                                onClick = { onClick(item.medication.id) },
                                modifier = Modifier,
                                // 卡片内不需要外圆角（已在 ElevatedCard 内）
                                flatStyle = true,
                            )
                            if (idx < items.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
            } // Column
        } // AnimatedVisibility
    } // ElevatedCard
}
