package com.example.medlog.ui.screen.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medlog.data.model.LogStatus
import com.example.medlog.data.model.MedicationLog
import com.example.medlog.data.model.TimePeriod
import com.example.medlog.ui.theme.calendarWarning
import java.text.SimpleDateFormat
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.medlog.R
import java.util.*

/** 剂型 key → Material Icon（与添加界面保持一致） */
private fun formIcon(form: String): ImageVector = when (form) {
    "capsule" -> Icons.Rounded.Science
    "liquid"  -> Icons.Rounded.LocalDrink
    "powder"  -> Icons.Rounded.WaterDrop
    else      -> Icons.Rounded.Medication
}

/** 剂型 key → 本地化标签 */
@Composable
private fun formLabel(form: String): String = when (form) {
    "capsule" -> stringResource(R.string.detail_form_capsule)
    "liquid"  -> stringResource(R.string.detail_form_liquid)
    "powder"  -> stringResource(R.string.detail_form_powder)
    else      -> stringResource(R.string.detail_form_tablet)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationDetailScreen(
    medicationId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: MedicationDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(medicationId) { viewModel.loadMedication(medicationId) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }

    val med = uiState.medication
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    if (med != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = formIcon(med.form),
                                contentDescription = formLabel(med.form),
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(med.name)
                        }
                    } else {
                        Text(stringResource(R.string.detail_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.detail_back))
                    }
                },
                actions = {
                    if (med != null) {
                        IconButton(onClick = { onEdit(med.id) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.detail_edit_cd))
                        }
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.detail_more_cd))
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.archive)) },
                                    onClick = { menuExpanded = false; showArchiveDialog = true },
                                    leadingIcon = { Icon(Icons.Rounded.Archive, null) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                    onClick = { menuExpanded = false; showDeleteDialog = true },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.Delete, null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (med == null) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.detail_not_found))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 坚持率统计卡 ──────────────────────────────────
            item {
                AdherenceStatsCard(
                    adherence = uiState.adherence30d,
                    taken = uiState.taken30d,
                    total = uiState.total30d,
                )
            }

            // ── 基本信息卡 ────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        Text(
                            stringResource(R.string.detail_section_basic),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        DetailRow(stringResource(R.string.detail_label_name), med.name)
                        DetailRow(stringResource(R.string.detail_label_form), formLabel(med.form))
                        // 分类：支持单路径/多路径（用 \n 分隔的多条 ATC/TCM 路径）
                        val storedPaths = med.fullPath.split("\n").filter { it.isNotBlank() }
                        when {
                            storedPaths.size > 1 -> {
                                // 多路径药品：每条路径单独一行展示
                                val tcmSuffix = if (med.isTcm) stringResource(R.string.detail_tcm_suffix) else ""
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.detail_category_label),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    storedPaths.forEachIndexed { index, path ->
                                        Text(
                                            text = if (index == 0) "$path$tcmSuffix" else path,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                            storedPaths.size == 1 -> {
                                val display = if (med.isTcm) "${storedPaths[0]}${stringResource(R.string.detail_tcm_suffix)}" else storedPaths[0]
                                DetailRow(stringResource(R.string.detail_label_category), display)
                            }
                            med.category.isNotBlank() -> {
                                val display = if (med.isTcm) "${med.category}${stringResource(R.string.detail_tcm_suffix)}" else med.category
                                DetailRow(stringResource(R.string.detail_category_label), display)
                            }
                            else -> DetailRow(stringResource(R.string.detail_label_category), "—")
                        }
                        DetailRow(stringResource(R.string.detail_label_dose), run {
                            val qty = med.doseQuantity
                            val qtyStr = if (qty == qty.toLong().toDouble()) "${qty.toLong()}"
                                         else "%.2f".format(qty).trimEnd('0').trimEnd('.')
                            "$qtyStr ${med.doseUnit}"
                        })
                        if (med.isPRN) {
                            DetailRow(stringResource(R.string.detail_label_usage), stringResource(R.string.detail_usage_prn))
                        } else {
                            val period = TimePeriod.fromKey(med.timePeriod)
                            val timeStr = if (med.timePeriod == "exact")
                                med.reminderTimes.replace(",", " / ")
                            else stringResource(period.labelRes)
                            DetailRow(stringResource(R.string.detail_label_period), timeStr)
                            val freqStr = when (med.frequencyType) {
                                "daily"         -> stringResource(R.string.detail_freq_daily)
                                "interval"      -> pluralStringResource(R.plurals.detail_freq_interval, med.frequencyInterval, med.frequencyInterval)
                                "specific_days" -> {
                                    val dayNames = listOf(
                                        stringResource(R.string.detail_day_0),
                                        stringResource(R.string.detail_day_1),
                                        stringResource(R.string.detail_day_2),
                                        stringResource(R.string.detail_day_3),
                                        stringResource(R.string.detail_day_4),
                                        stringResource(R.string.detail_day_5),
                                        stringResource(R.string.detail_day_6),
                                    )
                                    med.frequencyDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                                        .map { dayNames.getOrElse(it % 7) { it.toString() } }
                                        .joinToString(" ")
                                }
                                else -> med.frequencyType
                            }
                            DetailRow(stringResource(R.string.detail_label_frequency), freqStr)
                        }
                        if (med.isHighPriority) DetailRow(stringResource(R.string.detail_label_priority), stringResource(R.string.detail_priority_high))
                        med.stock?.let { DetailRow(stringResource(R.string.detail_label_stock), "$it ${med.doseUnit}") }
                        med.endDate?.let {
                            DetailRow(stringResource(R.string.detail_label_end_date), java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
                                .format(java.util.Date(it)))
                        }
                        if (med.notes.isNotBlank()) DetailRow(stringResource(R.string.detail_label_notes), med.notes)
                    }
                }
            }

            // ── 库存进度条（如果有库存信息） ──────────────────
            val stock = med.stock
            val refillThreshold = med.refillThreshold
            if (stock != null) {
                item {
                    StockCard(
                        stock = stock,
                        refillThreshold = refillThreshold,
                        unit = med.doseUnit,
                        doseQuantity = med.doseQuantity,
                        onAdjustStock = { delta -> viewModel.adjustStock(delta) },
                    )
                }
            }

            // ── 服药历史标题 ──────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.detail_history_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (uiState.logs.isNotEmpty()) {
                        Text(
                            pluralStringResource(R.plurals.detail_history_count, uiState.taken30d, uiState.taken30d, uiState.total30d),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (uiState.logs.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.detail_no_logs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(uiState.logs, key = { it.id }) { log ->
                    DetailLogRow(log = log)
                }
            }
        }
    }

    if (showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title = { Text(stringResource(R.string.detail_archive_title)) },
            text = { Text(stringResource(R.string.detail_archive_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showArchiveDialog = false
                    viewModel.archiveMedication()
                    onBack()
                }) { Text(stringResource(R.string.archive)) }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteMedication()
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.common_action_cancel)) }
            },
        )
    }
}

