package com.example.medlog.ui.ocr

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AirlineStops
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.medlog.R
import com.example.medlog.data.model.HealthType

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
) {
    val context = LocalContext.current
    val motionScheme = MaterialTheme.motionScheme
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    var parseResult by remember { mutableStateOf(OcrParseResult(emptyList(), emptyList(), emptyList())) }
    var isProcessing by remember { mutableStateOf(false) }
    var showResults by remember { mutableStateOf(false) }

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
            if (hasPermission) {
                AnimatedContent(
                    targetState = showResults,
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
                                isProcessing = isProcessing,
                                onCaptureRequested = { isProcessing = true },
                                onCapture = { imageProxy ->
                                    processImage(imageProxy) { texts ->
                                        parseResult = HealthMetricParser.parseAll(texts)
                                        isProcessing = false
                                        if (texts.isNotEmpty()) {
                                            showResults = true
                                        }
                                    }
                                },
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
                            AnimatedVisibility(
                                visible = isProcessing,
                                enter = fadeIn(motionScheme.defaultEffectsSpec()),
                                exit = fadeOut(motionScheme.fastEffectsSpec()),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
                                        )
                                        .pointerInput(Unit) { /* 消费触摸 */ },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        LoadingIndicator(
                                            modifier = Modifier.size(56.dp),
                                        )
                                        Text(
                                            text = stringResource(R.string.ocr_processing),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        HealthMetricResultList(
                            result = parseResult,
                            suggestedType = suggestedType,
                            onSelect = onMetricSelected,
                            onRetry = {
                                showResults = false
                                parseResult = OcrParseResult(emptyList(), emptyList(), emptyList())
                            },
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(
                        stringResource(R.string.ocr_scan_permission_rationale),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.ocr_scan_grant_permission))
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
            Text(
                text = when {
                    hasStructured -> stringResource(R.string.ocr_health_detected)
                    hasCandidates -> stringResource(R.string.ocr_health_numbers_found)
                    else -> stringResource(R.string.ocr_health_no_metrics)
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
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
                                    Text(
                                        text = formatMetricValue(metric),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
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
                itemsIndexed(result.candidates) { index, number ->
                    val baseDelay = if (hasStructured) result.metrics.size else 0
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

// ── 候选数字卡片 ─────────────────────────────────────────────────────────────

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

    val inferredType = suggestedType
        ?: if (number.pairedValue != null) HealthType.BLOOD_PRESSURE
        else HealthType.entries.firstOrNull { HealthMetricParser.isValuePlausible(number.value, it) }

    ElevatedCard(
        onClick = {
            val type = inferredType ?: HealthType.BLOOD_PRESSURE
            onSelect(
                ParsedHealthMetric(
                    type = type,
                    value = number.value,
                    secondaryValue = number.pairedValue,
                    rawText = number.rawText,
                ),
            )
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = displayValue,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (inferredType != null) {
                AssistChip(
                    onClick = { /* 类型标签仅做展示 */ },
                    label = { Text(stringResource(inferredType.labelRes)) },
                    leadingIcon = {
                        Icon(
                            healthMetricIcon(inferredType),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── 列表项入场动画 ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedListItem(
    index: Int,
    motionScheme: MotionScheme,
    content: @Composable () -> Unit,
) {
    val animatedAlpha = remember { Animatable(0f) }
    val animatedOffset = remember { Animatable(24f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 60L)
        animatedAlpha.animateTo(1f, animationSpec = motionScheme.defaultEffectsSpec())
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 60L)
        animatedOffset.animateTo(0f, animationSpec = motionScheme.defaultEffectsSpec())
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = animatedAlpha.value
            translationY = animatedOffset.value
        },
    ) {
        content()
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
