package com.example.medlog.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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

@Composable
fun MedicationCard(
    item: MedicationWithStatus,
    onToggleTaken: () -> Unit,
    onSkip: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val med = item.medication
    val containerColor by animateColorAsState(
        targetValue = when {
            item.isTaken   -> MaterialTheme.colorScheme.tertiaryContainer
            item.isSkipped -> MaterialTheme.colorScheme.surfaceContainerHigh
            else           -> MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardColor",
    )

    // 已服用后整张卡片轻微透明，减弱视觉权重
    val cardAlpha by animateFloatAsState(
        targetValue = if (item.isTaken || item.isSkipped) 0.72f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardAlpha",
    )

    val borderMod = if (med.isHighPriority && !item.isTaken && !item.isSkipped)
        Modifier.border(1.5.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.medium)
    else Modifier

    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .then(borderMod)
            .graphicsLayer { alpha = cardAlpha }
            .clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (med.isHighPriority && !item.isTaken) 3.dp else 1.dp,
        ),
    ) {
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
                            // 已服用：显示删除线
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

            if (!item.isSkipped) {
                IconButton(onClick = onToggleTaken) {
                    Icon(
                        imageVector = if (item.isTaken) Icons.Rounded.CheckCircle
                                      else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (item.isTaken) "取消" else "标记已服",
                        tint = if (item.isTaken) MaterialTheme.colorScheme.tertiary
                               else MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
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
                            leadingIcon = { Icon(Icons.Rounded.Undo, null) },
                        )
                    }
                }
            }
        }
    }
}

/** 带弹性缩放的状态圆圈 */
@Composable
private fun AnimatedStatusCircle(isTaken: Boolean, isSkipped: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (isTaken) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "circleScale",
    )
    val bgColor by animateColorAsState(
        targetValue = when {
            isTaken   -> MaterialTheme.colorScheme.tertiary
            isSkipped -> MaterialTheme.colorScheme.outlineVariant
            else      -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
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