// ─── 坚持率统计卡 ─────────────────────────────────────────────

@Composable
private fun AdherenceStatsCard(adherence: Float, taken: Int, total: Int) {
    val colorScheme = MaterialTheme.colorScheme
    val adherenceColor by animateColorAsState(
        targetValue = when {
            adherence >= 0.9f -> colorScheme.tertiary
            adherence >= 0.6f -> calendarWarning
            else              -> colorScheme.error
        },
        animationSpec = tween(600),
        label = "adhColor",
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.detail_adherence_title),
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // 圆形进度指示器
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { adherence },
                        modifier = Modifier.size(72.dp),
                        color = adherenceColor,
                        trackColor = adherenceColor.copy(alpha = 0.15f),
                        strokeWidth = 7.dp,
                    )
                    Text(
                        "${(adherence * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = adherenceColor,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatRow(
                        icon = Icons.Rounded.CheckCircle,
                        tint = colorScheme.tertiary,
                        label = stringResource(R.string.medication_taken),
                        value = pluralStringResource(R.plurals.detail_count_times, taken, taken),
                    )
                    StatRow(
                        icon = Icons.Rounded.Cancel,
                        tint = colorScheme.error,
                        label = stringResource(R.string.detail_missed_skipped),
                        value = pluralStringResource(R.plurals.detail_count_times, (total - taken).coerceAtLeast(0), (total - taken).coerceAtLeast(0)),
                    )
                    StatRow(
                        icon = Icons.Rounded.DateRange,
                        tint = colorScheme.secondary,
                        label = stringResource(R.string.detail_total_count),
                        value = pluralStringResource(R.plurals.detail_count_times, total, total),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    value: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

// ─── 库存快捷操作卡 ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StockCard(
    stock: Double,
    refillThreshold: Double?,
    unit: String,
    doseQuantity: Double,
    onAdjustStock: (Double) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isLow = refillThreshold != null && stock <= refillThreshold
    val stockColor = if (isLow) colorScheme.error else colorScheme.tertiary

    val stockDisplay = if (stock == stock.toLong().toDouble()) "${stock.toLong()}" else "%.1f".format(stock)
    val doseDisplay = if (doseQuantity == doseQuantity.toLong().toDouble()) "${doseQuantity.toLong()}" else "%.1f".format(doseQuantity)

    // 常用补药预设量
    val presets = listOf("+10" to 10.0, "+30" to 30.0, "+60" to 60.0, "+90" to 90.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 标题行 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Inventory, null, tint = stockColor, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.detail_stock_title), style = MaterialTheme.typography.labelLarge, color = colorScheme.primary)
                }
                Text(
                    "$stockDisplay $unit",
                    style = MaterialTheme.typography.titleSmall,
                    color = stockColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // ── 状态提示 ──
            if (refillThreshold != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = (if (isLow) colorScheme.errorContainer else colorScheme.tertiaryContainer).copy(alpha = 0.7f),
                ) {
                    Text(
                        if (isLow) stringResource(R.string.detail_stock_low_warning, refillThreshold.toString(), unit)
                        else stringResource(R.string.detail_stock_ok, refillThreshold.toString(), unit),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isLow) colorScheme.onErrorContainer else colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ── 快捷调整：M3 Expressive ButtonGroup ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.detail_stock_adjust_hint, doseDisplay, unit),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                @Suppress("DEPRECATION")
                ButtonGroup {
                    OutlinedIconButton(
                        onClick = { onAdjustStock(-doseQuantity) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Rounded.Remove, stringResource(R.string.detail_stock_decrease_cd), Modifier.size(18.dp))
                    }
                    FilledIconButton(
                        onClick = { onAdjustStock(+doseQuantity) },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colorScheme.primaryContainer,
                            contentColor = colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.detail_stock_increase_cd), Modifier.size(18.dp))
                    }
                }
            }

            // ── 批量补入预设 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.detail_batch_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                )
                presets.forEach { (label, amount) ->
                    SuggestionChip(
                        onClick = { onAdjustStock(amount) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ─── 日志行 ───────────────────────────────────────────────────

@Composable
private fun DetailLogRow(log: MedicationLog) {
    val logItemFmt = stringResource(R.string.date_format_log_item)
    val dateFmt = remember(logItemFmt) { SimpleDateFormat(logItemFmt, Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val colorScheme = MaterialTheme.colorScheme
    val statusColor = when (log.status) {
        LogStatus.TAKEN   -> colorScheme.tertiary
        LogStatus.SKIPPED -> colorScheme.outline
        LogStatus.MISSED  -> colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 状态点
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Column(Modifier.weight(1f)) {
            Text(
                dateFmt.format(Date(log.scheduledTimeMs)),
                style = MaterialTheme.typography.bodyMedium,
            )
            log.actualTakenTimeMs?.let {
                if (log.status == LogStatus.TAKEN) {
                    Text(
                        stringResource(R.string.detail_log_actual_time, timeFmt.format(Date(it))),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (log.notes.isNotBlank()) {
                Text(
                    log.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = statusColor.copy(alpha = 0.12f),
        ) {
            Text(
                text = when (log.status) {
                    LogStatus.TAKEN   -> stringResource(R.string.medication_taken)
                    LogStatus.SKIPPED -> stringResource(R.string.medication_skipped)
                    LogStatus.MISSED  -> stringResource(R.string.medication_missed)
                },
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
}

// ─── 工具 Composable ──────────────────────────────────────────

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
