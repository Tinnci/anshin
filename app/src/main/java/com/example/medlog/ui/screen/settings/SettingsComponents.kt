package com.example.medlog.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.medlog.R
import com.example.medlog.data.model.Medication

// ── 通用设置卡片组（24dp 扁平卡片，含组标题）────────────────────────────────

@Composable
internal fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            content()
        }
    }
}

// ── 小组件选择卡片（预览图 + 说明 + 添加按钮）────────────────────────────────

@Composable
internal fun WidgetPickerCard(
    previewRes: Int,
    name: String,
    description: String,
    sizes: List<String>,
    canPin: Boolean,
    onAdd: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        // 预览区域
        Image(
            painter = painterResource(previewRes),
            contentDescription = stringResource(R.string.settings_widget_preview_cd, name),
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            contentScale = ContentScale.FillWidth,
        )
        // 信息区域
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                // 尺寸徽章
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    sizes.forEach { size ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(size, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp),
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
                    .padding(top = 4.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.AddToHomeScreen, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (canPin) stringResource(R.string.settings_widget_add_btn) else stringResource(R.string.settings_widget_grant_btn),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// ── Switch 行 ─────────────────────────────────────────────────────────────────

@Composable
internal fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

// ── 作息时间行（点击展开内联 TimeInput，无模态对话框）────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutineTimeRow(
    label: String,
    hour: Int,
    minute: Int,
    icon: ImageVector,
    onTimeSelected: (Int, Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // 状态始终保持，不随 expanded 重置
    val timeState = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = true,
    )

    Column {
        ListItem(
            headlineContent = { Text(label) },
            supportingContent = {
                Text(
                    "%02d:%02d".format(hour, minute),
                    color = if (expanded) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingContent = {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.clickable { expanded = !expanded },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimeInput(state = timeState)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = {
                        onTimeSelected(timeState.hour, timeState.minute)
                        expanded = false
                    }) { Text(stringResource(R.string.confirm)) }
                }
            }
        }
    }
}

// ── 已归档药品可展开列表（替代 ModalBottomSheet）────────────────────────────

@Composable
internal fun ArchivedMedicationsRow(
    archived: List<Medication>,
    onRestore: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        ListItem(
            headlineContent = { Text(stringResource(R.string.archived_medications)) },
            supportingContent = {
                Text(
                    if (archived.isEmpty()) stringResource(R.string.settings_archived_empty)
                    else pluralStringResource(R.plurals.settings_archived_count, archived.size, archived.size),
                )
            },
            leadingContent = {
                Icon(Icons.Rounded.Archive, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = {
                if (archived.isNotEmpty()) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = Modifier.clickable(enabled = archived.isNotEmpty()) { expanded = !expanded },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        AnimatedVisibility(
            visible = expanded && archived.isNotEmpty(),
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                archived.forEach { med ->
                    ListItem(
                        headlineContent = { Text(med.name) },
                        supportingContent = {
                            val catText = med.category.ifBlank { null }
                            val label = when {
                                med.isTcm && catText != null -> stringResource(R.string.tcm_cat_label, catText)
                                med.isTcm -> stringResource(R.string.tcm_label)
                                catText != null -> catText
                                else -> null
                            }
                            if (label != null) Text(label)
                        },
                        leadingContent = {
                            Icon(
                                if (med.isTcm) Icons.Rounded.LocalFlorist else Icons.Rounded.Medication,
                                null,
                                tint = if (med.isTcm)
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                                else
                                    MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingContent = {
                            FilledTonalButton(
                                onClick = { onRestore(med.id) },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                            ) {
                                Text(stringResource(R.string.restore), style = MaterialTheme.typography.labelMedium)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}
