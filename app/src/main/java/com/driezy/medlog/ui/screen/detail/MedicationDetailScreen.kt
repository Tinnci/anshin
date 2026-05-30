package com.driezy.medlog.ui.screen.detail

import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.data.model.LogStatus
import com.driezy.medlog.data.model.MedicationLog
import com.driezy.medlog.data.model.TimePeriod
import com.driezy.medlog.ui.util.labelRes
import com.driezy.medlog.ui.util.formatDose
import com.driezy.medlog.ui.util.formatDosePrecise
import com.driezy.medlog.ui.util.displayName
import com.driezy.medlog.ui.theme.MedLogSpacing
import java.text.SimpleDateFormat
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.driezy.medlog.R
import java.util.*

/** 剂型 key → Material Icon（与添加界面保持一致） */
import com.driezy.medlog.ui.util.formIcon

/** 剂型 key → 本地化标签 */
@Composable
internal fun formLabel(form: String): String = when (form) {
    "capsule" -> stringResource(R.string.detail_form_capsule)
    "liquid"  -> stringResource(R.string.detail_form_liquid)
    "powder"  -> stringResource(R.string.detail_form_powder)
    else      -> stringResource(R.string.detail_form_tablet)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val medDisplayName = med?.displayName()
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
                            MedLogIcon(
                                icon = formIcon(med.form),
                                contentDescription = formLabel(med.form),
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(medDisplayName ?: med.name)
                        }
                    } else {
                        Text(stringResource(R.string.detail_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        MedLogIcon(MedLogIcons.ArrowBack, contentDescription = stringResource(R.string.detail_back))
                    }
                },
                actions = {
                    if (med != null) {
                        IconButton(onClick = { onEdit(med.id) }) {
                            MedLogIcon(MedLogIcons.Edit, contentDescription = stringResource(R.string.detail_edit_cd))
                        }
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                MedLogIcon(MedLogIcons.MoreVert, contentDescription = stringResource(R.string.detail_more_cd))
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.archive)) },
                                    onClick = { menuExpanded = false; showArchiveDialog = true },
                                    leadingIcon = { MedLogIcon(MedLogIcons.Archive, null) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                    onClick = { menuExpanded = false; showDeleteDialog = true },
                                    leadingIcon = {
                                        MedLogIcon(
                                            MedLogIcons.Delete, null,
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
                LoadingIndicator()
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
            contentPadding = MedLogSpacing.ScreenContentDefault,
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
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
                        modifier = Modifier.padding(MedLogSpacing.Large),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        Text(
                            stringResource(R.string.detail_section_basic),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = MedLogSpacing.Small),
                        )
                        DetailRow(stringResource(R.string.detail_label_name), medDisplayName ?: med.name)
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
                        DetailRow(stringResource(R.string.detail_label_dose),
                            "${med.doseQuantity.formatDosePrecise()} ${med.doseUnit}")
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
                        val endDateFmt = remember { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()) }
                        med.endDate?.let {
                            DetailRow(stringResource(R.string.detail_label_end_date), endDateFmt.format(Date(it)))
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
