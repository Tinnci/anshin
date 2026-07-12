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
        shape = MaterialTheme.shapes.extraLarge,
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
internal fun SettingsHomeOverviewPanel(
    uiState: SettingsUiState,
    canScheduleExactAlarms: Boolean,
    canPostNotifications: Boolean,
    onNavigateToReminderSettings: () -> Unit,
    onNavigateToIntelligenceSettings: () -> Unit,
    onNavigateToDataSettings: () -> Unit,
) {
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
            modifier = Modifier.padding(MedLogSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
            ) {
                SettingsHomeStatusChip(
                    text = stringResource(
                        if (presentation.reminderTone == SettingsHomeStatusTone.WARNING) {
                            R.string.settings_home_reminders_warning
                        } else {
                            R.string.settings_home_reminders_ok
                        },
                    ),
                    icon = MedLogIcons.Notifications,
                    tone = presentation.reminderTone,
                )
                SettingsHomeStatusChip(
                    text = stringResource(
                        when (presentation.intelligenceTone) {
                            SettingsHomeStatusTone.WARNING -> R.string.settings_home_ai_warning
                            SettingsHomeStatusTone.OK -> R.string.settings_home_ai_ready
                            SettingsHomeStatusTone.INFO -> R.string.settings_home_ai_local
                        },
                    ),
                    icon = MedLogIcons.AutoAwesome,
                    tone = presentation.intelligenceTone,
                )
                SettingsHomeStatusChip(
                    text = pluralStringResource(
                        R.plurals.settings_home_modules_enabled,
                        presentation.enabledModuleCount,
                        presentation.enabledModuleCount,
                    ),
                    icon = MedLogIcons.Tune,
                    tone = SettingsHomeStatusTone.INFO,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            ) {
                FilledTonalButton(
                    onClick = onNavigateToReminderSettings,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.settings_home_action_reminders))
                }
                FilledTonalButton(
                    onClick = if (presentation.intelligenceTone == SettingsHomeStatusTone.WARNING) {
                        onNavigateToIntelligenceSettings
                    } else {
                        onNavigateToDataSettings
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(
                            if (presentation.intelligenceTone == SettingsHomeStatusTone.WARNING) {
                                R.string.settings_home_action_intelligence
                            } else {
                                R.string.settings_home_action_data
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHomeStatusChip(
    text: String,
    icon: Int,
    tone: SettingsHomeStatusTone,
) {
    val containerColor = when (tone) {
        SettingsHomeStatusTone.OK -> MaterialTheme.colorScheme.secondaryContainer
        SettingsHomeStatusTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        SettingsHomeStatusTone.INFO -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when (tone) {
        SettingsHomeStatusTone.OK -> MaterialTheme.colorScheme.onSecondaryContainer
        SettingsHomeStatusTone.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
        SettingsHomeStatusTone.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MedLogIcon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            Text(text = text, style = MaterialTheme.typography.labelMedium)
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
