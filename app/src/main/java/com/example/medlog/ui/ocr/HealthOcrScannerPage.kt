package com.example.medlog.ui.ocr

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AirlineStops
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.medlog.R
import com.example.medlog.data.model.ExtractedNumber
import com.example.medlog.data.model.HealthType
import com.example.medlog.data.model.OcrParseResult
import com.example.medlog.data.model.ParsedHealthMetric
import com.example.medlog.ui.components.AnimatedListItem
import com.example.medlog.ui.components.CameraPermissionGate
import com.example.medlog.ui.components.ProcessingOverlay

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
    val motionScheme = MaterialTheme.motionScheme
    val state by viewModel.uiState.collectAsState()
    val view = LocalView.current

    // 包装回调以添加触觉反馈
    val onMetricSelectedWithHaptic: (ParsedHealthMetric) -> Unit = { metric ->
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
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
                        (fadeIn(motionScheme.defaultEffectsSpec()) +
                            slideInVertically(motionScheme.defaultEffectsSpec()) { it / 8 })
                            .togetherWith(
                                fadeOut(motionScheme.fastEffectsSpec()) +
                                    slideOutVertically(motionScheme.fastEffectsSpec()) { -it / 8 },
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
                                onCaptureRequested = { viewModel.onCaptureRequested() },
                                onCapture = { imageProxy -> viewModel.onImageCaptured(imageProxy) },
                            )
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp, start = 24.dp, end = 24.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                tonalElevation = 2.dp,
                            ) {
                                Text(
                                    text = stringResource(R.string.ocr_health_scan_hint),
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            ProcessingOverlay(
                                visible = state.isProcessing,
                                text = processingText,
                            )
                        }
                    } else {
                        HealthMetricResultList(
                            result = state.parseResult,
                            suggestedType = suggestedType,
                            onSelect = onMetricSelectedWithHaptic,
                            onRetry = { viewModel.onRetry() },
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
    suggestedType: HealthType?,
    onSelect: (ParsedHealthMetric) -> Unit,
    onRetry: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    val hasStructured = result.metrics.isNotEmpty()
    val hasCandidates = result.candidates.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
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
            }
        }

        // 预计算血压配对建议（在 LazyColumn 外的 @Composable 作用域中）
        val bpPairs = remember(result.candidates) {
            if (hasCandidates) HealthMetricParser.findPotentialBpPairs(result.candidates)
            else emptyList()
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                        Icon(
                                            imageVector = healthMetricIcon(metric.type),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(22.dp),
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
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
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
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
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
            if (result.rawTexts.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.ocr_health_raw_text),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                itemsIndexed(result.rawTexts) { _, text ->
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

        FilledTonalButton(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ocr_retry))
        }
    }
}

// ── 血压配对建议卡片 ─────────────────────────────────────────────────────────

@Composable
private fun BpMergeSuggestionCard(
    systolic: ExtractedNumber,
    diastolic: ExtractedNumber,
    onAccept: () -> Unit,
) {
    ElevatedCard(
        onClick = onAccept,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
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
            Icon(
                Icons.Rounded.Bloodtype,
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
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
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

    ElevatedCard(
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
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitValue() }),
                    singleLine = true,
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                IconButton(onClick = { submitValue() }) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                }
            } else {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { isEditing = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Rounded.Edit,
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
                        Icon(
                            healthMetricIcon(selectedType),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
            if (!isEditing) {
                Icon(
                    Icons.Rounded.ChevronRight,
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

/** 体征类型对应的图标 */
private fun healthMetricIcon(type: HealthType): ImageVector = when (type) {
    HealthType.BLOOD_PRESSURE -> Icons.Rounded.Bloodtype
    HealthType.BLOOD_GLUCOSE  -> Icons.Rounded.WaterDrop
    HealthType.WEIGHT         -> Icons.Rounded.FitnessCenter
    HealthType.HEART_RATE     -> Icons.Rounded.Favorite
    HealthType.TEMPERATURE    -> Icons.Rounded.Thermostat
    HealthType.SPO2           -> Icons.Rounded.AirlineStops
}

/** 格式化体征值（血压 sys/dia，其他值+单位） */
private fun formatMetricValue(metric: ParsedHealthMetric): String {
    return if (metric.type == HealthType.BLOOD_PRESSURE && metric.secondaryValue != null) {
        "${metric.value.toInt()}/${metric.secondaryValue.toInt()} ${metric.type.unit}"
    } else {
        val v = if (metric.value == metric.value.toLong().toDouble()) {
            metric.value.toLong().toString()
        } else {
            metric.value.toString()
        }
        "$v ${metric.type.unit}"
    }
}
