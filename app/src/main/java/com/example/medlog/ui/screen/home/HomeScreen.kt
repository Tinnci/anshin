package com.example.medlog.ui.screen.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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
                actions = {
                    if (pendingItems.size > 1) {
                        FilledTonalIconButton(
                            onClick = {
                                scope.launch {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.takeAll()
                                    snackbarHostState.showSnackbar(
                                        message = "已全部标记为已服",
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Icon(Icons.Rounded.DoneAll, contentDescription = "全部标记已服")
                        }
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

            // ── 空状态 ────────────────────────────────────────
            if (uiState.items.isEmpty()) {
                item {
                    EmptyMedicationState(onAddMedication = onAddMedication)
                }
            }

            // ── 药品卡片列表 ──────────────────────────────────
            items(uiState.items, key = { it.medication.id }) { item ->
                MedicationCard(
                    item = item,
                    onToggleTaken = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleMedicationStatus(item)
                        scope.launch {
                            val wasTaken = item.isTaken
                            snackbarHostState.showSnackbar(
                                message = if (wasTaken)
                                    "${item.medication.name} 已重置为待服"
                                else
                                    "${item.medication.name} 已标记为已服",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                    onSkip = { viewModel.skipMedication(item) },
                    onClick = { onMedicationClick(item.medication.id) },
                    modifier = Modifier.animateItem(),
                )
            }

            // ── 底部间距（FAB 避让）──────────────────────────
            item { Spacer(Modifier.height(80.dp)) }
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

    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
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
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
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
