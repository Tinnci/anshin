package com.example.medlog.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.medlog.data.model.TimePeriod
import com.example.medlog.ui.screen.home.MedicationWithStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MedicationCard(
    item: MedicationWithStatus,
    onToggleTaken: () -> Unit,
    onSkip: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val med = item.medication
    val motionScheme = MaterialTheme.motionScheme

    // 卡片底色：未服 → surfaceContainerLow（与 Flutter 一致），已服 → surface（减弱），跳过 → surfaceContainerHigh
    val containerColor by animateColorAsState(
        targetValue = when {
            item.isTaken   -> MaterialTheme.colorScheme.surface
            item.isSkipped -> MaterialTheme.colorScheme.surfaceContainerHigh
            else           -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "cardColor",
    )

    // 已服用后整张卡片轻微透明，减弱视觉权重
    val cardAlpha by animateFloatAsState(
        targetValue = if (item.isTaken || item.isSkipped) 0.75f else 1f,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "cardAlpha",
    )

    // 左侧色带颜色：高优先级=primary，普通=secondaryContainer，已服/跳过=outlineVariant
    val stripColor by animateColorAsState(
        targetValue = when {
            item.isTaken || item.isSkipped -> MaterialTheme.colorScheme.outlineVariant
            med.isHighPriority             -> MaterialTheme.colorScheme.primary
            else                           -> MaterialTheme.colorScheme.secondaryContainer
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "strip",
    )

    val cardShape = RoundedCornerShape(24.dp)
    // 高优先级且未完成：显示半透明 primary 描边
    val borderMod = if (med.isHighPriority && !item.isTaken && !item.isSkipped)
        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), cardShape)
    else Modifier

    var expanded by remember { mutableStateOf(false) }

    // 扁平卡片（elevation = 0），24dp 大圆角，与 Flutter 版对齐
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(borderMod)
            .graphicsLayer { alpha = cardAlpha }
            .clickable(onClick = onClick),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        // IntrinsicSize.Min 使左侧色带高度与主内容对齐
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // ── 左侧色带 ─────────────────────────────────────
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .fillMaxHeight()
                    .background(stripColor),
            )

            // ── 主内容区 ──────────────────────────────────────
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedStatusCircle(isTaken = item.isTaken, isSkipped = item.isSkipped)
                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = med.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                textDecoration = if (item.isTaken) TextDecoration.LineThrough
                                                 else TextDecoration.None,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (med.isHighPriority) {
                            Icon(
                                Icons.Rounded.PriorityHigh,
                                contentDescription = "高优先级",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        if (med.isPRN) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text("PRN", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val period = TimePeriod.fromKey(med.timePeriod)
                        Icon(
                            period.icon, null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val timeText = if (med.timePeriod == "exact") {
                            med.reminderTimes.split(",").firstOrNull()
                                ?: "%02d:%02d".format(med.reminderHour, med.reminderMinute)
                        } else period.label
                        val doseDisplay = med.doseQuantity.let {
                            if (it == it.toLong().toDouble()) "${it.toLong()} ${med.doseUnit}"
                            else "%.1f ${med.doseUnit}".format(it)
                        }
                        Text(
                            text = "$doseDisplay  ·  $timeText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.isTaken && item.log?.actualTakenTimeMs != null) {
                        Text(
                            text = "✓ 已于 ${SimpleDateFormat("HH:mm", Locale.getDefault())
                                .format(Date(item.log.actualTakenTimeMs))} 服用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (item.isSkipped) {
                        Text(
                            "今日已跳过",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    if (med.stock != null && med.refillThreshold != null && med.stock <= med.refillThreshold) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Warning, null,
                                Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "库存不足（剩余 ${med.stock.toInt()} ${med.doseUnit}）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                // ── M3 Expressive ButtonGroup：取药 + 跳过 + 更多 ──
                Spacer(Modifier.width(4.dp))
                @Suppress("DEPRECATION")
                ButtonGroup {
                    // 服药 / 撤销 按钮
                    FilledIconButton(
                        onClick = onToggleTaken,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (item.isTaken)
                                MaterialTheme.colorScheme.tertiaryContainer
                            else
                                MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (item.isTaken)
                                MaterialTheme.colorScheme.onTertiaryContainer
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(
                            imageVector = if (item.isTaken) Icons.AutoMirrored.Rounded.Undo else Icons.Rounded.Check,
                            contentDescription = if (item.isTaken) "撤销" else "标记已服",
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    // 跳过按钮（仅在未处理时显示）
                    if (!item.isTaken && !item.isSkipped) {
                        OutlinedIconButton(
                            onClick = onSkip,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Rounded.SkipNext, "跳过", Modifier.size(18.dp))
                        }
                    }

                    // 更多操作
                    Box {
                        OutlinedIconButton(
                            onClick = { expanded = true },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Rounded.MoreVert, "更多", Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            if (!item.isTaken && !item.isSkipped) {
                                DropdownMenuItem(
                                    text = { Text("跳过今日") },
                                    onClick = { expanded = false; onSkip() },
                                    leadingIcon = { Icon(Icons.Rounded.SkipNext, null) },
                                )
                            }
                            if (item.isSkipped || item.isTaken) {
                                DropdownMenuItem(
                                    text = { Text("撤销") },
                                    onClick = { expanded = false; onToggleTaken() },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Undo, null) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 带弹性缩放的状态圆圈 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedStatusCircle(isTaken: Boolean, isSkipped: Boolean) {
    val motionScheme = MaterialTheme.motionScheme
    val scale by animateFloatAsState(
        targetValue = if (isTaken) 1f else 0.9f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "circleScale",
    )
    val bgColor by animateColorAsState(
        targetValue = when {
            isTaken   -> MaterialTheme.colorScheme.tertiary
            isSkipped -> MaterialTheme.colorScheme.outlineVariant
            else      -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "circleBg",
    )
    Box(
        modifier = Modifier
            .size(36.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isTaken   -> Icon(
                Icons.Rounded.Check, null,
                tint = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(20.dp),
            )
            isSkipped -> Icon(
                Icons.Rounded.Remove, null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

