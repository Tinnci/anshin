package com.driezy.medlog.feature.medications.home

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.ui.components.MedLogScreenScaffold
import com.driezy.medlog.ui.components.MedicationCard
import com.driezy.medlog.ui.components.MedicationMessageCard
import com.driezy.medlog.ui.components.RefreshWhileVisible
import com.driezy.medlog.ui.components.ScreenChromeState
import com.driezy.medlog.ui.components.ScreenFab
import com.driezy.medlog.ui.components.ScreenOverlay
import com.driezy.medlog.ui.components.ScreenOverlayHost
import com.driezy.medlog.ui.components.ScreenTopBarSize
import com.driezy.medlog.ui.components.TopBarAction
import com.driezy.medlog.ui.components.TopBarActionPriority
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.theme.emphasizedTypography
import com.driezy.medlog.ui.util.displayName
import com.driezy.medlog.ui.utils.MedLogHapticEffect
import com.driezy.medlog.ui.utils.rememberMedLogHaptics
import kotlinx.coroutines.delay
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
    val context = LocalContext.current
    RefreshWhileVisible { viewModel.onAction(HomeUiAction.RefreshTime) }

    @Suppress("LocalContextResourcesRead") // resources 在组合时捕获，用于 LaunchedEffect 中的 plurals
    val importResources = context.resources
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeUiEffect.DoseSaved -> {
                    val message = importResources.getString(
                        if (effect.change.after ==
                            null
                        ) {
                            R.string.dose_record_removed
                        } else {
                            R.string.dose_record_saved
                        },
                    )
                    val result = snackbarHostState.showSnackbar(
                        message,
                        actionLabel = importResources.getString(R.string.home_snackbar_undo),
                    )
                    if (result ==
                        SnackbarResult.ActionPerformed
                    ) {
                        viewModel.onAction(HomeUiAction.RestoreDose(effect.change))
                    }
                }
                is HomeUiEffect.Failed -> snackbarHostState.showSnackbar(
                    importResources.getString(R.string.dose_record_failed),
                )
                is HomeUiEffect.ImportSucceeded -> {
                    val message = importResources.getQuantityString(
                        R.plurals.qr_import_success,
                        effect.count,
                        effect.count,
                    )
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }
    HomeContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        onAddMedication = onAddMedication,
        onMedicationClick = onMedicationClick,
        onOpenSettings = onOpenSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeContent(
    uiState: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (HomeUiAction) -> Unit,
    onAddMedication: () -> Unit,
    onMedicationClick: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val performHaptic = rememberMedLogHaptics()
    var overlay by remember { mutableStateOf<ScreenOverlay?>(null) }
    fun toggleDose(item: MedicationWithStatus) {
        if (item.doseKey in uiState.savingDoses) return
        performHaptic(MedLogHapticEffect.CONFIRM)
        onAction(HomeUiAction.ToggleDose(item))
    }

    fun skipDose(item: MedicationWithStatus) {
        if (item.doseKey in uiState.savingDoses) return
        performHaptic(MedLogHapticEffect.CONFIRM)
        onAction(HomeUiAction.SkipDose(item))
    }

    fun togglePrnDose(item: MedicationWithStatus) = toggleDose(item)

    val lowStockItems = remember(uiState.items) {
        uiState.items.distinctBy { it.medication.id }.filter { item ->
            val stock = item.medication.stock ?: return@filter false
            val threshold = item.medication.refillThreshold ?: return@filter false
            stock <= threshold
        }
    }

    MedLogScreenScaffold(
        topBarSize = ScreenTopBarSize.Compact,
        title = {
            Column {
                Text(stringResource(R.string.home_title), style = MaterialTheme.emphasizedTypography.titleLarge)
                Text(
                    todayDateString(uiState.today),
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
            ),
            TopBarAction(
                id = "settings",
                label = stringResource(R.string.settings_action_open),
                icon = MedLogIcons.Settings,
                priority = TopBarActionPriority.Secondary,
            ),
        ),
        chromeState = ScreenChromeState(
            isLoading = uiState.isLoading,
            fab = ScreenFab(
                id = "add",
                label = stringResource(R.string.home_fab_add),
                icon = MedLogIcons.Add,
            ),
        ),
        snackbarHostState = snackbarHostState,
        onChromeAction = { id ->
            when (id) {
                "add" -> onAddMedication()
                "qr" -> {
                    overlay = ScreenOverlay.Custom(id = "home:qr") {
                        MedicationQrDialog(
                            items = uiState.items,
                            onDismiss = { overlay = null },
                            generateExportUri = { uiState.exportUri },
                            onQrScanned = {
                                performHaptic(MedLogHapticEffect.CONFIRM)
                                onAction(HomeUiAction.QrScanned(it))
                            },
                        )
                    }
                }
                "group" -> {
                    performHaptic(MedLogHapticEffect.TOGGLE)
                    onAction(HomeUiAction.ToggleGrouping)
                }
                "settings" -> onOpenSettings()
            }
        },
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = MedLogSpacing.ScreenContentDefault,
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
        ) {
            if (uiState.savingDoses.isNotEmpty()) {
                item(key = "saving") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            if (uiState.errorMessage != null) {
                item(key = "loadError") {
                    MedicationMessageCard(
                        stringResource(R.string.dose_load_failed),
                        isError = true,
                        onRetry = { onAction(HomeUiAction.RefreshTime) },
                    )
                }
            }
            item(key = "homeHero", contentType = "homeHero") {
                HomeHero(
                    presentation = uiState.heroPresentation,
                    style = uiState.homeHeroStyle,
                    currentStreak = uiState.currentStreak,
                    onTakeNext = ::toggleDose,
                    onSkipNext = ::skipDose,
                    onViewDetails = { onMedicationClick(it.medication.id) },
                    onAddMedication = onAddMedication,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── 低库存警告 banner ──────────────────────────────
            if (lowStockItems.isNotEmpty()) {
                item {
                    LowStockBanner(
                        medications = lowStockItems.map {
                            it.medication.displayName() to
                                ((it.medication.stock ?: 0.0) to it.medication.doseUnit)
                        },
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
            if (uiState.heroPresentation.totalCount > 0) {
                item(key = "todayPlanHeader", contentType = "header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MedLogSpacing.Medium, bottom = MedLogSpacing.Tiny),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.home_hero_plan_title),
                            style = MaterialTheme.emphasizedTypography.titleLarge,
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.home_hero_plan_count,
                                uiState.heroPresentation.totalCount,
                                uiState.heroPresentation.totalCount,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── 药品卡片列表（可按时段或分类分组）────────────────
            val focusedPlanItem = uiState.heroPresentation.nextPendingItem
            if (uiState.groupByTime) {
                val taskGroups = listOf(
                    "now" to uiState.nowTaskItems,
                    "later" to uiState.laterTaskItems,
                ).map { (key, groupItems) ->
                    key to groupItems.filterNot { it.doseKey == focusedPlanItem?.doseKey }
                }.filter { (_, groupItems) -> groupItems.isNotEmpty() }
                taskGroups.forEach { (key, groupItems) ->
                    item(key = "task_group_$key", contentType = "taskGroup") {
                        val groupTitle = if (key == "now") {
                            stringResource(R.string.home_now_group_title)
                        } else {
                            stringResource(R.string.home_later_group_title)
                        }
                        MedicationTaskGroupCard(
                            title = groupTitle,
                            subtitle = if (key == "now") {
                                stringResource(R.string.home_now_group_body)
                            } else {
                                stringResource(R.string.home_later_group_body)
                            },
                            icon = if (key == "now") MedLogIcons.CheckCircle else MedLogIcons.AccessTime,
                            items = groupItems,
                            onToggleTaken = ::toggleDose,
                            onSkip = ::skipDose,
                            onPartialTake = { item, qty ->
                                performHaptic(MedLogHapticEffect.CONFIRM)
                                onAction(HomeUiAction.MarkPartial(item, qty))
                            },
                            onTakeAll = {
                                performHaptic(MedLogHapticEffect.CONFIRM)
                                groupItems
                                    .filter { !it.isHandled }
                                    .forEach { onAction(HomeUiAction.ToggleDose(it)) }
                            },
                            onClick = onMedicationClick,
                            modifier = Modifier.animateItem(),
                            autoCollapse = uiState.autoCollapseCompletedGroups,
                        )
                    }
                }
            } else {
                // 分类分组：扁平卡片列表
                uiState.groupedItems.forEach { (category, groupItems) ->
                    val visibleItems = groupItems.filterNot { it.doseKey == focusedPlanItem?.doseKey }
                    if (category.isNotBlank() && visibleItems.isNotEmpty()) {
                        item(key = "header_$category", contentType = "header") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 2.dp),
                            ) {
                                Text(
                                    text = if (category == HomeUiState.UNCATEGORIZED_KEY) {
                                        stringResource(R.string.category_other)
                                    } else {
                                        category
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    itemsIndexed(
                        visibleItems,
                        key = { _, it -> it.doseKey.listKey },
                    ) { idx, item ->
                        val motionScheme = MaterialTheme.motionScheme
                        val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
                        var visible by remember(item.doseKey) { mutableStateOf(false) }
                        LaunchedEffect(item.doseKey, animationsEnabled) {
                            if (animationsEnabled) {
                                delay(idx * STAGGER_DELAY_MS) // 基于组内索引，而非全局，避免底部首次出现延迟
                            }
                            visible = true
                        }
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                                slideInVertically(motionScheme.defaultSpatialSpec()) { it / 4 },
                        ) {
                            MedicationCard(
                                item = item,
                                onToggleTaken = { toggleDose(item) },
                                onSkip = { skipDose(item) },
                                onClick = { onMedicationClick(item.medication.id) },
                                modifier = Modifier.animateItem(),
                                onPartialTake = {
                                    performHaptic(MedLogHapticEffect.CONFIRM)
                                    onAction(HomeUiAction.MarkPartial(item, it))
                                },
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
                        onToggleTaken = ::togglePrnDose,
                        onClick = onMedicationClick,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
    val importOverlay = when {
        uiState.importPreview != null -> ScreenOverlay.Custom(id = "home:import-preview") {
            ImportPreviewDialog(
                plan = requireNotNull(uiState.importPreview),
                onMerge = {
                    onAction(
                        HomeUiAction.ConfirmImport(com.driezy.medlog.feature.medications.application.ImportMode.MERGE),
                    )
                },
                onReplace = {
                    onAction(
                        HomeUiAction.ConfirmImport(
                            com.driezy.medlog.feature.medications.application.ImportMode.REPLACE,
                        ),
                    )
                },
                onDismiss = { onAction(HomeUiAction.ClearImportPreview) },
            )
        }
        uiState.importError != null -> ScreenOverlay.Confirm(
            id = "home:import-error",
            title = stringResource(R.string.qr_scan_title),
            body = stringResource(R.string.qr_invalid),
            confirmLabel = stringResource(R.string.home_close),
            dismissLabel = stringResource(R.string.home_close),
        )
        else -> null
    }
    ScreenOverlayHost(
        overlay = overlay ?: importOverlay,
        onConfirm = { descriptor, _ ->
            if (descriptor.id == "home:import-error") onAction(HomeUiAction.ClearImportPreview)
        },
        onDismiss = {
            overlay = null
            onAction(HomeUiAction.ClearImportPreview)
        },
    )
}
