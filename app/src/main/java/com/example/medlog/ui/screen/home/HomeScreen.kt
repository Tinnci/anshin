package com.example.medlog.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.graphics.Color
import com.example.medlog.data.model.TimePeriod
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import com.example.medlog.ui.utils.generateQrBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medlog.R
import com.example.medlog.data.model.DrugInteraction
import com.example.medlog.data.model.InteractionSeverity
import com.example.medlog.ui.components.MedicationCard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onAddMedication: () -> Unit,
    onMedicationClick: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showQrDialog by remember { mutableStateOf(false) }
    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()
    val importError by viewModel.importError.collectAsStateWithLifecycle()
    // 预捕获所有 snackbar 字符串，保证语言切换时内容同步更新
    val undoLabel = stringResource(R.string.home_snackbar_undo)
    val msgAllTaken = stringResource(R.string.home_snackbar_all_taken)
    val fmtUndoSkip = stringResource(R.string.home_snackbar_undo_skip)
    val fmtReset = stringResource(R.string.home_snackbar_reset)
    val fmtTaken = stringResource(R.string.home_snackbar_taken)
    val fmtSkipped = stringResource(R.string.home_snackbar_skipped)
    val fmtPeriodAllTaken = stringResource(R.string.home_snackbar_period_all_taken)
    val fmtPrnUndo = stringResource(R.string.home_snackbar_prn_undo)
    val fmtPrnTaken = stringResource(R.string.home_snackbar_prn_taken)
    val fmtImportSuccess = stringResource(R.string.qr_import_success)

    // 收集导入成功事件 → Snackbar
    LaunchedEffect(Unit) {
        viewModel.importSuccess.collect { count ->
            snackbarHostState.showSnackbar(fmtImportSuccess.format(count))
        }
    }

    // Pending items for "take all" button (excluding PRN on-demand meds)
    val pendingItems = uiState.items.filter { !it.isTaken && !it.isSkipped && !it.medication.isPRN }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.home_title), fontWeight = FontWeight.Bold)
                        Text(
                            todayDateString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(Icons.Rounded.QrCode2, contentDescription = stringResource(R.string.home_share_qr_cd))
                    }
                    IconButton(onClick = viewModel::toggleGroupBy) {
                        Icon(
                            imageVector = if (uiState.groupByTime) Icons.Rounded.Category else Icons.Rounded.AccessTime,
                            contentDescription = if (uiState.groupByTime) stringResource(R.string.home_group_toggle_by_category) else stringResource(R.string.home_group_toggle_by_time),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddMedication,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_fab_add)) },
                expanded = uiState.items.isEmpty(),
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            // ── 进度卡片 ──────────────────────────────────────
            item {
                AnimatedProgressCard(
                    taken = uiState.takenCount,
                    total = uiState.totalCount,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }

            // ── 连续打卡 Streak badge ─────────────────────────
            if (uiState.currentStreak > 0) {
                item {
                    StreakBadgeRow(
                        currentStreak = uiState.currentStreak,
                        longestStreak = uiState.longestStreak,
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            // ── "下一服" 智能提示（部分完成时显示）────────────────
            val nextUp = uiState.nextUpPeriod
            if (nextUp != null && uiState.takenCount > 0 && uiState.takenCount < uiState.totalCount) {
                item {
                    NextUpChip(period = nextUp.first, time = nextUp.second)
                    Spacer(Modifier.height(4.dp))
                }
            }

            // ── 低库存警告 banner ──────────────────────────────
            val lowStockItems = uiState.items.filter { item ->
                val stock = item.medication.stock ?: return@filter false
                val threshold = item.medication.refillThreshold ?: return@filter false
                stock <= threshold
            }
            if (lowStockItems.isNotEmpty()) {
                item {
                    LowStockBanner(
                        medications = lowStockItems.map { it.medication.name to ((it.medication.stock ?: 0.0) to it.medication.doseUnit) },
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            // ── 药品相互作用警告 ───────────────────────────
            if (uiState.interactions.isNotEmpty()) {
                item {
                    InteractionBannerCard(
                        interactions = uiState.interactions,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            // ── 一键全服（Flutter 参考：列表顶部大按钮，>1待服时出现）────
            if (pendingItems.size > 1) {
                item {
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.takeAll()
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = msgAllTaken,
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Rounded.DoneAll, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.home_take_all_btn, pendingItems.size),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            // ── 空状态 ────────────────────────────────────────
            if (uiState.items.isEmpty()) {
                item {
                    EmptyMedicationState(onAddMedication = onAddMedication)
                }
            }

            // ── 药品卡片列表（可按时段或分类分组）────────────────
            if (uiState.groupByTime) {
                // M3 Expressive 风格：每个时段一张卡片，头部含一键服用
                uiState.groupedByTimePeriod.forEach { (timePeriod, groupItems) ->
                    item(key = "tgroup_${timePeriod.key}", contentType = "timeGroup") {
                        val periodLabel = stringResource(timePeriod.labelRes)
                        TimePeriodGroupCard(
                            timePeriod = timePeriod,
                            items = groupItems,
                            onToggleTaken = { item ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (item.isSkipped) {
                                    viewModel.undoByMedicationId(item.medication.id)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            fmtUndoSkip.format(item.medication.name),
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                } else {
                                    val wasTaken = item.isTaken
                                    viewModel.toggleMedicationStatus(item)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = (if (wasTaken) fmtReset else fmtTaken).format(item.medication.name),
                                            actionLabel = undoLabel,
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.undoByMedicationId(item.medication.id)
                                        }
                                    }
                                }
                            },
                            onSkip = { item ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.skipMedication(item)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        fmtSkipped.format(item.medication.name),
                                        actionLabel = undoLabel,
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoByMedicationId(item.medication.id)
                                    }
                                }
                            },
                            onTakeAll = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.takeAllForPeriod(timePeriod.key)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        fmtPeriodAllTaken.format(periodLabel),
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            },
                            onClick = onMedicationClick,
                            autoCollapse = uiState.autoCollapseCompletedGroups,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            } else {
                // 分类分组：扁平卡片列表
                uiState.groupedItems.forEach { (category, groupItems) ->
                    if (category.isNotBlank()) {
                        item(key = "header_$category", contentType = "header") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 2.dp),
                            ) {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    itemsIndexed(
                        groupItems,
                        key = { _, it -> it.medication.id },
                    ) { idx, item ->
                        val motionScheme = MaterialTheme.motionScheme
                        var visible by remember(item.medication.id) { mutableStateOf(false) }
                        LaunchedEffect(item.medication.id) {
                            delay(idx * 30L)   // 基于组内索引，而非全局，避免底部首次出现延迟
                            visible = true
                        }
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                                    slideInVertically(motionScheme.defaultSpatialSpec()) { it / 4 },
                        ) {
                            MedicationCard(
                                item = item,
                                onToggleTaken = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (item.isSkipped) {
                                        viewModel.undoByMedicationId(item.medication.id)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                fmtUndoSkip.format(item.medication.name),
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                    } else {
                                        val wasTaken = item.isTaken
                                        viewModel.toggleMedicationStatus(item)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = (if (wasTaken) fmtReset else fmtTaken).format(item.medication.name),
                                                actionLabel = undoLabel,
                                                duration = SnackbarDuration.Short,
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.undoByMedicationId(item.medication.id)
                                            }
                                        }
                                    }
                                },
                                onSkip = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.skipMedication(item)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            fmtSkipped.format(item.medication.name),
                                            actionLabel = undoLabel,
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.undoByMedicationId(item.medication.id)
                                        }
                                    }
                                },
                                onClick = { onMedicationClick(item.medication.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }

            // ── PRN 按需用药区域 ───────────────────────────────
            if (uiState.prnItems.isNotEmpty()) {
                item(key = "prnSection", contentType = "prnSection") {
                    PRNSectionCard(
                        items = uiState.prnItems,
                        onToggleTaken = { item ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val wasTaken = item.isTaken
                            viewModel.toggleMedicationStatus(item)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = (if (wasTaken) fmtPrnUndo else fmtPrnTaken).format(item.medication.name),
                                    actionLabel = undoLabel,
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.undoByMedicationId(item.medication.id)
                                }
                            }
                        },
                        onClick = onMedicationClick,
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            // ── 底部间距（FAB 避让）──────────────────────────
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    if (showQrDialog) {
        MedicationQrDialog(
            items = uiState.items,
            onDismiss = { showQrDialog = false },
            generateExportUri = viewModel::generateExportUri,
            onQrScanned = viewModel::onQrScanned,
        )
    }

    // ── 导入预览对话框 ─────────────────────────────────────────────────────────
    if (importPreview != null) {
        ImportPreviewDialog(
            plan = importPreview!!,
            onMerge = { viewModel.confirmImport(com.example.medlog.domain.ImportMode.MERGE) },
            onReplace = { viewModel.confirmImport(com.example.medlog.domain.ImportMode.REPLACE) },
            onDismiss = viewModel::clearImportPreview,
        )
    }

    // ── 无效 QR 码提示 ─────────────────────────────────────────────────────────
    if (importError != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearImportPreview,
            title = { Text(stringResource(R.string.qr_scan_title)) },
            text = { Text(stringResource(R.string.qr_invalid)) },
            confirmButton = {
                TextButton(onClick = viewModel::clearImportPreview) {
                    Text(stringResource(R.string.home_close))
                }
            },
        )
    }
}

