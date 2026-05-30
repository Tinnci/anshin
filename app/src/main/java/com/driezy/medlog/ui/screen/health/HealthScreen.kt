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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.driezy.medlog.ui.ocr.HealthOcrScannerPage
import java.text.SimpleDateFormat
import java.util.*

// ─── 类型图标映射 ────────────────────────────────────────────────────────────

@Composable
private fun healthTypeIcon(type: HealthType) = when (type) {
    HealthType.BLOOD_PRESSURE -> MedLogIcons.Bloodtype
    HealthType.BLOOD_GLUCOSE  -> MedLogIcons.WaterDrop
    HealthType.WEIGHT         -> MedLogIcons.FitnessCenter
    HealthType.BODY_FAT       -> MedLogIcons.MonitorWeight
    HealthType.HEART_RATE     -> MedLogIcons.Favorite
    HealthType.TEMPERATURE    -> MedLogIcons.Thermostat
    HealthType.SPO2           -> MedLogIcons.AirlineStops
}

private fun HealthType.formatMetricValue(value: Double, secondaryValue: Double?): String = when (this) {
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
    viewModel: HealthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showOcrScanner by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.health_screen_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallFloatingActionButton(
                    onClick = viewModel::startAdd,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    MedLogIcon(MedLogIcons.Add, contentDescription = stringResource(R.string.health_screen_fab_cd))
                }
                ExtendedFloatingActionButton(
                    onClick = { showOcrScanner = true },
                    icon = { MedLogIcon(MedLogIcons.DocumentScanner, contentDescription = null) },
                    text = { Text(stringResource(R.string.health_ocr_hero_scan)) },
                )
            }
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = MedLogSpacing.ScreenContentWithFab,
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                    // ── OCR 主入口 Hero ─────────────────────────────────────
                    item(key = "ocr_hero") {
                        HealthOcrHeroCard(
                            onScan = { showOcrScanner = true },
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
    }

    // ── 新增/编辑底部表单 ────────────────────────────────────────────────────
    if (uiState.showAddSheet) {
        AddEditHealthSheet(
            draft           = uiState.draft,
            onDismiss       = viewModel::dismissSheet,
            onTypeChange    = viewModel::onDraftTypeChange,
            onValueChange   = viewModel::onDraftValueChange,
            onSecondaryChange = viewModel::onDraftSecondaryChange,
            onNotesChange   = viewModel::onDraftNotesChange,
            onTimeChange    = viewModel::onDraftTimeChange,
            onOcrScan       = { showOcrScanner = true },
            onSave          = viewModel::saveRecord,
        )
    }

    // ── 删除确认对话框 ────────────────────────────────────────────────────────
    if (uiState.deleteTarget != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.health_delete_title)) },
            text  = { Text(stringResource(R.string.health_delete_body)) },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDelete,
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.common_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.common_action_cancel)) }
            },
        )
    }

    // ── OCR 体征扫描器全屏覆盖层 ─────────────────────────────────────────────
    if (showOcrScanner) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showOcrScanner = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
        ) {
            HealthOcrScannerPage(
                onMetricSelected = { metric ->
                    showOcrScanner = false
                    viewModel.applyOcrMetric(metric)
                },
                onBack = { showOcrScanner = false },
                suggestedType = uiState.draft.type,
            )
        }
    }
}

// ─── 体征摘要卡片 ────────────────────────────────────────────────────────────

