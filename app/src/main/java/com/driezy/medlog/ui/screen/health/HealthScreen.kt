package com.driezy.medlog.ui.screen.health

import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import com.driezy.medlog.ui.theme.emphasizedTypography
import com.driezy.medlog.ui.theme.MedLogSpacing
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.driezy.medlog.R
import com.driezy.medlog.data.model.HealthRecord
import com.driezy.medlog.data.model.HealthType
import com.driezy.medlog.domain.health.HealthInsight
import com.driezy.medlog.domain.health.HealthInsightSeverity
import com.driezy.medlog.domain.health.AiExecutionStatus
import com.driezy.medlog.ui.components.AiInteractionStatusPill
import com.driezy.medlog.ui.components.MedLogScreenScaffold
import com.driezy.medlog.ui.components.ScreenChromeState
import com.driezy.medlog.ui.components.ScreenFab
import com.driezy.medlog.ui.components.ScreenOverlay
import com.driezy.medlog.ui.components.ScreenOverlayHost
import com.driezy.medlog.ui.components.TopBarAction
import com.driezy.medlog.ui.components.TopBarActionPriority
import com.driezy.medlog.ui.ocr.HealthOcrScannerPage
import java.text.SimpleDateFormat
import java.util.*

// ─── 类型图标映射 ────────────────────────────────────────────────────────────

@Composable
internal fun healthTypeIcon(type: HealthType) = when (type) {
    HealthType.BLOOD_PRESSURE -> MedLogIcons.Bloodtype
    HealthType.BLOOD_GLUCOSE  -> MedLogIcons.WaterDrop
    HealthType.WEIGHT         -> MedLogIcons.FitnessCenter
    HealthType.BODY_FAT       -> MedLogIcons.MonitorWeight
    HealthType.HEART_RATE     -> MedLogIcons.Favorite
    HealthType.TEMPERATURE    -> MedLogIcons.Thermostat
    HealthType.SPO2           -> MedLogIcons.AirlineStops
}

internal fun HealthType.formatMetricValue(value: Double, secondaryValue: Double?): String = when (this) {
    HealthType.BLOOD_PRESSURE -> if (secondaryValue != null) {
        "${value.toInt()}/${secondaryValue.toInt()}"
    } else {
        "${value.toInt()}"
    }
    HealthType.TEMPERATURE,
    HealthType.BLOOD_GLUCOSE,
    HealthType.WEIGHT,
    HealthType.BODY_FAT,
    -> "%.1f".format(value)
    else -> "${value.toInt()}"
}

internal fun HealthRecord.userVisibleNotes(): String {
    val trimmed = notes.trim()
    return if (trimmed.startsWith("seed:", ignoreCase = true)) "" else trimmed
}

@OptIn(ExperimentalMaterial3Api::class)
internal val HealthRecordSheetEnabledStates = setOf(
    SheetValue.Hidden,
    SheetValue.PartiallyExpanded,
    SheetValue.Expanded,
)

internal data class HealthInsightsPresentation(
    val showPendingBody: Boolean,
    val pendingBodyRes: Int = R.string.health_insights_pending_body,
) {
    companion object {
        fun from(
            insightCount: Int,
            isRefreshing: Boolean,
        ): HealthInsightsPresentation =
            HealthInsightsPresentation(showPendingBody = isRefreshing && insightCount == 0)
    }
}

