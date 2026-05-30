package com.driezy.medlog.ui.screen.settings

import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.util.displayName

// ── 通用设置卡片组（24dp 扁平卡片，含组标题）────────────────────────────────

@Composable
internal fun SettingsCard(
    title: String,
    icon: Int,
    subtitle: String? = null,
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
            modifier = Modifier.padding(vertical = MedLogSpacing.Medium),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = MedLogSpacing.Large)
                    .padding(bottom = MedLogSpacing.Tiny),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            ) {
                MedLogIcon(
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
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = MedLogSpacing.Large)
                        .padding(bottom = MedLogSpacing.Small),
                )
            }
            content()
        }
    }
}

@Composable
internal fun SettingsSectionDivider(
    title: String,
    icon: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MedLogSpacing.Large)
                .padding(top = MedLogSpacing.Medium, bottom = MedLogSpacing.Tiny),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            MedLogIcon(
                icon = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotificationSettingsOverview(uiState: SettingsUiState) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MedLogSpacing.Large)
            .padding(top = MedLogSpacing.Small, bottom = MedLogSpacing.Medium),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(MedLogSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            ) {
                MedLogIcon(
                    MedLogIcons.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.settings_notifications_overview_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
            ) {
                NotificationStatusChip(
                    selected = uiState.persistentReminder,
                    text = stringResource(
                        if (uiState.persistentReminder) R.string.settings_notifications_live_on
                        else R.string.settings_notifications_live_off,
                    ),
                )
                NotificationStatusChip(
                    selected = uiState.earlyReminderMinutes > 0,
                    text = stringResource(
                        if (uiState.earlyReminderMinutes > 0) R.string.settings_notifications_pre_alert_on
                        else R.string.settings_notifications_pre_alert_off,
                    ),
                )
                NotificationStatusChip(
                    selected = uiState.followUpReminderEnabled,
                    text = stringResource(
                        if (uiState.followUpReminderEnabled) R.string.settings_notifications_follow_up_on
                        else R.string.settings_notifications_follow_up_off,
                    ),
                )
            }
            Text(
                text = stringResource(R.string.settings_notifications_system_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotificationStatusChip(selected: Boolean, text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

// ── 小组件选择卡片（预览图 + 说明 + 添加按钮）────────────────────────────────

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                // 尺寸徽章
                Row(horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny)) {
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
                    .padding(top = MedLogSpacing.Tiny),
            ) {
                MedLogIcon(MedLogIcons.AddToHomeScreen, null, Modifier.size(16.dp))
                Spacer(Modifier.width(MedLogSpacing.Small))
                Text(
                    if (canPin) stringResource(R.string.settings_widget_add_btn) else stringResource(R.string.settings_widget_grant_btn),
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
            modifier = Modifier.padding(MedLogSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (type == WidgetPreviewType.STREAK)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
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
        modifier = Modifier.fillMaxWidth().height(6.dp),
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
        text = pluralStringResource(R.plurals.widget_streak_days_fmt, spec.primaryText.toInt(), spec.primaryText.toInt()),
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
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

// ── Switch 行 ─────────────────────────────────────────────────────────────────

@Composable
internal fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: Int,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
        },
        leadingContent = {
            MedLogIcon(
                icon,
                null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
internal fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    icon: Int,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            MedLogIcon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            MedLogIcon(
                MedLogIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
internal fun DataSafetyPanel() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MedLogSpacing.Large)
            .padding(top = MedLogSpacing.Small, bottom = MedLogSpacing.Medium),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(MedLogSpacing.Medium),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            MedLogIcon(
                MedLogIcons.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Hairline)) {
                Text(
                    text = stringResource(R.string.settings_data_safety_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.settings_data_safety_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DataActionRow(
    title: String,
    subtitle: String,
    icon: Int,
    actionLabel: String,
    enabled: Boolean,
    destructive: Boolean = false,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MedLogSpacing.Large),
        shape = RoundedCornerShape(18.dp),
        color = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(MedLogSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        ) {
            MedLogIcon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Hairline),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (destructive)
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.82f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (loading) {
                LoadingIndicator(modifier = Modifier.size(24.dp))
            } else {
                if (destructive) {
                    OutlinedButton(
                        onClick = onClick,
                        enabled = enabled,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text(actionLabel)
                    }
                } else {
                    FilledTonalButton(onClick = onClick, enabled = enabled) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

// ── 作息时间行（点击展开内联 TimeInput，无模态对话框）────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RoutineTimeRow(
    label: String,
    hour: Int,
    minute: Int,
    icon: Int,
    onTimeSelected: (Int, Int) -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
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
                MedLogIcon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = {
                MedLogIcon(
                    icon = if (expanded) MedLogIcons.ExpandLess else MedLogIcons.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.clickable { expanded = !expanded },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MedLogSpacing.Large)
                    .padding(bottom = MedLogSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimeInput(state = timeState)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(MedLogSpacing.Small))
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ArchivedMedicationsRow(
    archived: List<Medication>,
    onRestore: (Long) -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
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
                MedLogIcon(MedLogIcons.Archive, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = {
                if (archived.isNotEmpty()) {
                    MedLogIcon(
                        if (expanded) MedLogIcons.ExpandLess else MedLogIcons.ExpandMore,
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
            enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            Column {
                HorizontalDivider(modifier = Modifier.padding(horizontal = MedLogSpacing.Large))
                archived.forEach { med ->
                    ListItem(
                        headlineContent = { Text(med.displayName()) },
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
                            MedLogIcon(
                                if (med.isTcm) MedLogIcons.LocalFlorist else MedLogIcons.Medication,
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
                                contentPadding = PaddingValues(horizontal = MedLogSpacing.Medium),
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
