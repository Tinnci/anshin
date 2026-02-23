package com.example.medlog.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.medlog.R
import kotlinx.coroutines.delay

/**
 * 专为 [com.example.medlog.data.model.Medication.isPRN] == true 的药品设计的卡片区域。
 * 不显示"跳过"选项；用"今日已服 N 次"替代进度显示。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PRNSectionCard(
    items: List<MedicationWithStatus>,
    onToggleTaken: (MedicationWithStatus) -> Unit,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        // 头部
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Rounded.Healing,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_prn_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    stringResource(R.string.home_prn_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )

        // 药品列表
        items.forEachIndexed { idx, item ->
            var visible by remember(item.medication.id) { mutableStateOf(false) }
            LaunchedEffect(item.medication.id) {
                delay(idx * 30L)
                visible = true
            }
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                        slideInVertically(motionScheme.defaultSpatialSpec()) { it / 3 },
            ) {
                Column {
                    ListItem(
                        headlineContent = {
                            Text(item.medication.name, fontWeight = FontWeight.Medium)
                        },
                        supportingContent = {
                            val maxDose = item.medication.maxDailyDose
                            if (maxDose != null) {
                                Text(
                                    if (item.isTaken) stringResource(R.string.home_prn_taken_with_max, maxDose.toString(), item.medication.doseUnit)
                                    else stringResource(R.string.home_prn_max_dose, maxDose.toString(), item.medication.doseUnit),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (item.isTaken) MaterialTheme.colorScheme.tertiary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else if (item.isTaken) {
                                Text(
                                    stringResource(R.string.home_prn_taken_once),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        },
                        leadingContent = {
                            Icon(
                                if (item.medication.isTcm) Icons.Rounded.LocalFlorist
                                else Icons.Rounded.Medication,
                                contentDescription = null,
                                tint = if (item.isTaken) MaterialTheme.colorScheme.outline
                                       else MaterialTheme.colorScheme.secondary,
                            )
                        },
                        trailingContent = {
                            FilledTonalButton(
                                onClick = { onToggleTaken(item) },
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (item.isTaken)
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    else
                                        MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = if (item.isTaken)
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            ) {
                                Icon(
                                    if (item.isTaken) Icons.Rounded.CheckCircle else Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (item.isTaken) stringResource(R.string.home_prn_btn_taken) else stringResource(R.string.home_prn_btn_take),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        },
                        modifier = Modifier.clickable { onClick(item.medication.id) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
    }
}
