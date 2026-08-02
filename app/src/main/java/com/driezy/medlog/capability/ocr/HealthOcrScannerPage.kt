package com.driezy.medlog.capability.ocr

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.data.model.ExtractedNumber
import com.driezy.medlog.data.model.HealthType
import com.driezy.medlog.data.model.OcrParseResult
import com.driezy.medlog.data.model.ParsedHealthMetric
import com.driezy.medlog.feature.health.application.AiExecutionStatus
import com.driezy.medlog.feature.health.application.AiFallbackReason
import com.driezy.medlog.ui.components.AiInteractionStatusPill
import com.driezy.medlog.ui.components.AnimatedListItem
import com.driezy.medlog.ui.components.CameraGuidancePill
import com.driezy.medlog.ui.components.CameraPermissionGate
import com.driezy.medlog.ui.components.CameraReadinessPanel
import com.driezy.medlog.ui.components.ProcessingOverlay
import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.driezy.medlog.ui.theme.emphasizedTypography
import com.driezy.medlog.ui.util.labelRes
import com.driezy.medlog.ui.utils.performConfirmHapticFeedback

private const val HEALTH_OCR_FRAME_WIDTH = 0.86f
private const val HEALTH_OCR_FRAME_ASPECT = 2.15f

/**
 * 体征数据 OCR 扫描页面：拍照后自动解析血压/心率/血糖等体征指标。
 *
 * @param onMetricSelected 用户选中某条解析出的体征后回调
 * @param onBack           返回按钮回调
 * @param suggestedType    当前草稿中已选类型，用于候选数字自动推断
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HealthOcrScannerPage(
    onMetricSelected: (ParsedHealthMetric) -> Unit,
    onBack: () -> Unit,
    suggestedType: HealthType? = null,
    viewModel: HealthOcrViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HealthOcrScannerContent(
        state = state,
        suggestedType = suggestedType,
        onMetricSelected = onMetricSelected,
        onBack = onBack,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HealthOcrScannerContent(
    state: HealthOcrUiState,
    suggestedType: HealthType?,
    onMetricSelected: (ParsedHealthMetric) -> Unit,
    onBack: () -> Unit,
    onAction: (HealthOcrUiAction) -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    val view = LocalView.current

    // 包装回调以添加触觉反馈
    val onMetricSelectedWithHaptic: (ParsedHealthMetric) -> Unit = { metric ->
        view.performConfirmHapticFeedback()
        onMetricSelected(metric)
    }

    // 处理阶段文案
    val processingText = when (state.processingStage) {
        1 -> stringResource(R.string.ocr_processing_recognizing)
        2 -> stringResource(R.string.ocr_processing_parsing)
        else -> stringResource(R.string.ocr_processing)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ocr_health_scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        MedLogIcon(MedLogIcons.ArrowBack, contentDescription = stringResource(R.string.common_back_cd))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            CameraPermissionGate {
                AnimatedContent(
                    targetState = state.showResults,
                    transitionSpec = {
                        (
                            fadeIn(motionScheme.defaultEffectsSpec()) +
                                slideInVertically(motionScheme.defaultSpatialSpec()) { it / 8 }
                            )
                            .togetherWith(
                                fadeOut(motionScheme.fastEffectsSpec()) +
                                    slideOutVertically(motionScheme.fastSpatialSpec()) { -it / 8 },
                            )
                    },
                    label = "health_ocr_content",
                ) { resultsVisible ->
                    if (!resultsVisible) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            OcrCameraPreview(
                                modifier = Modifier.fillMaxSize(),
                                isProcessing = state.isProcessing,
                                frameWidthFraction = HEALTH_OCR_FRAME_WIDTH,
                                frameAspectRatio = HEALTH_OCR_FRAME_ASPECT,
                                onCaptureRequested = { onAction(HealthOcrUiAction.CaptureRequested) },
                                onCapture = { imageProxy, region ->
                                    onAction(HealthOcrUiAction.ImageCaptured(imageProxy, region))
                                },
                            )
                            CameraGuidancePill(
                                text = stringResource(R.string.ocr_health_scan_hint),
                                icon = MedLogIcons.DocumentScanner,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp, start = 24.dp, end = 24.dp),
                            )
                            CameraReadinessPanel(
                                fillFrameText = stringResource(R.string.ocr_guidance_fill_frame),
                                holdSteadyText = stringResource(R.string.ocr_guidance_hold_steady),
                                avoidGlareText = stringResource(R.string.ocr_guidance_avoid_glare),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(
                                        start = MedLogSpacing.Large,
                                        end = MedLogSpacing.Large,
                                        bottom = 132.dp,
                                    ),
                            )
                            ProcessingOverlay(
                                visible = state.isProcessing,
                                text = processingText,
                            )
                        }
                    } else {
                        HealthMetricResultList(
                            result = state.parseResult,
                            recognitionOutput = state.recognitionOutput,
                            suggestedType = suggestedType,
                            canRunCloudAnalysis = state.canRunCloudAnalysis,
                            isCloudAnalyzing = state.isCloudAnalyzing,
                            cloudAnalysisFailed = state.cloudAnalysisFailed,
                            cloudAnalysisStatus = state.cloudAnalysisStatus,
                            onSelect = onMetricSelectedWithHaptic,
                            onCloudAnalyze = { onAction(HealthOcrUiAction.CloudAnalysisRequested) },
                            onRetry = { onAction(HealthOcrUiAction.Retry) },
                        )
                    }
                }
            }
        }
    }
}

// ── 体征识别结果列表（三层：结构化匹配 → 候选数字 → 原始文本） ──────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HealthMetricResultList(
    result: OcrParseResult,
    recognitionOutput: OcrRecognitionOutput,
    suggestedType: HealthType?,
    canRunCloudAnalysis: Boolean,
    isCloudAnalyzing: Boolean,
    cloudAnalysisFailed: Boolean,
    cloudAnalysisStatus: AiExecutionStatus,
    onSelect: (ParsedHealthMetric) -> Unit,
    onCloudAnalyze: () -> Unit,
    onRetry: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    val hasStructured = result.metrics.isNotEmpty()
    val hasCandidates = result.candidates.isNotEmpty()
    val cloudActionPresentation = HealthOcrCloudActionPresentation.from(
        canRunCloudAnalysis = canRunCloudAnalysis,
        isCloudAnalyzing = isCloudAnalyzing,
        cloudAnalysisFailed = cloudAnalysisFailed,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = MedLogSpacing.Large, vertical = MedLogSpacing.Large)) {
                Text(
                    text = when {
                        hasStructured -> stringResource(R.string.ocr_health_detected)
                        hasCandidates -> stringResource(R.string.ocr_health_numbers_found)
                        else -> stringResource(R.string.ocr_health_no_metrics)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!hasStructured && !hasCandidates) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.ocr_health_no_metrics_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedVisibility(visible = cloudActionPresentation.showPanel) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MedLogSpacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Small),
                    ) {
                        if (cloudAnalysisFailed) {
                            Text(
                                text = stringResource(cloudAnalysisStatus.cloudAnalysisMessageRes()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Tiny),
                        ) {
                            FilledTonalButton(
                                onClick = onCloudAnalyze,
                                enabled = canRunCloudAnalysis && !isCloudAnalyzing,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (isCloudAnalyzing) {
                                    LoadingIndicator(modifier = Modifier.size(18.dp))
                                } else {
                                    MedLogIcon(
                                        MedLogIcons.CloudUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(Modifier.width(MedLogSpacing.Small))
                                Text(
                                    text = if (isCloudAnalyzing) {
                                        stringResource(R.string.ocr_cloud_analysis_running)
                                    } else {
                                        stringResource(R.string.ocr_cloud_analysis_action)
                                    },
                                )
                            }
                            AiInteractionStatusPill(
                                status = cloudAnalysisStatus,
                                isRunning = isCloudAnalyzing,
                                modifier = Modifier.align(Alignment.Start),
                            )
                        }
                    }
                }
            }
        }

        // 预计算血压配对建议（在 LazyColumn 外的 @Composable 作用域中）
        val bpPairs = remember(result.candidates) {
            if (hasCandidates) {
                HealthMetricParser.findPotentialBpPairs(result.candidates)
            } else {
                emptyList()
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = MedLogSpacing.Large, vertical = MedLogSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MedLogSpacing.Medium),
        ) {
            // ── 第一层：结构化匹配的体征指标 ──
            if (hasStructured) {
                item {
                    Text(
                        text = stringResource(R.string.ocr_health_tap_to_record),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                itemsIndexed(result.metrics) { index, metric ->
                    AnimatedListItem(index, motionScheme) {
                        ListItem(
                            onClick = { onSelect(metric) },
                            modifier = Modifier.fillMaxWidth(),
                            shapes = ListItemDefaults.shapes(),
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(
                                    alpha = 0.6f,
                                ),
                            ),
                            leadingContent = {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        MedLogIcon(
                                            icon = healthMetricIcon(metric.type),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            },
                            supportingContent = {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = formatMetricValue(metric),
                                            style = MaterialTheme.emphasizedTypography.headlineSmall,
                                        )
                                        ConfidenceBadge(metric.confidence)
                                    }
                                    Text(
                                        text = metric.rawText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            trailingContent = {
                                MedLogIcon(MedLogIcons.ChevronRight, contentDescription = null)
                            },
                        ) {
                            Text(
                                text = stringResource(metric.type.labelRes),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }

            // ── 第二层：候选数字 ──
            if (hasCandidates) {
                item {
                    Spacer(Modifier.height(if (hasStructured) 8.dp else 0.dp))
                    Text(
                        text = stringResource(R.string.ocr_health_candidate_numbers),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                // 智能血压配对建议
                if (bpPairs.isNotEmpty()) {
                    val bestPair = bpPairs.first()
                    val sys = result.candidates[bestPair.first]
                    val dia = result.candidates[bestPair.second]
                    item {
                        val baseDelay = if (hasStructured) result.metrics.size else 0
                        AnimatedListItem(baseDelay, motionScheme) {
                            BpMergeSuggestionCard(
                                systolic = sys,
                                diastolic = dia,
                                onAccept = {
                                    onSelect(
                                        ParsedHealthMetric(
                                            type = HealthType.BLOOD_PRESSURE,
                                            value = sys.value,
                                            secondaryValue = dia.value,
                                            rawText = "${sys.rawText}/${dia.rawText}",
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
                itemsIndexed(result.candidates) { index, number ->
                    val baseDelay = (if (hasStructured) result.metrics.size else 0) +
                        (if (bpPairs.isNotEmpty()) 1 else 0)
                    AnimatedListItem(baseDelay + index, motionScheme) {
                        CandidateNumberCard(
                            number = number,
                            suggestedType = suggestedType,
                            onSelect = onSelect,
                        )
                    }
                }
            }

            // ── 第三层：原始 OCR 文本行 ──
            if (recognitionOutput.groups.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.ocr_health_raw_text),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                recognitionOutput.groups.forEach { group ->
                    item(key = "raw_source_${group.source}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = MedLogSpacing.Small),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(group.source.labelRes()),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = pluralStringResource(
                                    R.plurals.ocr_result_source_count,
                                    group.texts.size,
                                    group.texts.size,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    itemsIndexed(group.texts, key = { index, text -> "${group.source}_$index:$text" }) { _, text ->
                        Surface(
                            onClick = {
                                val type = suggestedType ?: HealthType.BLOOD_PRESSURE
                                onSelect(ParsedHealthMetric(type, 0.0, rawText = text))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        FilledTonalButton(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            MedLogIcon(
                MedLogIcons.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ocr_retry))
        }
    }
}

private fun OcrResultSource.labelRes(): Int = when (this) {
    OcrResultSource.ML_KIT_ORIGINAL -> R.string.ocr_source_mlkit_original
    OcrResultSource.PREPROCESSED_VARIANTS -> R.string.ocr_source_preprocessed
    OcrResultSource.SEVEN_SEGMENT_MODEL -> R.string.ocr_source_seven_segment
    OcrResultSource.LCD_CROP_MODEL -> R.string.ocr_source_lcd_crop
}

// ── 血压配对建议卡片 ─────────────────────────────────────────────────────────

@Composable
private fun BpMergeSuggestionCard(systolic: ExtractedNumber, diastolic: ExtractedNumber, onAccept: () -> Unit) {
    Card(
        onClick = onAccept,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MedLogIcon(
                MedLogIcons.Bloodtype,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ocr_bp_merge_suggestion),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "${systolic.value.toInt()}/${diastolic.value.toInt()} mmHg",
                    style = MaterialTheme.emphasizedTypography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            MedLogIcon(
                MedLogIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

// ── 候选数字卡片（支持编辑） ──────────────────────────────────────────────────

@Composable
private fun CandidateNumberCard(
    number: ExtractedNumber,
    suggestedType: HealthType?,
    onSelect: (ParsedHealthMetric) -> Unit,
) {
    val displayValue = if (number.pairedValue != null) {
        "${number.value.toInt()}/${number.pairedValue.toInt()}"
    } else {
        if (number.value == number.value.toLong().toDouble()) {
            number.value.toLong().toString()
        } else {
            number.value.toString()
        }
    }

    val hasDecimal = number.value != number.value.toLong().toDouble()
    val plausibleTypes = remember(number) {
        HealthMetricParser.rankPlausibleTypes(
            value = number.value,
            hasDecimal = hasDecimal,
            isPaired = number.pairedValue != null,
        )
    }

    val initialIndex = if (suggestedType != null) {
        val idx = plausibleTypes.indexOf(suggestedType)
        if (idx >= 0) idx else 0
    } else {
        0
    }
    var selectedIndex by remember(number) { mutableIntStateOf(initialIndex) }
    val selectedType = plausibleTypes.getOrNull(selectedIndex)

    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember(number) {
        mutableStateOf(TextFieldValue(displayValue, TextRange(displayValue.length)))
    }
    val focusRequester = remember { FocusRequester() }

    fun submitValue() {
        val text = editValue.text.trim()
        val type = selectedType ?: HealthType.BLOOD_PRESSURE
        // 尝试解析 sys/dia 格式
        val parts = text.split("/")
        val primary = parts.firstOrNull()?.toDoubleOrNull()
        val secondary = parts.getOrNull(1)?.toDoubleOrNull()
        if (primary != null) {
            onSelect(
                ParsedHealthMetric(
                    type = type,
                    value = primary,
                    secondaryValue = secondary,
                    rawText = text,
                ),
            )
        }
        isEditing = false
    }

    OutlinedCard(
        onClick = {
            if (!isEditing) {
                val type = selectedType ?: HealthType.BLOOD_PRESSURE
                onSelect(
                    ParsedHealthMetric(
                        type = type,
                        value = number.value,
                        secondaryValue = number.pairedValue,
                        rawText = number.rawText,
                    ),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (isEditing) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isEditing) {
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.emphasizedTypography.headlineSmall,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitValue() }),
                    singleLine = true,
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                IconButton(onClick = { submitValue() }) {
                    MedLogIcon(MedLogIcons.Check, contentDescription = stringResource(R.string.common_confirm_cd))
                }
            } else {
                Text(
                    text = displayValue,
                    style = MaterialTheme.emphasizedTypography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { isEditing = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    MedLogIcon(
                        MedLogIcons.Edit,
                        contentDescription = stringResource(R.string.ocr_edit_value),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selectedType != null) {
                AssistChip(
                    onClick = {
                        if (plausibleTypes.size > 1) {
                            selectedIndex = (selectedIndex + 1) % plausibleTypes.size
                        }
                    },
                    label = { Text(stringResource(selectedType.labelRes)) },
                    leadingIcon = {
                        MedLogIcon(
                            healthMetricIcon(selectedType),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
            if (!isEditing) {
                MedLogIcon(
                    MedLogIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── 置信度标签 ───────────────────────────────────────────────────────────────

@Composable
private fun ConfidenceBadge(confidence: Float) {
    if (confidence <= 0f) return
    val (label, color) = when {
        confidence >= 0.85f -> stringResource(R.string.ocr_confidence_high) to MaterialTheme.colorScheme.primary
        confidence >= 0.65f -> stringResource(R.string.ocr_confidence_medium) to MaterialTheme.colorScheme.tertiary
        else -> stringResource(R.string.ocr_confidence_low) to MaterialTheme.colorScheme.error
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

// ── 工具函数 ─────────────────────────────────────────────────────────────────

internal fun AiExecutionStatus.cloudAnalysisMessageRes(): Int = when (reason) {
    AiFallbackReason.API_KEY_MISSING -> R.string.ocr_cloud_analysis_needs_key
    AiFallbackReason.WIFI_REQUIRED -> R.string.ocr_cloud_analysis_wifi_required
    AiFallbackReason.IMAGE_INPUT_UNSUPPORTED -> R.string.ocr_cloud_analysis_image_unsupported
    AiFallbackReason.OPENAI_COMPATIBLE_BASE_URL_MISSING -> R.string.ocr_cloud_analysis_base_url_missing
    AiFallbackReason.CLOUD_AI_DISABLED,
    AiFallbackReason.FEATURE_DISABLED,
    -> R.string.ocr_cloud_analysis_disabled
    AiFallbackReason.PROVIDER_ERROR -> R.string.ocr_cloud_analysis_provider_error
    AiFallbackReason.RESPONSE_FORMAT_INVALID -> R.string.ocr_cloud_analysis_format_error
    AiFallbackReason.NO_HEALTH_CONTEXT,
    AiFallbackReason.UNKNOWN_ERROR,
    AiFallbackReason.NONE,
    -> R.string.ocr_cloud_analysis_failed
}

internal enum class HealthOcrCloudStatusPlacement {
    BELOW_PRIMARY_ACTION,
}

internal data class HealthOcrCloudActionPresentation(
    val showPanel: Boolean,
    val statusPlacement: HealthOcrCloudStatusPlacement = HealthOcrCloudStatusPlacement.BELOW_PRIMARY_ACTION,
) {
    companion object {
        fun from(
            canRunCloudAnalysis: Boolean,
            isCloudAnalyzing: Boolean,
            cloudAnalysisFailed: Boolean,
        ): HealthOcrCloudActionPresentation = HealthOcrCloudActionPresentation(
            showPanel = canRunCloudAnalysis || isCloudAnalyzing || cloudAnalysisFailed,
        )
    }
}

/** 体征类型对应的图标 */
private fun healthMetricIcon(type: HealthType): Int = when (type) {
    HealthType.BLOOD_PRESSURE -> MedLogIcons.Bloodtype
    HealthType.BLOOD_GLUCOSE -> MedLogIcons.WaterDrop
    HealthType.WEIGHT -> MedLogIcons.FitnessCenter
    HealthType.BODY_FAT -> MedLogIcons.MonitorWeight
    HealthType.HEART_RATE -> MedLogIcons.Favorite
    HealthType.TEMPERATURE -> MedLogIcons.Thermostat
    HealthType.SPO2 -> MedLogIcons.AirlineStops
}

/** 格式化体征值（血压 sys/dia，其他值+单位） */
private fun formatMetricValue(metric: ParsedHealthMetric): String =
    if (metric.type == HealthType.BLOOD_PRESSURE && metric.secondaryValue != null) {
        "${metric.value.toInt()}/${metric.secondaryValue.toInt()} ${metric.type.unit}"
    } else {
        val v = if (metric.value == metric.value.toLong().toDouble()) {
            metric.value.toLong().toString()
        } else {
            metric.value.toString()
        }
        "$v ${metric.type.unit}"
    }