// ─── 主屏幕 ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HealthScreen(
    onOpenSettings: () -> Unit,
    viewModel: HealthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var overlay by remember { mutableStateOf<ScreenOverlay?>(null) }
    fun showOcrScanner() {
        overlay = ScreenOverlay.FullScreen(
            id = "health:ocr",
            dismissOnClickOutside = false,
        ) {
            HealthOcrScannerPage(
                onMetricSelected = { metric ->
                    overlay = null
                    viewModel.applyOcrMetric(metric)
                },
                onBack = { overlay = null },
                suggestedType = uiState.draft.type,
            )
        }
    }

    MedLogScreenScaffold(
        title = { Text(stringResource(R.string.health_screen_title)) },
        actions = listOf(
            TopBarAction(
                id = "manual-record",
                label = stringResource(R.string.health_screen_fab_cd),
                icon = MedLogIcons.Add,
                priority = TopBarActionPriority.Secondary,
                onClick = viewModel::startAdd,
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
            fab = ScreenFab(
                label = stringResource(R.string.health_ocr_hero_scan),
                icon = MedLogIcons.DocumentScanner,
                onClick = ::showOcrScanner,
            ),
        ),
    ) { innerPadding ->
            LazyColumn(
                contentPadding = MedLogSpacing.ScreenContentWithFab,
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                    // ── OCR 主入口 Hero ─────────────────────────────────────
                    item(key = "ocr_hero") {
                        HealthOcrHeroCard(
                            onScan = ::showOcrScanner,
                            onManualRecord = viewModel::startAdd,
                        )
                    }

                    // ── 类型过滤 Chips ─────────────────────────────────────────
                    item(key = "type_filter") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected   = uiState.selectedType == null,
                                onClick    = { viewModel.selectType(null) },
                                label      = { Text(stringResource(R.string.common_filter_all)) },
                                leadingIcon = if (uiState.selectedType == null) ({
                                    MedLogIcon(MedLogIcons.Check, null, Modifier.size(16.dp))
                                }) else null,
                            )
                            HealthType.entries.forEach { type ->
                                FilterChip(
                                    selected = uiState.selectedType == type,
                                    onClick  = { viewModel.selectType(if (uiState.selectedType == type) null else type) },
                                    label    = { Text(stringResource(type.labelRes)) },
                                    leadingIcon = {
                                        MedLogIcon(healthTypeIcon(type), null, Modifier.size(16.dp))
                                    },
                                )
                            }
                        }
                    }

                    // ── 体征摘要卡片（横向滚动） ───────────────────────────────
                    if (uiState.stats.isNotEmpty()) {
                        item(key = "stats_row") {
                            val visibleStats = if (uiState.selectedType == null) uiState.stats
                                else uiState.stats.filter { it.type == uiState.selectedType }
                            HealthMetricsSection(stats = visibleStats)
                        }
                    }

                    // ── 智能建议：后台聚合，不暴露 prompt / 对话复杂度 ─────────
                    if (
                        uiState.insights.isNotEmpty() ||
                        uiState.isInsightRefreshing
                    ) {
                        item(key = "health_insights") {
                            HealthInsightsSection(
                                insights = uiState.insights,
                                executionStatus = uiState.insightExecutionStatus,
                                isRefreshing = uiState.isInsightRefreshing,
                            )
                        }
                    }

                    // ── BMI 卡片（体重数据 + 有身高时显示） ──────────────────
                    val hasWeightStat = uiState.stats.any { it.type == HealthType.WEIGHT }
                    if (hasWeightStat && (uiState.selectedType == null || uiState.selectedType == HealthType.WEIGHT)) {
                        item(key = "bmi_card") {
                            BmiCard(
                                bmi = uiState.bmi,
                                bmiClassRes = uiState.bmiClassRes,
                                userHeightCm = uiState.userHeightCm,
                                onUpdateHeight = viewModel::updateHeight,
                            )
                        }
                    }

                    // ── 趋势图（选中某类型且有 ≥2 个数据点时显示） ───────────
                    if (uiState.selectedType != null && uiState.chartPoints.size >= 2) {
                        item(key = "trend_chart") {
                            HealthTrendChart(
                                type = uiState.selectedType!!,
                                points = uiState.chartPoints,
                            )
                        }
                    }

                    // ── 记录列表 ───────────────────────────────────────────────
                    if (uiState.records.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    MedLogIcon(
                                        MedLogIcons.MonitorHeartDisplay48,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                    Text(
                                        stringResource(R.string.health_empty_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        item(key = "recent_records_header") {
                            SectionHeader(
                                title = stringResource(R.string.health_recent_records_title),
                                subtitle = stringResource(R.string.health_recent_records_subtitle),
                            )
                        }
                        items(uiState.records, key = { it.id }) { record ->
                            HealthRecordItem(
                                record    = record,
                                onEdit    = { viewModel.startEdit(record) },
                                onDelete  = { viewModel.requestDelete(record) },
                            )
                        }
                    }
            }
    }

    val deleteTarget = uiState.deleteTarget
    val stateOverlay = when {
        uiState.showAddSheet -> ScreenOverlay.Custom(id = "health:record-sheet") {
            AddEditHealthSheet(
                draft = uiState.draft,
                onDismiss = viewModel::dismissSheet,
                onTypeChange = viewModel::onDraftTypeChange,
                onValueChange = viewModel::onDraftValueChange,
                onSecondaryChange = viewModel::onDraftSecondaryChange,
                onNotesChange = viewModel::onDraftNotesChange,
                onTimeChange = viewModel::onDraftTimeChange,
                onOcrScan = ::showOcrScanner,
                voiceInput = uiState.voiceInput,
                onStartVoiceInput = viewModel::startVoiceInput,
                onStopVoiceInput = viewModel::stopVoiceInput,
                onSave = viewModel::saveRecord,
            )
        }
        deleteTarget != null -> ScreenOverlay.Confirm(
            id = "health:delete:${deleteTarget.id}",
            title = stringResource(R.string.health_delete_title),
            body = stringResource(R.string.health_delete_body),
            confirmLabel = stringResource(R.string.common_action_delete),
            dismissLabel = stringResource(R.string.common_action_cancel),
            targetKey = deleteTarget.id.toString(),
            isDanger = true,
            onConfirm = viewModel::confirmDelete,
        )
        else -> null
    }
    ScreenOverlayHost(
        overlay = overlay ?: stateOverlay,
        onDismiss = {
            if (overlay != null) {
                overlay = null
            } else if (uiState.showAddSheet) {
                viewModel.dismissSheet()
            } else if (uiState.deleteTarget != null) {
                viewModel.cancelDelete()
            }
        },
    )
}
