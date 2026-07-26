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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing

// ── 通用设置卡片组（24dp 扁平卡片，含组标题）────────────────────────────────

@Composable
internal fun SettingsCard(
    title: String,
    icon: Int,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val layoutProfile = rememberSettingsLayoutProfile()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(
                vertical = if (layoutProfile.constrained) MedLogSpacing.Small else MedLogSpacing.Medium,
            ),
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
                    maxLines = if (layoutProfile.constrained) 2 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            content()
        }
    }
}

@Composable
internal fun SettingsNavigationGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = MedLogSpacing.Small),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(content = content)
        }
    }
}

@Composable
internal fun SettingsSectionDivider(title: String, icon: Int, modifier: Modifier = Modifier) {
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
internal fun SettingsHomeOverviewPanel(
    uiState: SettingsUiState,
    canScheduleExactAlarms: Boolean,
    canPostNotifications: Boolean,
) {
    val layoutProfile = rememberSettingsLayoutProfile()
    val presentation = SettingsHomeOverviewPresentation.from(
        uiState = uiState,
        canScheduleExactAlarms = canScheduleExactAlarms,
        canPostNotifications = canPostNotifications,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(
                if (layoutProfile.constrained) MedLogSpacing.Medium else MedLogSpacing.Large,
            ),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    MedLogIcon(
                        MedLogIcons.Settings,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(MedLogSpacing.Small)
                            .size(24.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Hairline),
                ) {
                    Text(
                        text = stringResource(R.string.settings_home_status_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (presentation.attentionCount > 0) {
                            pluralStringResource(
                                R.plurals.settings_home_status_attention,
                                presentation.attentionCount,
                                presentation.attentionCount,
                            )
                        } else {
                            stringResource(R.string.settings_home_status_all_good)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                    )
                }
            }
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
                        if (uiState.persistentReminder) {
                            R.string.settings_notifications_live_on
                        } else {
                            R.string.settings_notifications_live_off
                        },
                    ),
                )
                NotificationStatusChip(
                    selected = uiState.earlyReminderMinutes > 0,
                    text = stringResource(
                        if (uiState.earlyReminderMinutes > 0) {
                            R.string.settings_notifications_pre_alert_on
                        } else {
                            R.string.settings_notifications_pre_alert_off
                        },
                    ),
                )
                NotificationStatusChip(
                    selected = uiState.followUpReminderEnabled,
                    text = stringResource(
                        if (uiState.followUpReminderEnabled) {
                            R.string.settings_notifications_follow_up_on
                        } else {
                            R.string.settings_notifications_follow_up_off
                        },
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

// ── 小组件选择卡片（预览图 + 说明 + 添加按钮）────────────────────────────────
