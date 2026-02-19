package com.example.medlog.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
            item.isTaken  -> MaterialTheme.colorScheme.tertiaryContainer
            item.isSkipped -> MaterialTheme.colorScheme.surfaceVariant
            else          -> MaterialTheme.colorScheme.surface
        },
        label = "cardColor",
    )

    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status indicator circle
            StatusCircle(isTaken = item.isTaken, isSkipped = item.isSkipped)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = med.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val period = TimePeriod.fromKey(med.timePeriod)
                    Icon(
                        imageVector = period.icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val timeText = if (med.timePeriod == "exact") {
                        "%02d:%02d".format(med.reminderHour, med.reminderMinute)
                    } else {
                        period.label
                    }
                    Text(
                        text = "${med.dose} ${med.doseUnit}  ·  $timeText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Actual taken time
                if (item.isTaken && item.log?.actualTakenTimeMs != null) {
                    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                    Text(
                        text = "实际服用：${fmt.format(Date(item.log.actualTakenTimeMs))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            // Action buttons
            if (!item.isSkipped) {
                IconButton(onClick = onToggleTaken) {
                    Icon(
                        imageVector = if (item.isTaken) Icons.Rounded.Check else Icons.Rounded.Check,
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
                            leadingIcon = { Icon(Icons.Rounded.Schedule, null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCircle(isTaken: Boolean, isSkipped: Boolean) {
    val bgColor = when {
        isTaken  -> MaterialTheme.colorScheme.tertiary
        isSkipped -> MaterialTheme.colorScheme.outlineVariant
        else     -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        if (isTaken) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}