@Composable
private fun HealthOcrHeroCard(
    onScan: () -> Unit,
    onManualRecord: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(MedLogSpacing.XLarge),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Large),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    MedLogIcon(
                        MedLogIcons.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(MedLogSpacing.Medium).size(28.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.health_ocr_hero_title),
                        style = MaterialTheme.emphasizedTypography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        stringResource(R.string.health_ocr_hero_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
            ) {
                Button(
                    onClick = onScan,
                    modifier = Modifier.weight(1.5f),
                ) {
                    MedLogIcon(MedLogIcons.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(MedLogSpacing.Small))
                    Text(
                        stringResource(R.string.health_ocr_hero_scan),
                        style = MaterialTheme.emphasizedTypography.labelLarge,
                    )
                }
                FilledTonalButton(
                    onClick = onManualRecord,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.health_ocr_hero_manual))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthMetricsSection(stats: List<HealthTypeStat>) {
    val carouselState = rememberCarouselState { stats.size }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
    ) {
        SectionHeader(
            title = stringResource(R.string.health_metrics_section_title),
            subtitle = stringResource(R.string.health_metrics_section_subtitle),
        )
        HorizontalUncontainedCarousel(
            state = carouselState,
            itemWidth = 168.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp),
            itemSpacing = MedLogSpacing.Small,
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            HealthStatCard(
                stat = stats[it],
                modifier = Modifier
                    .fillMaxHeight()
                    .maskClip(MaterialTheme.shapes.large),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HealthInsightsSection(
    insights: List<HealthInsight>,
    executionStatus: AiExecutionStatus,
    isRefreshing: Boolean,
) {
    val presentation = HealthInsightsPresentation.from(
        insightCount = insights.size,
        isRefreshing = isRefreshing,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
            verticalAlignment = Alignment.Top,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SectionHeader(
                    title = stringResource(R.string.health_insights_section_title),
                    subtitle = stringResource(R.string.health_insights_section_subtitle),
                )
            }
            AiInteractionStatusPill(
                status = executionStatus,
                isRunning = isRefreshing,
            )
        }
        if (presentation.showPendingBody) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier.padding(MedLogSpacing.Medium),
                    horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LoadingIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        text = stringResource(presentation.pendingBodyRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        insights.forEach { insight ->
            HealthInsightCard(insight = insight)
        }
    }
}

@Composable
private fun HealthInsightCard(insight: HealthInsight) {
    val colors = when (insight.severity) {
        HealthInsightSeverity.URGENT -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        HealthInsightSeverity.WARNING -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        HealthInsightSeverity.INFO -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
    val icon = when (insight.severity) {
        HealthInsightSeverity.URGENT,
        HealthInsightSeverity.WARNING,
        -> MedLogIcons.Warning
        HealthInsightSeverity.INFO -> MedLogIcons.AutoAwesome
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = colors,
    ) {
        Row(
            modifier = Modifier.padding(MedLogSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
            verticalAlignment = Alignment.Top,
        ) {
            MedLogIcon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
            ) {
                Text(
                    insight.title,
                    style = MaterialTheme.emphasizedTypography.titleSmall,
                )
                Text(
                    insight.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.82f),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Hairline)) {
        Text(
            title,
            style = MaterialTheme.emphasizedTypography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HealthStatCard(
    stat: HealthTypeStat,
    modifier: Modifier = Modifier,
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd", Locale.getDefault()) }
    val containerColor = if (stat.isAbnormal)
        MaterialTheme.colorScheme.errorContainer
    else
        MaterialTheme.colorScheme.secondaryContainer

    Card(
        modifier = modifier.width(168.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MedLogIcon(
                    healthTypeIcon(stat.type),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (stat.isAbnormal) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(stat.type.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (stat.isAbnormal) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                stat.type.formatMetricValue(stat.latestValue, stat.latestSecondary),
                style = MaterialTheme.emphasizedTypography.headlineSmall,
                color = if (stat.isAbnormal) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                stat.type.unit,
                style = MaterialTheme.typography.labelMedium,
                color = if (stat.isAbnormal)
                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                else
                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
            )

            // ── 血压分类标签 ──────────────────────────────────────
            if (stat.bpClassRes != null) {
                val bpColor = when (stat.bpClassRes) {
                    R.string.health_bp_class_normal -> MaterialTheme.colorScheme.primary
                    R.string.health_bp_class_low -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = bpColor.copy(alpha = 0.15f),
                ) {
                    Text(
                        stringResource(stat.bpClassRes),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = bpColor,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (stat.avg7d != null) {
                    Text(
                        stringResource(R.string.health_7day_avg, "%.1f".format(stat.avg7d)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val trendArrow = when (stat.trend) {
                        1  -> "↑"
                        -1 -> "↓"
                        0  -> "→"
                        else -> ""
                    }
                    if (trendArrow.isNotEmpty()) {
                        Text(
                            trendArrow,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (stat.trend) {
                                1  -> MaterialTheme.colorScheme.error
                                -1 -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                } else {
                    Text(
                        dateFormat.format(Date(stat.latestTime)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── 智能解读文案 ──────────────────────────────────────
            val interpText = buildInterpretation(stat)
            if (interpText != null) {
                Text(
                    interpText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

/** 根据统计数据生成智能解读文案 */
@Composable
private fun buildInterpretation(stat: HealthTypeStat): String? {
    val parts = mutableListOf<String>()
    // 异常判断
    if (stat.isAbnormal) {
        if (stat.latestValue > stat.type.normalMax) {
            parts += stringResource(R.string.health_interp_high)
        } else if (stat.latestValue < stat.type.normalMin) {
            parts += stringResource(R.string.health_interp_low)
        }
    }
    // 趋势
    when (stat.trend) {
        1  -> parts += stringResource(R.string.health_interp_trend_rising)
        -1 -> parts += stringResource(R.string.health_interp_trend_falling)
        0  -> if (!stat.isAbnormal) parts += stringResource(R.string.health_interp_normal)
    }
    return parts.joinToString("；").ifEmpty { null }
}

// ─── 单条记录 ListItem ────────────────────────────────────────────────────────

@Composable
private fun HealthRecordItem(
    record: HealthRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val type = remember(record.type) { HealthType.fromName(record.type) }
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    var showMenu by remember { mutableStateOf(false) }
    val isAbnormal = !type.isNormal(record.value)
    val visibleNotes = remember(record.notes) { record.userVisibleNotes() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isAbnormal) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                             else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        type.formatMetricValue(record.value, record.secondaryValue),
                        style = MaterialTheme.emphasizedTypography.titleLarge,
                    )
                    Spacer(Modifier.width(MedLogSpacing.Tiny))
                    Text(
                        type.unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            },
            overlineContent = {
                Text(stringResource(type.labelRes))
            },
            supportingContent = {
                Column {
                    Text(
                        timeFormat.format(Date(record.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (visibleNotes.isNotBlank()) {
                        Text(
                            visibleNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            leadingContent = {
                MedLogIcon(
                    healthTypeIcon(type),
                    contentDescription = null,
                    tint = if (isAbnormal) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.primary,
                )
            },
            trailingContent = {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        MedLogIcon(MedLogIcons.MoreVert, contentDescription = stringResource(R.string.health_more_ops_cd))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_action_edit)) },
                            onClick = { showMenu = false; onEdit() },
                            leadingIcon = { MedLogIcon(MedLogIcons.Edit, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_action_delete)) },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { MedLogIcon(MedLogIcons.Delete, null) },
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

// ─── BMI 卡片 ────────────────────────────────────────────────────────────────

@Composable
private fun BmiCard(
    bmi: Double?,
    bmiClassRes: Int?,
    userHeightCm: Float,
    onUpdateHeight: (Float) -> Unit,
) {
    var showHeightDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MedLogIcon(MedLogIcons.Monitor, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.health_bmi_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showHeightDialog = true }) {
                    Text(
                        if (userHeightCm > 0f) "${userHeightCm.toInt()} cm" else stringResource(R.string.health_height_label),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (bmi != null && bmiClassRes != null) {
                val classLabel = stringResource(bmiClassRes)
                Text(
                    stringResource(R.string.health_bmi_value, bmi, classLabel),
                    style = MaterialTheme.emphasizedTypography.headlineSmall,
                    color = when (bmiClassRes) {
                        R.string.health_bmi_normal -> MaterialTheme.colorScheme.primary
                        R.string.health_bmi_underweight -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    },
                )
            } else {
                Text(
                    stringResource(R.string.health_bmi_no_height),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showHeightDialog) {
        var heightInput by remember { mutableStateOf(if (userHeightCm > 0f) userHeightCm.toInt().toString() else "") }
        AlertDialog(
            onDismissRequest = { showHeightDialog = false },
            title = { Text(stringResource(R.string.health_height_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = heightInput,
                    onValueChange = { heightInput = it },
                    label = { Text(stringResource(R.string.health_height_dialog_hint)) },
                    suffix = { Text("cm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        heightInput.toFloatOrNull()?.let { onUpdateHeight(it) }
                        showHeightDialog = false
                    },
                    enabled = heightInput.toFloatOrNull()?.let { it in 50f..300f } == true,
                ) { Text(stringResource(R.string.common_action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showHeightDialog = false }) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            },
        )
    }
}

// ─── 趋势折线图 ──────────────────────────────────────────────────────────────

@Composable
private fun HealthTrendChart(
    type: HealthType,
    points: List<HealthRecord>,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val secondaryLineColor = MaterialTheme.colorScheme.tertiary
    val normalBandColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val dateFormat = remember { SimpleDateFormat("M/d", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MedLogIcon(MedLogIcons.TrendingUp, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.health_chart_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            ) {
                val leftPadding = 48.dp.toPx()
                val bottomPadding = 28.dp.toPx()
                val chartWidth = size.width - leftPadding - 8.dp.toPx()
                val chartHeight = size.height - bottomPadding - 8.dp.toPx()

                if (points.isEmpty() || chartWidth <= 0 || chartHeight <= 0) return@Canvas

                val sorted = points.sortedBy { it.timestamp }
                val minTime = sorted.first().timestamp.toFloat()
                val maxTime = sorted.last().timestamp.toFloat()
                val timeRange = (maxTime - minTime).coerceAtLeast(1f)

                // 计算值域（主值 + 正常范围）
                val allValues = sorted.map { it.value }
                val secondaryValues = sorted.mapNotNull { it.secondaryValue }
                val dataMin = (allValues + secondaryValues).min()
                val dataMax = (allValues + secondaryValues).max()
                val rangeMin = minOf(dataMin, type.normalMin).let { it - (it * 0.05).coerceAtLeast(1.0) }
                val rangeMax = maxOf(dataMax, type.normalMax).let { it + (it * 0.05).coerceAtLeast(1.0) }
                val valueRange = (rangeMax - rangeMin).coerceAtLeast(1.0)

                fun xOf(timestamp: Long) = leftPadding + ((timestamp - minTime) / timeRange) * chartWidth
                fun yOf(value: Double) = 8.dp.toPx() + chartHeight * (1 - ((value - rangeMin) / valueRange)).toFloat()

                // 绘制正常范围带
                if (type != HealthType.WEIGHT) {
                    val normalTop = yOf(type.normalMax)
                    val normalBottom = yOf(type.normalMin)
                    drawRect(
                        color = normalBandColor,
                        topLeft = Offset(leftPadding, normalTop),
                        size = androidx.compose.ui.geometry.Size(chartWidth, normalBottom - normalTop),
                    )

                    // 正常范围虚线边界
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    drawLine(gridColor, Offset(leftPadding, normalTop), Offset(leftPadding + chartWidth, normalTop), strokeWidth = 1.dp.toPx(), pathEffect = dashEffect)
                    drawLine(gridColor, Offset(leftPadding, normalBottom), Offset(leftPadding + chartWidth, normalBottom), strokeWidth = 1.dp.toPx(), pathEffect = dashEffect)
                }

                // Y 轴标签
                val textPaint = android.graphics.Paint().apply {
                    color = labelColor.toArgb()
                    textSize = with(density) { 10.sp.toPx() }
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
                for (i in 0..4) {
                    val v = rangeMin + valueRange * i / 4.0
                    val y = yOf(v)
                    drawContext.canvas.nativeCanvas.drawText(
                        if (type == HealthType.TEMPERATURE || type == HealthType.BLOOD_GLUCOSE || type == HealthType.BODY_FAT) "%.1f".format(v) else "${v.toInt()}",
                        leftPadding - 6.dp.toPx(), y + 4.dp.toPx(), textPaint
                    )
                    drawLine(gridColor.copy(alpha = 0.3f), Offset(leftPadding, y), Offset(leftPadding + chartWidth, y), strokeWidth = 0.5.dp.toPx())
                }

                // X 轴日期标签
                val xTextPaint = android.graphics.Paint().apply {
                    color = labelColor.toArgb()
                    textSize = with(density) { 9.sp.toPx() }
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val labelCount = minOf(sorted.size, 5)
                for (i in 0 until labelCount) {
                    val idx = i * (sorted.size - 1) / (labelCount - 1).coerceAtLeast(1)
                    val x = xOf(sorted[idx].timestamp)
                    drawContext.canvas.nativeCanvas.drawText(
                        dateFormat.format(Date(sorted[idx].timestamp)),
                        x, size.height - 4.dp.toPx(), xTextPaint
                    )
                }

                // 绘制主值折线
                val path = Path()
                sorted.forEachIndexed { i, rec ->
                    val x = xOf(rec.timestamp)
                    val y = yOf(rec.value)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx()))

                // 绘制数据点
                sorted.forEach { rec ->
                    drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(xOf(rec.timestamp), yOf(rec.value)))
                }

                // 血压：绘制舒张压折线
                if (type == HealthType.BLOOD_PRESSURE && secondaryValues.isNotEmpty()) {
                    val secPath = Path()
                    sorted.forEachIndexed { i, rec ->
                        val sv = rec.secondaryValue ?: return@forEachIndexed
                        val x = xOf(rec.timestamp)
                        val y = yOf(sv)
                        if (i == 0) secPath.moveTo(x, y) else secPath.lineTo(x, y)
                    }
                    drawPath(secPath, secondaryLineColor, style = Stroke(width = 2.dp.toPx()))
                    sorted.forEach { rec ->
                        rec.secondaryValue?.let {
                            drawCircle(secondaryLineColor, radius = 3.dp.toPx(), center = Offset(xOf(rec.timestamp), yOf(it)))
                        }
                    }
                }
            }
        }
    }
}

// ─── 新增/编辑 ModalBottomSheet ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditHealthSheet(
    draft: HealthDraftState,
    onDismiss: () -> Unit,
    onTypeChange: (HealthType) -> Unit,
    onValueChange: (String) -> Unit,
    onSecondaryChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onTimeChange: (Long) -> Unit,
    onOcrScan: () -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, HealthRecordSheetEnabledStates)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (draft.editingId == null) stringResource(R.string.health_sheet_add_title) else stringResource(R.string.health_sheet_edit_title),
                style = MaterialTheme.emphasizedTypography.titleLarge,
            )

            // ── 记录时间选择器 ─────────────────────────────────────────
            HealthTimePicker(
                timestampMs = draft.timestamp,
                onTimeChange = onTimeChange,
            )

            // ── 类型选择器 ─────────────────────────────────────────────
            Text(stringResource(R.string.health_type_selector_label), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HealthType.entries.forEach { type ->
                    FilterChip(
                        selected    = draft.type == type,
                        onClick     = { onTypeChange(type) },
                        label       = { Text(stringResource(type.labelRes)) },
                        leadingIcon = {
                            MedLogIcon(healthTypeIcon(type), null, Modifier.size(16.dp))
                        },
                    )
                }
            }

            // ── 数值输入 ───────────────────────────────────────────────
            if (draft.type == HealthType.BLOOD_PRESSURE) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = draft.value,
                        onValueChange = onValueChange,
                        label = { Text(stringResource(R.string.health_bp_systolic)) },
                        suffix = { Text("mmHg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = draft.secondaryValue,
                        onValueChange = onSecondaryChange,
                        label = { Text(stringResource(R.string.health_bp_diastolic)) },
                        suffix = { Text("mmHg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                OutlinedTextField(
                    value = draft.value,
                    onValueChange = onValueChange,
                    label = { Text(stringResource(draft.type.labelRes)) },
                    suffix = { Text(draft.type.unit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        val type = draft.type
                        if (type != HealthType.WEIGHT) {
                            Text(stringResource(R.string.health_normal_range, type.normalMin.toString(), type.normalMax.toString(), type.unit))
                        }
                    },
                )
            }

            // ── OCR 拍照填充 ──────────────────────────────────────────
            AssistChip(
                onClick = onOcrScan,
                label = { Text(stringResource(R.string.ocr_health_scan_chip)) },
                leadingIcon = { MedLogIcon(MedLogIcons.CameraAlt, null, Modifier.size(18.dp)) },
            )

            // ── 备注 ──────────────────────────────────────────────────
            OutlinedTextField(
                value = draft.notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.common_notes_hint)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                leadingIcon = { MedLogIcon(MedLogIcons.Notes, null) },
            )

            // ── 操作按钮 ──────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.common_action_cancel)) }
                val isValid = draft.value.toDoubleOrNull() != null &&
                    (draft.type != HealthType.BLOOD_PRESSURE || draft.secondaryValue.toDoubleOrNull() != null)
                Button(
                    onClick  = onSave,
                    enabled  = isValid,
                    modifier = Modifier.weight(2f),
                ) { Text(stringResource(R.string.common_action_save)) }
            }
        }
    }
}

// ─── 时间选择器组件 ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthTimePicker(
    timestampMs: Long,
    onTimeChange: (Long) -> Unit,
) {
    val cal = remember(timestampMs) { Calendar.getInstance().apply { timeInMillis = timestampMs } }
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Text(
        stringResource(R.string.health_record_time),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AssistChip(
            onClick = { showDatePicker = true },
            label = { Text(dateFmt.format(Date(timestampMs))) },
            leadingIcon = { MedLogIcon(MedLogIcons.CalendarMonth, null, Modifier.size(18.dp)) },
            modifier = Modifier.weight(1f),
        )
        AssistChip(
            onClick = { showTimePicker = true },
            label = { Text(timeFmt.format(Date(timestampMs))) },
            leadingIcon = { MedLogIcon(MedLogIcons.Schedule, null, Modifier.size(18.dp)) },
            modifier = Modifier.weight(1f),
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = timestampMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedMs ->
                        val selected = Calendar.getInstance().apply { timeInMillis = selectedMs }
                        val merged = Calendar.getInstance().apply {
                            timeInMillis = timestampMs
                            set(Calendar.YEAR, selected.get(Calendar.YEAR))
                            set(Calendar.MONTH, selected.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, selected.get(Calendar.DAY_OF_MONTH))
                        }
                        onTimeChange(merged.timeInMillis)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }

    if (showTimePicker) {
        val tpState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val merged = Calendar.getInstance().apply {
                        timeInMillis = timestampMs
                        set(Calendar.HOUR_OF_DAY, tpState.hour)
                        set(Calendar.MINUTE, tpState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onTimeChange(merged.timeInMillis)
                    showTimePicker = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            text = { TimePicker(state = tpState) },
            title = null,
        )
    }
}
