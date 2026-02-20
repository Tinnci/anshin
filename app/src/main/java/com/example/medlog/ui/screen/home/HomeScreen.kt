package com.example.medlog.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Medication
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

    // Pending items for "take all" button
    val pendingItems = uiState.items.filter { !it.isTaken && !it.isSkipped }

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
                actions = {},
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

            // ── 药品卡片列表（按分类分组）──────────────────────
            var globalIndex = 0
            uiState.groupedItems.forEach { (category, groupItems) ->
                // 有分类名时显示 sticky 分组标题
                if (category.isNotBlank()) {
                    item(key = "header_$category", contentType = "header") {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                }
                itemsIndexed(
                    groupItems,
                    key = { _, it -> it.medication.id },
                ) { _, item ->
                    val index = globalIndex++
                    val motionScheme = MaterialTheme.motionScheme
                    var visible by remember(item.medication.id) { mutableStateOf(false) }
                    LaunchedEffect(item.medication.id) {
                        delay(index * 40L)
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
                                val wasTaken = item.isTaken
                                viewModel.toggleMedicationStatus(item)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = if (wasTaken)
                                            "${item.medication.name} 已重置为待服"
                                        else
                                            "${item.medication.name} 已标记为已服",
                                        actionLabel = "撤销",
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoByMedicationId(item.medication.id)
                                    }
                                }
                            },
                            onSkip = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.skipMedication(item)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "${item.medication.name} 已跳过今日",
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

            // ── 底部间距（FAB 避让）──────────────────────────
            item { Spacer(Modifier.height(80.dp)) }
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
                    Text(
                        "$taken / $total",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (allDone) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
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
