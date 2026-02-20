package com.example.medlog.ui.screen.symptom

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medlog.data.model.SymptomLog
import java.text.SimpleDateFormat
import java.util.*

// ─── 评级 Emoji 映射 ─────────────────────────────────────────────────────────

private fun ratingEmoji(rating: Int) = when (rating) {
    1 -> "😞"
    2 -> "😟"
    3 -> "😐"
    4 -> "🙂"
    else -> "😊"
}

private fun ratingLabel(rating: Int) = when (rating) {
    1 -> "很差"
    2 -> "较差"
    3 -> "一般"
    4 -> "较好"
    else -> "很好"
}

// ─── Screen ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymptomDiaryScreen(
    viewModel: SymptomDiaryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("症状日记") },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::startAdd) {
                Icon(Icons.Filled.Add, contentDescription = "记录症状")
            }
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else if (uiState.logs.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✏️", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "还没有日记记录",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "点击右下角 + 开始记录今天的状态",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(uiState.logs, key = { it.id }) { log ->
                    SymptomLogCard(
                        log = log,
                        dateFormat = dateFormat,
                        onEdit = { viewModel.startEdit(log) },
                        onDelete = { viewModel.deleteLog(log.id) },
                    )
                }
            }
        }
    }

    // ── 新增 / 编辑底部弹窗 ──────────────────────────────────────────────────
    if (uiState.showDialog) {
        AddEditDiarySheet(
            draft = uiState.draft,
            onDismiss = viewModel::dismissDialog,
            onRatingChange = viewModel::onRatingChange,
            onToggleSymptom = viewModel::onToggleSymptom,
            onCustomSymptomChange = viewModel::onCustomSymptomChange,
            onAddCustomSymptom = viewModel::onAddCustomSymptom,
            onToggleSideEffect = viewModel::onToggleSideEffect,
            onCustomSideEffectChange = viewModel::onCustomSideEffectChange,
            onAddCustomSideEffect = viewModel::onAddCustomSideEffect,
            onNoteChange = viewModel::onNoteChange,
            onSave = viewModel::saveLog,
        )
    }
}

// ─── 日志卡片 ─────────────────────────────────────────────────────────────────

@Composable
private fun SymptomLogCard(
    log: SymptomLog,
    dateFormat: SimpleDateFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            // 头部：日期 + 评级 + 操作按钮
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dateFormat.format(Date(log.recordedAt)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${ratingEmoji(log.overallRating)} ${ratingLabel(log.overallRating)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // 关联药品
            if (log.medicationName.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "💊 ${log.medicationName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // 症状 Chips
            if (log.symptomList.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "症状",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    log.symptomList.forEach { s ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(s, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            // 副作用 Chips
            if (log.sideEffectList.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "副作用",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    log.sideEffectList.forEach { se ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(se, style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                labelColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        )
                    }
                }
            }

            // 备注
            if (log.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    log.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除记录") },
            text = { Text("确定要删除这条症状日记吗？此操作无法撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}

// ─── 新增 / 编辑 ModalBottomSheet ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditDiarySheet(
    draft: DiaryDraftState,
    onDismiss: () -> Unit,
    onRatingChange: (Int) -> Unit,
    onToggleSymptom: (String) -> Unit,
    onCustomSymptomChange: (String) -> Unit,
    onAddCustomSymptom: () -> Unit,
    onToggleSideEffect: (String) -> Unit,
    onCustomSideEffectChange: (String) -> Unit,
    onAddCustomSideEffect: () -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (draft.editingId == null) "记录今天的状态" else "编辑记录",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            // ── 评级选择 ──────────────────────────────────────────────────
            Text("整体感受", style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                (1..5).forEach { r ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        FilledIconToggleButton(
                            checked = draft.rating == r,
                            onCheckedChange = { onRatingChange(r) },
                        ) {
                            Text(ratingEmoji(r), style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            ratingLabel(r),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (draft.rating == r)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── 症状快选 ──────────────────────────────────────────────────
            Text("症状（可多选）", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESET_SYMPTOMS.forEach { s ->
                    FilterChip(
                        selected = s in draft.symptoms,
                        onClick = { onToggleSymptom(s) },
                        label = { Text(s) },
                    )
                }
            }
            // 自定义症状输入
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.customSymptom,
                    onValueChange = onCustomSymptomChange,
                    label = { Text("自定义症状") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = onAddCustomSymptom,
                    enabled = draft.customSymptom.isNotBlank(),
                ) { Text("添加") }
            }

            HorizontalDivider()

            // ── 副作用快选 ─────────────────────────────────────────────────
            Text("副作用（可多选）", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESET_SIDE_EFFECTS.forEach { se ->
                    FilterChip(
                        selected = se in draft.sideEffects,
                        onClick = { onToggleSideEffect(se) },
                        label = { Text(se) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    )
                }
            }
            // 自定义副作用输入
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.customSideEffect,
                    onValueChange = onCustomSideEffectChange,
                    label = { Text("自定义副作用") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = onAddCustomSideEffect,
                    enabled = draft.customSideEffect.isNotBlank(),
                ) { Text("添加") }
            }

            HorizontalDivider()

            // ── 备注 ────────────────────────────────────────────────────────
            OutlinedTextField(
                value = draft.note,
                onValueChange = onNoteChange,
                label = { Text("备注（可选）") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── 操作按钮 ─────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("取消") }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(2f),
                ) { Text("保存记录") }
            }
        }
    }
}
