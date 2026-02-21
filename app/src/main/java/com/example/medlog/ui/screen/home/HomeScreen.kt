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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medlog.data.model.DrugInteraction
import com.example.medlog.data.model.InteractionSeverity
import com.example.medlog.ui.components.MedicationCard
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

    // Pending items for "take all" button (excluding PRN on-demand meds)
    val pendingItems = uiState.items.filter { !it.isTaken && !it.isSkipped && !it.medication.isPRN }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("今日用药", fontWeight = FontWeight.Bold)
                        Text(
                            todayDateString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleGroupBy) {
                        Icon(
                            imageVector = if (uiState.groupByTime) Icons.Rounded.Category else Icons.Rounded.AccessTime,
                            contentDescription = if (uiState.groupByTime) "切换为分类分组" else "切换为时间分组",
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
                text = { Text("添加药品") },
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                        medications = lowStockItems.map { it.medication.name to (it.medication.stock!! to it.medication.doseUnit) },
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
                                    message = "已全部标记为已服",
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
                            "一键服用全部 (${pendingItems.size})",
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
                        TimePeriodGroupCard(
                            timePeriod = timePeriod,
                            items = groupItems,
                            onToggleTaken = { item ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (item.isSkipped) {
                                    viewModel.undoByMedicationId(item.medication.id)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "${item.medication.name} 已撤销跳过，恢复待服",
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                } else {
                                    val wasTaken = item.isTaken
                                    viewModel.toggleMedicationStatus(item)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = if (wasTaken) "${item.medication.name} 已重置为待服"
                                            else "${item.medication.name} 已标记为已服",
                                            actionLabel = "撤销",
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
                                        "${item.medication.name} 已跳过今日",
                                        actionLabel = "撤销",
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
                                        "「${timePeriod.label}」已全部标记为已服",
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
                                                "${item.medication.name} 已撤销跳过，恢复待服",
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                    } else {
                                        val wasTaken = item.isTaken
                                        viewModel.toggleMedicationStatus(item)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = if (wasTaken) "${item.medication.name} 已重置为待服"
                                                else "${item.medication.name} 已标记为已服",
                                                actionLabel = "撤销",
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
                                            "${item.medication.name} 已跳过今日",
                                            actionLabel = "撤销",
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
                                    message = if (wasTaken) "${item.medication.name} 已撤销"
                                              else "${item.medication.name} 已记录服用",
                                    actionLabel = "撤销",
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
) {
    val pendingCount = items.count { !it.isTaken && !it.isSkipped }
    val allDone = pendingCount == 0
    val motionScheme = MaterialTheme.motionScheme

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
                    text = timePeriod.label,
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
            // 待服数量 Badge（allDone 时显示 ✓）
            if (allDone) {
                Icon(
                    Icons.Rounded.DoneAll,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) { Text("$pendingCount") }
                // 一键服用本时段 — pill 形，比单药按钮更大更显眼
                if (pendingCount > 1) {
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
                            "全部服用",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

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
    }
}

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
                    text = "库存不足提醒",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                medications.forEach { (name, stockPair) ->
                    val (stock, unit) = stockPair
                    Text(
                        text = "· $name：剩余 $stock $unit",
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
                    "🔥 连续 $currentStreak 天",
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
                        "最长 $longestStreak 天",
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
                "下一服 · ${period.label}  $time",
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
                    "按需用药",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    "以下药品无固定时间，需要时点击记录服用",
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
                                    if (item.isTaken) "今日已服 · 日最大剂量 $maxDose ${item.medication.doseUnit}"
                                    else "日最大剂量 ${maxDose} ${item.medication.doseUnit}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (item.isTaken) MaterialTheme.colorScheme.tertiary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else if (item.isTaken) {
                                Text(
                                    "今日已记录服用",
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
                                    if (item.isTaken) "已服" else "服用",
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
                    if (total == 0) "今日暂无用药计划" else "今日进度",
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
                    if (allDone) "全部完成！今日用药计划已完成 🎉"
                    else "还剩 ${total - taken} 种药品待服用",
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
            "今日尚无用药计划",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "点击下方按钮添加您的第一个药品",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = onAddMedication) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("添加第一个药品")
        }
    }
}

private fun todayDateString(): String =
    SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date())

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
                    text = if (highCount > 0) "⚠️ 发现 $highCount 处高风险配伍" else "发现 ${interactions.size} 处用药配伍提醒",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                Text(
                    text = "点击查看详情和建议",
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
                    "用药相互作用",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, "关闭")
                }
            }
            Text(
                "以下为基于 ATC 分类的配伍提示，仅供参考，请咨询医生或药师。",
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
    val (bgColor, labelColor, severityLabel) = when (interaction.severity) {
        InteractionSeverity.HIGH -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
            "高风险",
        )
        InteractionSeverity.MODERATE -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.secondary,
            "中度",
        )
        InteractionSeverity.LOW -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.tertiary,
            "注意",
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
                "建议：${interaction.advice}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
