package com.driezy.medlog.ui.screen.health

import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import com.driezy.medlog.ui.theme.emphasizedTypography
import com.driezy.medlog.ui.theme.MedLogSpacing
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.driezy.medlog.R
import com.driezy.medlog.data.model.HealthRecord
import com.driezy.medlog.data.model.HealthType
import com.driezy.medlog.domain.health.HealthInsight
import com.driezy.medlog.domain.health.HealthInsightSeverity
import com.driezy.medlog.domain.health.AiExecutionStatus
import com.driezy.medlog.ui.components.AiInteractionStatusPill
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


@Composable
internal fun HealthRecordItem(
    record: HealthRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val type = remember(record.type) { HealthType.fromName(record.type) }
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    var showMenu by remember { mutableStateOf(false) }
    val isAbnormal = !type.isNormal(record.value)
    val visibleNotes = remember(record.notes) { record.userVisibleNotes() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isAbnormal) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                             else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        type.formatMetricValue(record.value, record.secondaryValue),
                        style = MaterialTheme.emphasizedTypography.titleLarge,
                    )
                    Spacer(Modifier.width(MedLogSpacing.Tiny))
                    Text(
                        type.unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            },
            overlineContent = {
                Text(stringResource(type.labelRes))
            },
            supportingContent = {
                Column {
                    Text(
                        timeFormat.format(Date(record.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (visibleNotes.isNotBlank()) {
                        Text(
                            visibleNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            leadingContent = {
                MedLogIcon(
                    healthTypeIcon(type),
                    contentDescription = null,
                    tint = if (isAbnormal) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.primary,
                )
            },
            trailingContent = {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        MedLogIcon(MedLogIcons.MoreVert, contentDescription = stringResource(R.string.health_more_ops_cd))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_action_edit)) },
                            onClick = { showMenu = false; onEdit() },
                            leadingIcon = { MedLogIcon(MedLogIcons.Edit, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_action_delete)) },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { MedLogIcon(MedLogIcons.Delete, null) },
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

// ─── BMI 卡片 ────────────────────────────────────────────────────────────────
