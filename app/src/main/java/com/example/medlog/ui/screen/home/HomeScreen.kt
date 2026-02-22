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

// ── 时段分组卡片（M3 Expressive 风格）─────────────────────────────────────────

/**
 * 将同一服药时段的所有药品包裹在一张圆角卡片内。
 *
 * 卡片头部：时段图标 + 时段名 + 待服数 badge + 「一键服用本时段」按钮。
 * 卡片内容：每个药品一行，行间以 HorizontalDivider 分隔；进入动画逐项延迟。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TimePeriodGroupCard(
    timePeriod: com.example.medlog.data.model.TimePeriod,
    items: List<MedicationWithStatus>,
    onToggleTaken: (MedicationWithStatus) -> Unit,
    onSkip: (MedicationWithStatus) -> Unit,
    onTakeAll: () -> Unit,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    autoCollapse: Boolean = true,
) {
    val pendingCount = items.count { !it.isTaken && !it.isSkipped }
    val allDone = pendingCount == 0
    val motionScheme = MaterialTheme.motionScheme
    // allDone 变化时重算展开状态：已全服且开启自动折叠时默认折叠
    var isExpanded by remember(allDone) { mutableStateOf(!allDone || !autoCollapse) }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (allDone)
                MaterialTheme.colorScheme.surfaceContainerLowest
            else
                MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (allDone) 0.dp else 1.dp,
        ),
    ) {
        // ── 卡片头部 ──────────────────────────────────────────
        // 对非精确时段，取首项的提醒时间作为代表性展示时间
        val representativeTime = if (timePeriod.key != "exact") {
            items.firstOrNull()?.medication
                ?.let { "%02d:%02d".format(it.reminderHour, it.reminderMinute) }
        } else null

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = timePeriod.icon,
                contentDescription = null,
                tint = if (allDone) MaterialTheme.colorScheme.outline
                       else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(timePeriod.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (allDone) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.primary,
                )
                if (representativeTime != null) {
                    Text(
                        text = representativeTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (allDone) MaterialTheme.colorScheme.outlineVariant
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 待服数量 Badge（allDone 时显示胶囊徽章）
            if (allDone) {
                SuggestionChip(
                    onClick = { isExpanded = !isExpanded },
                    icon = {
                        Icon(
                            Icons.Rounded.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                    label = {
                        Text(
                            stringResource(R.string.home_period_all_done_chip),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.tertiary,
                    ),
                    border = null,
                    modifier = Modifier.height(28.dp),
                )
                // 展开/折叠指示箭头
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) stringResource(R.string.home_period_collapse) else stringResource(R.string.home_period_expand),
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) { Text("$pendingCount") }
                // 一键服用本时段 — pill 形，比单药按钮更大更显眼
                if (pendingCount >= 1) {
                    Button(
                        onClick = onTakeAll,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier.height(40.dp),
                    ) {
                        Icon(
                            Icons.Rounded.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.home_period_take_all_btn),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // ── 展开时才显示分隔线 + 药品列表 ──────────────────────────────
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                // ── 药品列表 ──────────────────────────────────────────
                items.forEachIndexed { idx, item ->
                    var visible by remember(item.medication.id) { mutableStateOf(false) }
                    LaunchedEffect(item.medication.id) {
                        delay(idx * 30L)   // 组内相邻延迟，避免全局累积延迟
                        visible = true
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                                slideInVertically(motionScheme.defaultSpatialSpec()) { it / 3 },
                    ) {
                        Column {
                            MedicationCard(
                                item = item,
                                onToggleTaken = { onToggleTaken(item) },
                                onSkip = { onSkip(item) },
                                onClick = { onClick(item.medication.id) },
                                modifier = Modifier,
                                // 卡片内不需要外圆角（已在 ElevatedCard 内）
                                flatStyle = true,
                            )
                            if (idx < items.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
            } // Column
        } // AnimatedVisibility
    } // ElevatedCard
} // TimePeriodGroupCard

// ── 低库存警告 banner ─────────────────────────────────────────────────────────

@Composable
private fun LowStockBanner(
    medications: List<Pair<String, Pair<Double, String>>>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Medication,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_low_stock_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                medications.forEach { (name, stockPair) ->
                    val (stock, unit) = stockPair
                    Text(
                        text = stringResource(R.string.home_low_stock_item, name, stock.toString(), unit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

// ── 连续打卡 badge ────────────────────────────────────────────────────────────

@Composable
private fun StreakBadgeRow(currentStreak: Int, longestStreak: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SuggestionChip(
            onClick = {},
            label = {
                Text(
                    pluralStringResource(R.plurals.home_streak_current, currentStreak, currentStreak),
                    style = MaterialTheme.typography.labelMedium,
                )
            },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        )
        if (longestStreak > currentStreak) {
            SuggestionChip(
                onClick = {},
                label = {
                    Text(
                        pluralStringResource(R.plurals.home_streak_longest, longestStreak, longestStreak),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
}

// ── "下一服"智能提示 Chip ───────────────────────────────────────────────────

@Composable
private fun NextUpChip(period: TimePeriod, time: String) {
    SuggestionChip(
        onClick = {},
        icon = {
            Icon(
                period.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        label = {
            Text(
                stringResource(R.string.home_next_up, stringResource(period.labelRes), time),
                style = MaterialTheme.typography.labelMedium,
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

// ── PRN 按需用药卡片 ──────────────────────────────────────────────────────────

/**
 * 专为 [Medication.isPRN] == true 的药品设计的卡片区域。
 * 不显示"跳过"选项；用"今日已服 N 次"替代进度显示。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PRNSectionCard(
    items: List<MedicationWithStatus>,
    onToggleTaken: (MedicationWithStatus) -> Unit,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        // 头部
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Rounded.Healing,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_prn_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    stringResource(R.string.home_prn_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )

        // 药品列表
        items.forEachIndexed { idx, item ->
            var visible by remember(item.medication.id) { mutableStateOf(false) }
            LaunchedEffect(item.medication.id) {
                delay(idx * 30L)
                visible = true
            }
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                        slideInVertically(motionScheme.defaultSpatialSpec()) { it / 3 },
            ) {
                Column {
                    ListItem(
                        headlineContent = {
                            Text(item.medication.name, fontWeight = FontWeight.Medium)
                        },
                        supportingContent = {
                            val maxDose = item.medication.maxDailyDose
                            if (maxDose != null) {
                                Text(
                                    if (item.isTaken) stringResource(R.string.home_prn_taken_with_max, maxDose.toString(), item.medication.doseUnit)
                                    else stringResource(R.string.home_prn_max_dose, maxDose.toString(), item.medication.doseUnit),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (item.isTaken) MaterialTheme.colorScheme.tertiary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else if (item.isTaken) {
                                Text(
                                    stringResource(R.string.home_prn_taken_once),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        },
                        leadingContent = {
                            Icon(
                                if (item.medication.isTcm) Icons.Rounded.LocalFlorist
                                else Icons.Rounded.Medication,
                                contentDescription = null,
                                tint = if (item.isTaken) MaterialTheme.colorScheme.outline
                                       else MaterialTheme.colorScheme.secondary,
                            )
                        },
                        trailingContent = {
                            FilledTonalButton(
                                onClick = { onToggleTaken(item) },
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (item.isTaken)
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    else
                                        MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = if (item.isTaken)
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            ) {
                                Icon(
                                    if (item.isTaken) Icons.Rounded.CheckCircle else Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (item.isTaken) stringResource(R.string.home_prn_btn_taken) else stringResource(R.string.home_prn_btn_take),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        },
                        modifier = Modifier.clickable { onClick(item.medication.id) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    if (idx < items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

// ── 进度卡片（弹性动画进度条）────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedProgressCard(taken: Int, total: Int, modifier: Modifier = Modifier) {
    val motionScheme = MaterialTheme.motionScheme
    val progress by animateFloatAsState(
        targetValue = if (total == 0) 0f else taken.toFloat() / total,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "progress",
    )
    val allDone = total > 0 && taken == total
    val containerColor by animateColorAsState(
        targetValue = if (allDone)
            MaterialTheme.colorScheme.tertiaryContainer
        else
            MaterialTheme.colorScheme.primaryContainer,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "progressBg",
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (total == 0) stringResource(R.string.home_progress_no_plan) else stringResource(R.string.home_progress_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (total > 0) {
                    // 数字滚动动画：taken 变化时上滑出、下滑入
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedContent(
                            targetState = taken,
                            transitionSpec = {
                                (slideInVertically(spring(stiffness = 500f)) { -it / 2 } + fadeIn(tween(160))) togetherWith
                                    (slideOutVertically(tween(120)) { it / 2 } + fadeOut(tween(100)))
                            },
                            label = "takenNum",
                        ) { t ->
                            Text(
                                text = "$t",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (allDone) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = " / $total",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (allDone) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (total > 0) {
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (allDone) stringResource(R.string.home_progress_all_done)
                    else pluralStringResource(R.plurals.home_progress_remaining, total - taken, total - taken),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// ── 空状态组件 ────────────────────────────────────────────────────────────────

@Composable
private fun EmptyMedicationState(onAddMedication: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Rounded.Medication,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = onAddMedication) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.home_empty_add_btn))
        }
    }
}

@Composable
private fun todayDateString(): String {
    val pattern = stringResource(R.string.date_format_day_label)
    return remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()).format(Date()) }
}

// ── 药品相互作用横幅 ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InteractionBannerCard(
    interactions: List<DrugInteraction>,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }

    val highCount = interactions.count { it.severity == InteractionSeverity.HIGH }
    val bannerColor = when {
        highCount > 0 -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = when {
        highCount > 0 -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showSheet = true },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bannerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (highCount > 0) pluralStringResource(R.plurals.home_interaction_high_risk, highCount, highCount) else pluralStringResource(R.plurals.home_interaction_normal, interactions.size, interactions.size),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                Text(
                    text = stringResource(R.string.home_interaction_view_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
            Text(
                text = "${interactions.size}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        }
    }

    if (showSheet) {
        InteractionDetailSheet(
            interactions = interactions,
            onDismiss = { showSheet = false },
        )
    }
}

// ── 相互作用详情 BottomSheet ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InteractionDetailSheet(
    interactions: List<DrugInteraction>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.home_interaction_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.home_close))
                }
            }
            Text(
                stringResource(R.string.home_interaction_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            HorizontalDivider()
            interactions.forEach { interaction ->
                InteractionItem(interaction)
            }
        }
    }
}

@Composable
private fun InteractionItem(interaction: DrugInteraction) {
    val severityHighLabel = stringResource(R.string.home_severity_high)
    val severityModerateLabel = stringResource(R.string.home_severity_moderate)
    val severityLowLabel = stringResource(R.string.home_severity_low)
    val (bgColor, labelColor, severityLabel) = when (interaction.severity) {
        InteractionSeverity.HIGH -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
            severityHighLabel,
        )
        InteractionSeverity.MODERATE -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.secondary,
            severityModerateLabel,
        )
        InteractionSeverity.LOW -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.tertiary,
            severityLowLabel,
        )
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            severityLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                )
                Text(
                    "${interaction.drugA}  ×  ${interaction.drugB}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                interaction.description,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.home_severity_advice, interaction.advice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
// ── 今日用药 QR 码分享对话框 ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationQrDialog(
    items: List<MedicationWithStatus>,
    onDismiss: () -> Unit,
    generateExportUri: () -> String?,
    onQrScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    val shareIntentTitle = stringResource(R.string.home_share_intent_title)
    val shareChooserTitle = stringResource(R.string.home_share_chooser)
    val takenCount = remember(items) { items.count { it.isTaken } }
    val totalCount = items.size

    var selectedTab by remember { mutableIntStateOf(0) }
    var showScanner by remember { mutableStateOf(false) }

    // Pre-compute period label strings at composition scope
    val periodStrings: Map<String, String> = com.example.medlog.data.model.TimePeriod.entries.associate { tp ->
        tp.key to stringResource(tp.labelRes)
    }

    // ── Tab 0：今日打卡文本 ────────────────────────────────────────────────────
    val todayQrText = remember(items, periodStrings) {
        buildString {
            appendLine("Anshin ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())} [$takenCount/$totalCount]")
            items.forEach { item ->
                val status = when {
                    item.isTaken   -> "✓"
                    item.isSkipped -> "-"
                    else           -> "○"
                }
                val med = item.medication
                val dose = if (med.doseQuantity == med.doseQuantity.toLong().toDouble())
                    "${med.doseQuantity.toLong()}${med.doseUnit}"
                else "%.1f${med.doseUnit}".format(med.doseQuantity)
                val period = periodStrings[med.timePeriod] ?: ""
                appendLine("$status ${med.name} $dose $period")
            }
        }.trimEnd()
    }

    // ── Tab 1：计划导出 URI ────────────────────────────────────────────────────
    val exportUri = remember(selectedTab) {
        if (selectedTab == 1) generateExportUri() else null
    }

    // QR 位图（两个 tab 各自用对应文本）
    val activeQrText = if (selectedTab == 0) todayQrText else (exportUri ?: "")
    val canShowQr = exportUri != null &&
        com.example.medlog.domain.PlanExportCodec.canDisplayAsQr(exportUri)

    val todayQrBitmap by produceState<android.graphics.Bitmap?>(null, todayQrText) {
        value = withContext(Dispatchers.Default) { generateQrBitmap(todayQrText) }
    }
    val exportQrBitmap by produceState<android.graphics.Bitmap?>(null, exportUri, canShowQr) {
        if (canShowQr && exportUri != null)
            value = withContext(Dispatchers.Default) { generateQrBitmap(exportUri) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_qr_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Tab 切换 ────────────────────────────────────────
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.qr_tab_today)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.qr_tab_export)) },
                    )
                }

                // ── Tab 0 内容 ───────────────────────────────────────
                if (selectedTab == 0) {
                    Text(
                        stringResource(R.string.home_qr_taken_count, takenCount, totalCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    QrImageBox(bitmap = todayQrBitmap)
                    Text(
                        stringResource(R.string.home_qr_instruction),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                // ── Tab 1 内容 ───────────────────────────────────────
                if (selectedTab == 1) {
                    if (exportUri == null) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    } else if (canShowQr) {
                        QrImageBox(bitmap = exportQrBitmap)
                        Text(
                            stringResource(R.string.home_qr_instruction),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Text(
                            stringResource(R.string.qr_export_too_large),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // ── 扫码导入按钮（两个 tab 均可用）───────────────────
                OutlinedButton(
                    onClick = { showScanner = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.qr_scan_import))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val shareText = if (selectedTab == 1 && exportUri != null) exportUri else todayQrText
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    putExtra(Intent.EXTRA_TITLE, shareIntentTitle)
                }
                context.startActivity(Intent.createChooser(intent, shareChooserTitle))
            }) {
                Icon(Icons.Rounded.IosShare, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.home_qr_share_btn))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_close)) }
        },
    )

    // ── 扫描器全屏覆盖层 ───────────────────────────────────────────────────────
    if (showScanner) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showScanner = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
            ),
        ) {
            com.example.medlog.ui.qr.QrScannerPage(
                onResult = { raw ->
                    showScanner = false
                    onQrScanned(raw)
                    onDismiss()
                },
                onBack = { showScanner = false },
            )
        }
    }
}

// ── QR 图像盒子（两个 tab 共用）──────────────────────────────────────────────

@Composable
private fun QrImageBox(bitmap: android.graphics.Bitmap?) {
    Box(
        modifier = Modifier
            .size(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                painter = BitmapPainter(bitmap.asImageBitmap()),
                contentDescription = stringResource(R.string.home_qr_cd),
                modifier = Modifier.size(200.dp),
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(40.dp))
        }
    }
}

// ── 导入预览对话框 ──────────────────────────────────────────────────────────

@Composable
private fun ImportPreviewDialog(
    plan: com.example.medlog.domain.PlanExport,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qr_import_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.qr_import_medication_count, plan.meds.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(plan.meds) { med ->
                        Text(
                            "• ${med.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(
                    onClick = { onMerge(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.qr_import_mode_merge))
                }
                OutlinedButton(
                    onClick = { onReplace(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.qr_import_mode_replace))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_close)) }
        },
    )
}