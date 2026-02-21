package com.example.medlog.ui.screen.health

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medlog.data.model.HealthRecord
import com.example.medlog.data.model.HealthType
import java.text.SimpleDateFormat
import java.util.*

// ─── 类型图标映射 ────────────────────────────────────────────────────────────

@Composable
private fun healthTypeIcon(type: HealthType) = when (type) {
    HealthType.BLOOD_PRESSURE -> Icons.Rounded.Bloodtype
    HealthType.BLOOD_GLUCOSE  -> Icons.Rounded.WaterDrop
    HealthType.WEIGHT         -> Icons.Rounded.FitnessCenter
    HealthType.HEART_RATE     -> Icons.Rounded.Favorite
    HealthType.TEMPERATURE    -> Icons.Rounded.Thermostat
    HealthType.SPO2           -> Icons.Rounded.AirlineStops
}

// ─── 主屏幕 ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    viewModel: HealthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("健康体征") },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::startAdd) {
                Icon(Icons.Rounded.Add, contentDescription = "记录体征")
            }
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 80.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── 类型过滤 Chips ─────────────────────────────────────────
                item(key = "type_filter") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected   = uiState.selectedType == null,
                            onClick    = { viewModel.selectType(null) },
                            label      = { Text("全部") },
                            leadingIcon = if (uiState.selectedType == null) ({
                                Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                            }) else null,
                        )
                        HealthType.entries.forEach { type ->
                            FilterChip(
                                selected = uiState.selectedType == type,
                                onClick  = { viewModel.selectType(if (uiState.selectedType == type) null else type) },
                                label    = { Text(type.label) },
                                leadingIcon = {
                                    Icon(healthTypeIcon(type), null, Modifier.size(16.dp))
                                },
                            )
                        }
                    }
                }

                // ── 体征摘要卡片（横向滚动） ───────────────────────────────
                if (uiState.stats.isNotEmpty()) {
                    item(key = "stats_row") {
                        val visibleStats = if (uiState.selectedType == null) uiState.stats
                            else uiState.stats.filter { it.type == uiState.selectedType }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            visibleStats.forEach { stat ->
                                HealthStatCard(stat = stat)
                            }
                        }
                    }
                }

                // ── 记录列表 ───────────────────────────────────────────────
                if (uiState.records.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.MonitorHeart,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.outlineVariant,
                                )
                                Text(
                                    "暂无记录，点击 + 开始记录",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(uiState.records, key = { it.id }) { record ->
                        HealthRecordItem(
                            record    = record,
                            onEdit    = { viewModel.startEdit(record) },
                            onDelete  = { viewModel.requestDelete(record) },
                        )
                    }
                }
            }
        }
    }

    // ── 新增/编辑底部表单 ────────────────────────────────────────────────────
    if (uiState.showAddSheet) {
        AddEditHealthSheet(
            draft           = uiState.draft,
            onDismiss       = viewModel::dismissSheet,
            onTypeChange    = viewModel::onDraftTypeChange,
            onValueChange   = viewModel::onDraftValueChange,
            onSecondaryChange = viewModel::onDraftSecondaryChange,
            onNotesChange   = viewModel::onDraftNotesChange,
            onSave          = viewModel::saveRecord,
        )
    }

    // ── 删除确认对话框 ────────────────────────────────────────────────────────
    if (uiState.deleteTarget != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("删除记录") },
            text  = { Text("确定要删除这条体征记录吗？此操作无法撤销。") },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDelete,
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("取消") }
            },
        )
    }
}

// ─── 体征摘要卡片 ────────────────────────────────────────────────────────────

@Composable
private fun HealthStatCard(stat: HealthTypeStat) {
    val dateFormat = remember { SimpleDateFormat("MM-dd", Locale.getDefault()) }
    val containerColor = if (stat.isAbnormal)
        MaterialTheme.colorScheme.errorContainer
    else
        MaterialTheme.colorScheme.secondaryContainer

    ElevatedCard(
        modifier = Modifier.width(148.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    healthTypeIcon(stat.type),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (stat.isAbnormal) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stat.type.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (stat.isAbnormal) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                stat.type.formatValue(stat.latestValue, stat.latestSecondary),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (stat.avg7d != null) {
                    Text(
                        "7日均 ${"%.1f".format(stat.avg7d)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val trendArrow = when (stat.trend) {
                        1  -> "↑"
                        -1 -> "↓"
                        0  -> "→"
                        else -> ""
                    }
                    if (trendArrow.isNotEmpty()) {
                        Text(
                            trendArrow,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (stat.trend) {
                                1  -> MaterialTheme.colorScheme.error
                                -1 -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                } else {
                    Text(
                        dateFormat.format(Date(stat.latestTime)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ─── 单条记录 ListItem ────────────────────────────────────────────────────────

@Composable
private fun HealthRecordItem(
    record: HealthRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val type = remember(record.type) { HealthType.fromName(record.type) }
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    var showMenu by remember { mutableStateOf(false) }
    val isAbnormal = !type.isNormal(record.value)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAbnormal) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                             else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    type.formatValue(record.value, record.secondaryValue),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            overlineContent = {
                Text(type.label)
            },
            supportingContent = {
                Column {
                    Text(
                        timeFormat.format(Date(record.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (record.notes.isNotBlank()) {
                        Text(
                            record.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            leadingContent = {
                Icon(
                    healthTypeIcon(type),
                    contentDescription = null,
                    tint = if (isAbnormal) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.primary,
                )
            },
            trailingContent = {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            onClick = { showMenu = false; onEdit() },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

// ─── 新增/编辑 ModalBottomSheet ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditHealthSheet(
    draft: HealthDraftState,
    onDismiss: () -> Unit,
    onTypeChange: (HealthType) -> Unit,
    onValueChange: (String) -> Unit,
    onSecondaryChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (draft.editingId == null) "记录体征" else "编辑记录",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            // ── 类型选择器 ─────────────────────────────────────────────
            Text("体征类型", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HealthType.entries.forEach { type ->
                    FilterChip(
                        selected    = draft.type == type,
                        onClick     = { onTypeChange(type) },
                        label       = { Text(type.label) },
                        leadingIcon = {
                            Icon(healthTypeIcon(type), null, Modifier.size(16.dp))
                        },
                    )
                }
            }

            // ── 数值输入 ───────────────────────────────────────────────
            if (draft.type == HealthType.BLOOD_PRESSURE) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = draft.value,
                        onValueChange = onValueChange,
                        label = { Text("收缩压") },
                        suffix = { Text("mmHg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = draft.secondaryValue,
                        onValueChange = onSecondaryChange,
                        label = { Text("舒张压") },
                        suffix = { Text("mmHg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                OutlinedTextField(
                    value = draft.value,
                    onValueChange = onValueChange,
                    label = { Text(draft.type.label) },
                    suffix = { Text(draft.type.unit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        val type = draft.type
                        if (type != HealthType.WEIGHT) {
                            Text("正常范围：${type.normalMin}–${type.normalMax} ${type.unit}")
                        }
                    },
                )
            }

            // ── 备注 ──────────────────────────────────────────────────
            OutlinedTextField(
                value = draft.notes,
                onValueChange = onNotesChange,
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, null) },
            )

            // ── 操作按钮 ──────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("取消") }
                val isValid = draft.value.toDoubleOrNull() != null &&
                    (draft.type != HealthType.BLOOD_PRESSURE || draft.secondaryValue.toDoubleOrNull() != null)
                Button(
                    onClick  = onSave,
                    enabled  = isValid,
                    modifier = Modifier.weight(2f),
                ) { Text("保存") }
            }
        }
    }
}
