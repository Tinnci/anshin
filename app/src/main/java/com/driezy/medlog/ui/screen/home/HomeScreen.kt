package com.driezy.medlog.ui.screen.home

import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

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
import androidx.compose.ui.graphics.Color
import com.driezy.medlog.data.model.TimePeriod
import com.driezy.medlog.ui.util.labelRes
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
import com.driezy.medlog.ui.utils.generateQrBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.driezy.medlog.ui.theme.emphasizedTypography
import com.driezy.medlog.ui.theme.MedLogSpacing
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.data.model.DrugInteraction
import com.driezy.medlog.data.model.InteractionSeverity
import com.driezy.medlog.ui.components.MedLogScreenScaffold
import com.driezy.medlog.ui.components.MedicationCard
import com.driezy.medlog.ui.components.ScreenChromeState
import com.driezy.medlog.ui.components.ScreenFab
import com.driezy.medlog.ui.components.ScreenOverlay
import com.driezy.medlog.ui.components.ScreenOverlayHost
import com.driezy.medlog.ui.components.TopBarAction
import com.driezy.medlog.ui.components.TopBarActionPriority
import com.driezy.medlog.ui.util.displayName
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/** 列表项交错入场动画的逐项延迟（毫秒） */
internal const val STAGGER_DELAY_MS = 30L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onAddMedication: () -> Unit,
    onMedicationClick: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var overlay by remember { mutableStateOf<ScreenOverlay?>(null) }
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

    // 收集导入成功事件 → Snackbar
    @Suppress("LocalContextResourcesRead") // resources 在组合时捕获，用于 LaunchedEffect 中的 plurals
    val importResources = context.resources
    LaunchedEffect(Unit) {
        viewModel.importSuccess.collect { count ->
            val msg = importResources.getQuantityString(R.plurals.qr_import_success, count, count)
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Pending items for "take all" button (excluding PRN on-demand meds)
    val pendingItems = remember(uiState.items) {
        uiState.items.filter { !it.isTaken && !it.isSkipped && !it.medication.isPRN }
    }

    MedLogScreenScaffold(
        title = {
            Column {
                Text(stringResource(R.string.home_title), style = MaterialTheme.emphasizedTypography.titleLarge)
                Text(
                    todayDateString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = listOf(
            TopBarAction(
                id = "qr",
                label = stringResource(R.string.home_share_qr_cd),
                icon = MedLogIcons.QrCode2,
                priority = TopBarActionPriority.Primary,
                onClick = {
                    overlay = ScreenOverlay.Custom(id = "home:qr") {
                        MedicationQrDialog(
                            items = uiState.items,
                            onDismiss = { overlay = null },
                            generateExportUri = viewModel::generateExportUri,
                            onQrScanned = viewModel::onQrScanned,
                        )
                    }
                },
            ),
            TopBarAction(
                id = "group",
                label = if (uiState.groupByTime) {
                    stringResource(R.string.home_group_toggle_by_category)
                } else {
                    stringResource(R.string.home_group_toggle_by_time)
                },
                icon = if (uiState.groupByTime) MedLogIcons.Category else MedLogIcons.AccessTime,
                priority = TopBarActionPriority.Secondary,
                onClick = viewModel::toggleGroupBy,
            ),
            TopBarAction(
                id = "settings",
                label = stringResource(R.string.settings_action_open),
                icon = MedLogIcons.Settings,
                priority = TopBarActionPriority.Secondary,
                onClick = onOpenSettings,
            ),
        ),
        chromeState = ScreenChromeState(
            isLoading = uiState.isLoading,
            snackbarHostState = snackbarHostState,
            fab = if (uiState.items.isNotEmpty()) {
                ScreenFab(
                    label = stringResource(R.string.home_fab_add),
                    icon = MedLogIcons.Add,
                    onClick = onAddMedication,
                )
            } else {
                null
            },
        ),
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = MedLogSpacing.ScreenContentWithFab,
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {

            // ── 进度卡片 ──────────────────────────────────────
            item {
                AnimatedProgressCard(
                    taken = uiState.takenCount,
                    total = uiState.totalCount,
                    currentStreak = uiState.currentStreak,
                    longestStreak = uiState.longestStreak,
                    nextUp = uiState.nextUpPeriod?.takeIf {
                        uiState.takenCount < uiState.totalCount
                    },
                    pendingCount = pendingItems.size,
                    onTakeAll = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.takeAll()
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = msgAllTaken,
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
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
                        medications = lowStockItems.map { it.medication.displayName() to ((it.medication.stock ?: 0.0) to it.medication.doseUnit) },
                    )
                }
            }
            // ── 药品相互作用警告 ───────────────────────────
            if (uiState.interactions.isNotEmpty()) {
                item {
                    InteractionBannerCard(
                        interactions = uiState.interactions,
                    )
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
                val taskGroups = listOf(
                    "now" to uiState.nowTaskItems,
                    "later" to uiState.laterTaskItems,
                ).filter { (_, groupItems) -> groupItems.isNotEmpty() }
                taskGroups.forEach { (key, groupItems) ->
                    item(key = "task_group_$key", contentType = "taskGroup") {
                        val groupTitle = if (key == "now")
                            stringResource(R.string.home_now_group_title)
                        else
                            stringResource(R.string.home_later_group_title)
                        MedicationTaskGroupCard(
                            title = groupTitle,
                            subtitle = if (key == "now")
                                stringResource(R.string.home_now_group_body)
                            else
                                stringResource(R.string.home_later_group_body),
                            icon = if (key == "now") MedLogIcons.CheckCircle else MedLogIcons.AccessTime,
                            items = groupItems,
                            onToggleTaken = { item ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (item.isSkipped) {
                                    viewModel.undoByMedicationId(item.medication.id)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            fmtUndoSkip.format(item.medication.displayName()),
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                } else {
                                    val wasHandled = item.isTaken || item.isPartial
                                    viewModel.toggleMedicationStatus(item)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = (if (wasHandled) fmtReset else fmtTaken).format(item.medication.displayName()),
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
                                        fmtSkipped.format(item.medication.displayName()),
                                        actionLabel = undoLabel,
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoByMedicationId(item.medication.id)
                                    }
                                }
                            },
                            onPartialTake = { item, qty ->
                                viewModel.markPartialDose(item, qty)
                            },
                            onTakeAll = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                groupItems
                                    .filter { !it.isHandled }
                                    .forEach { viewModel.toggleMedicationStatus(it) }
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        fmtPeriodAllTaken.format(groupTitle),
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            },
                            onClick = onMedicationClick,
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
                                    text = if (category == HomeUiState.UNCATEGORIZED_KEY)
                                        stringResource(R.string.category_other) else category,
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
                            delay(idx * STAGGER_DELAY_MS)   // 基于组内索引，而非全局，避免底部首次出现延迟
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
                                                fmtUndoSkip.format(item.medication.displayName()),
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                    } else {
                                        val wasHandled = item.isTaken || item.isPartial
                                        viewModel.toggleMedicationStatus(item)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = (if (wasHandled) fmtReset else fmtTaken).format(item.medication.displayName()),
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
                                            fmtSkipped.format(item.medication.displayName()),
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
                                onPartialTake = { qty -> viewModel.markPartialDose(item, qty) },
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
                                    message = (if (wasTaken) fmtPrnUndo else fmtPrnTaken).format(item.medication.displayName()),
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
    val importOverlay = when {
        importPreview != null -> ScreenOverlay.Custom(id = "home:import-preview") {
            ImportPreviewDialog(
                plan = importPreview!!,
                onMerge = { viewModel.confirmImport(com.driezy.medlog.domain.ImportMode.MERGE) },
                onReplace = { viewModel.confirmImport(com.driezy.medlog.domain.ImportMode.REPLACE) },
                onDismiss = viewModel::clearImportPreview,
            )
        }
        importError != null -> ScreenOverlay.Confirm(
            id = "home:import-error",
            title = stringResource(R.string.qr_scan_title),
            body = stringResource(R.string.qr_invalid),
            confirmLabel = stringResource(R.string.home_close),
            dismissLabel = stringResource(R.string.home_close),
            onConfirm = viewModel::clearImportPreview,
        )
        else -> null
    }
    ScreenOverlayHost(
        overlay = overlay ?: importOverlay,
        onDismiss = {
            overlay = null
            viewModel.clearImportPreview()
        },
    )
}
