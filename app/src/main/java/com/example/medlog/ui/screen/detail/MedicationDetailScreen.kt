package com.example.medlog.ui.screen.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medlog.data.model.LogStatus
import com.example.medlog.data.model.TimePeriod
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

    Scaffold(
        topBar = {
            TopAppBar(
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
                                        Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (med == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("未找到该药品")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Basic info card
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailRow("药品名称", med.name)
                        DetailRow("剂量", "${med.dose} ${med.doseUnit}")
                        DetailRow("分类", med.category)
                        val period = TimePeriod.fromKey(med.timePeriod)
                        val timeStr = if (med.timePeriod == "exact")
                            "%02d:%02d".format(med.reminderHour, med.reminderMinute)
                        else period.label
                        DetailRow("服药时段", timeStr)
                        med.stock?.let { DetailRow("库存", "$it ${med.doseUnit}") }
                        if (med.note.isNotBlank()) DetailRow("备注", med.note)
                    }
                }
            }

            // History header
            item {
                Text(
                    "服药历史（近60条）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
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
                    val dateFmt = SimpleDateFormat("M/d HH:mm", Locale.getDefault())
                    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = when (log.status) {
                                LogStatus.TAKEN   -> Icons.Rounded.CheckCircle
                                LogStatus.SKIPPED -> Icons.Rounded.SkipNext
                                LogStatus.MISSED  -> Icons.Rounded.Cancel
                            },
                            contentDescription = null,
                            tint = when (log.status) {
                                LogStatus.TAKEN   -> MaterialTheme.colorScheme.tertiary
                                LogStatus.SKIPPED -> MaterialTheme.colorScheme.outline
                                LogStatus.MISSED  -> MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.size(20.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                dateFmt.format(Date(log.scheduledTimeMs)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            log.actualTakenTimeMs?.let {
                                Text(
                                    "实际：${timeFmt.format(Date(it))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            when (log.status) {
                                LogStatus.TAKEN   -> "已服用"
                                LogStatus.SKIPPED -> "已跳过"
                                LogStatus.MISSED  -> "漏服"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    HorizontalDivider()
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
                TextButton(onClick = { showArchiveDialog = false; viewModel.archiveMedication(); onBack() }) {
                    Text("归档")
                }
            },
            dismissButton = { TextButton(onClick = { showArchiveDialog = false }) { Text("取消") } },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除药品") },
            text = { Text("删除后将清除所有相关记录，此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; viewModel.deleteMedication(); onBack() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
