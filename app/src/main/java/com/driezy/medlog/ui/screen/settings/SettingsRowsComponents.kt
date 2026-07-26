package com.driezy.medlog.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.driezy.medlog.R
import com.driezy.medlog.data.model.Medication
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.util.displayName

// ── 通用设置卡片组（24dp 扁平卡片，含组标题）────────────────────────────────

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
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.38f,
                    )
                },
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.38f,
                    )
                },
            )
        },
        leadingContent = {
            MedLogIcon(
                icon,
                null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.38f,
                    )
                },
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
internal fun SettingsNavigationRow(title: String, subtitle: String, icon: Int, onClick: () -> Unit) {
    val layoutProfile = rememberSettingsLayoutProfile()
    ListItem(
        headlineContent = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = if (layoutProfile.showSupportingText) {
            {
                Text(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            null
        },
        leadingContent = {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                MedLogIcon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp),
                )
            }
        },
        trailingContent = {
            MedLogIcon(
                MedLogIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (layoutProfile.constrained) 56.dp else 68.dp)
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
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
        color = if (destructive) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (destructive) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
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
                    color = if (destructive) {
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.82f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
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

// ── 已归档药品可展开列表（替代 ModalBottomSheet）────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ArchivedMedicationsRow(archived: List<Medication>, onRestore: (Long) -> Unit) {
    val motionScheme = MaterialTheme.motionScheme
    var expanded by remember { mutableStateOf(false) }
    Column {
        ListItem(
            headlineContent = { Text(stringResource(R.string.archived_medications)) },
            supportingContent = {
                Text(
                    if (archived.isEmpty()) {
                        stringResource(R.string.settings_archived_empty)
                    } else {
                        pluralStringResource(R.plurals.settings_archived_count, archived.size, archived.size)
                    },
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
                                tint = if (med.isTcm) {
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
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
