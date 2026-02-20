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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medlog.data.model.LogStatus
import com.example.medlog.data.model.MedicationLog
import com.example.medlog.data.model.TimePeriod
import com.example.medlog.ui.theme.calendarWarning
import java.text.SimpleDateFormat
import java.util.*

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
        topBar = {
            LargeTopAppBar(
                title = { Text(med?.name ?: "用药详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (med != null) {
                        IconButton(onClick = { onEdit(med.id) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "编辑")
                        }
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("归档") },
                                    onClick = { menuExpanded = false; showArchiveDialog = true },
                                    leadingIcon = { Icon(Icons.Rounded.Archive, null) },
                                )
                                DropdownMenuItem(
                                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
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
                Text("未找到该药品")
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
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        Text(
                            "基本信息",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        DetailRow("药品名称", med.name)
                        // 分类：支持单路径/多路径（用 \n 分隔的多条 ATC/TCM 路径）
                        val storedPaths = med.fullPath.split("\n").filter { it.isNotBlank() }
                        when {
                            storedPaths.size > 1 -> {
                                // 多路径药品：每条路径单独一行展示
                                val tcmSuffix = if (med.isTcm) "（中成药）" else ""
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                ) {
                                    Text(
                                        "分类",
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
                                val display = if (med.isTcm) "${storedPaths[0]}（中成药）" else storedPaths[0]
                                DetailRow("分类", display)
                            }
                            med.category.isNotBlank() -> {
                                val display = if (med.isTcm) "${med.category}（中成药）" else med.category
                                DetailRow("分类", display)
                            }
                            else -> DetailRow("分类", "—")
                        }
                        DetailRow("剂量", "${med.doseQuantity}×${med.dose} ${med.doseUnit}")
                        if (med.isPRN) {
                            DetailRow("用法", "按需服用 (PRN)")
                        } else {
                            val period = TimePeriod.fromKey(med.timePeriod)
                            val timeStr = if (med.timePeriod == "exact")
                                med.reminderTimes.replace(",", " / ")
                            else period.label
                            DetailRow("服药时段", timeStr)
                            val freqStr = when (med.frequencyType) {
                                "daily"         -> "每日"
                                "interval"      -> "每${med.frequencyInterval}天"
                                "specific_days" -> {
                                    val dayNames = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
                                    med.frequencyDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                                        .map { dayNames.getOrElse(it % 7) { it.toString() } }
                                        .joinToString(" ")
                                }
                                else -> med.frequencyType
                            }
                            DetailRow("频率", freqStr)
                        }
                        if (med.isHighPriority) DetailRow("优先级", "高优先级 ⚡")
                        med.stock?.let { DetailRow("当前库存", "$it ${med.doseUnit}") }
                        med.endDate?.let {
                            DetailRow("结束日期", java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
                                .format(java.util.Date(it)))
                        }
                        if (med.notes.isNotBlank()) DetailRow("备注", med.notes)
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
                        "服药历史（近60条）",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (uiState.logs.isNotEmpty()) {
                        Text(
                            "${uiState.taken30d}/${uiState.total30d} 已服（30天内）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (uiState.logs.isEmpty()) {
                item {
                    Text(
                        "暂无服药记录",
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
            title = { Text("归档药品") },
            text = { Text("归档后将保留历史记录，并停止未来的用药提醒。是否继续？") },
            confirmButton = {
                TextButton(onClick = {
                    showArchiveDialog = false
                    viewModel.archiveMedication()
                    onBack()
                }) { Text("归档") }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) { Text("取消") }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除药品") },
            text = { Text("删除后将清除所有相关记录，此操作不可撤销。") },
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
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "近30天坚持率",
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
                        label = "已服用",
                        value = "$taken 次",
                    )
                    StatRow(
                        icon = Icons.Rounded.Cancel,
                        tint = colorScheme.error,
                        label = "漏服/跳过",
                        value = "${(total - taken).coerceAtLeast(0)} 次",
                    )
                    StatRow(
                        icon = Icons.Rounded.DateRange,
                        tint = colorScheme.secondary,
                        label = "计划总次数",
                        value = "$total 次",
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
        shape = RoundedCornerShape(24.dp),
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
                    Text("库存管理", style = MaterialTheme.typography.labelLarge, color = colorScheme.primary)
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
                        if (isLow) "⚠️ 库存低于补货阈值（${refillThreshold} $unit），请及时补充"
                        else "✔ 库存充足，补货阈值：${refillThreshold} $unit",
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
                    "±1次用量（$doseDisplay $unit）",
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
                        Icon(Icons.Rounded.Remove, "减少一次量", Modifier.size(18.dp))
                    }
                    FilledIconButton(
                        onClick = { onAdjustStock(+doseQuantity) },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colorScheme.primaryContainer,
                            contentColor = colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(Icons.Rounded.Add, "增加一次量", Modifier.size(18.dp))
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
                    "批量",
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
    val dateFmt = remember { SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()) }
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
                        "实际服用：${timeFmt.format(Date(it))}",
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
                    LogStatus.TAKEN   -> "已服用"
                    LogStatus.SKIPPED -> "已跳过"
                    LogStatus.MISSED  -> "漏服"
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
