package com.example.medlog.ui.ocr

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.rounded.CameraAlt
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.medlog.R
import com.example.medlog.data.model.HealthType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.Executors

private const val TAG = "OcrScannerPage"

/**
 * OCR 扫描页面：使用 CameraX 拍照 + ML Kit 中文文字识别。
 *
 * 用户拍照后显示识别到的文本块列表，点击即选中并返回。
 *
 * @param onResult 用户选中某条文字后回调
 * @param onBack   返回按钮回调
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OcrScannerPage(
    onResult: (String) -> Unit,
    onBack: () -> Unit,
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

    // OCR 状态
    var recognizedTexts by remember { mutableStateOf<List<String>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var showResults by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ocr_scan_title)) },
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
                    label = "ocr_content",
                ) { resultsVisible ->
                    if (!resultsVisible) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            // 相机预览 + 拍照按钮
                            OcrCameraPreview(
                                modifier = Modifier.fillMaxSize(),
                                isProcessing = isProcessing,
                                onCaptureRequested = { isProcessing = true },
                                onCapture = { imageProxy ->
                                    processImage(imageProxy) { texts ->
                                        recognizedTexts = texts
                                        isProcessing = false
                                        if (texts.isNotEmpty()) {
                                            showResults = true
                                        }
                                    }
                                },
                            )
                            // 提示文案（半透明背景增强可读性）
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp, start = 24.dp, end = 24.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                tonalElevation = 2.dp,
                            ) {
                                Text(
                                    text = stringResource(R.string.ocr_scan_hint),
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            // 处理中遮罩 + 波浪指示器
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
                                        .pointerInput(Unit) { /* 消费触摸，阻止穿透 */ },
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
                        // 识别结果列表
                        OcrResultList(
                            texts = recognizedTexts,
                            onSelect = { text -> onResult(text.trim()) },
                            onRetry = {
                                showResults = false
                                recognizedTexts = emptyList()
                            },
                        )
                    }
                }
            } else {
                // 无相机权限提示
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

// ── 相机预览 + 拍照 ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun OcrCameraPreview(
    modifier: Modifier = Modifier,
    isProcessing: Boolean,
    onCaptureRequested: () -> Unit,
    onCapture: (ImageProxy) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        runCatching {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
        }.onFailure { Log.e(TAG, "Camera bind failed", it) }
    }

    // 按下缩放动画
    val motionScheme = MaterialTheme.motionScheme
    val captureScale = remember { Animatable(1f) }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        // 拍照按钮 — LargeFloatingActionButton
        LargeFloatingActionButton(
            onClick = {
                if (!isProcessing) {
                    onCaptureRequested()
                    imageCapture.takePicture(
                        executor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                onCapture(image)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e(TAG, "Capture failed", exception)
                            }
                        },
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            // 处理中显示小波浪进度，否则显示相机图标
            AnimatedContent(
                targetState = isProcessing,
                transitionSpec = {
                    (scaleIn(motionScheme.fastEffectsSpec()) + fadeIn(motionScheme.fastEffectsSpec()))
                        .togetherWith(
                            scaleOut(motionScheme.fastEffectsSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
                        )
                },
                label = "capture_icon",
            ) { processing ->
                if (processing) {
                    LoadingIndicator(
                        modifier = Modifier.size(36.dp),
                    )
                } else {
                    Icon(
                        Icons.Rounded.CameraAlt,
                        contentDescription = stringResource(R.string.ocr_capture),
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
    }
}

// ── 识别结果列表 ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OcrResultList(
    texts: List<String>,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme

    Column(modifier = Modifier.fillMaxSize()) {
        // 标题区域
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp,
        ) {
            Text(
                text = stringResource(R.string.ocr_result_hint),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(texts) { index, text ->
                // 交错入场动画
                val animatedAlpha = remember { Animatable(0f) }
                val animatedOffset = remember { Animatable(24f) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(index * 50L)
                    animatedAlpha.animateTo(
                        1f,
                        animationSpec = motionScheme.defaultEffectsSpec(),
                    )
                }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(index * 50L)
                    animatedOffset.animateTo(
                        0f,
                        animationSpec = motionScheme.defaultEffectsSpec(),
                    )
                }

                ElevatedCard(
                    onClick = { onSelect(text) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = animatedAlpha.value
                            translationY = animatedOffset.value
                        },
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = 1.dp,
                    ),
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        leadingContent = {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }

        // 重新拍照按钮 — FilledTonalButton + 图标
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

// ── 体征 OCR 扫描页面 ───────────────────────────────────────────────────────

/**
 * 体征数据 OCR 扫描页面：拍照后自动解析血压/心率/血糖等体征指标。
 *
 * @param onMetricSelected 用户选中某条解析出的体征后回调
 * @param onBack           返回按钮回调
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HealthOcrScannerPage(
    onMetricSelected: (ParsedHealthMetric) -> Unit,
    onBack: () -> Unit,
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

    // OCR + 解析状态
    var recognizedTexts by remember { mutableStateOf<List<String>>(emptyList()) }
    var parsedMetrics by remember { mutableStateOf<List<ParsedHealthMetric>>(emptyList()) }
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
                                        recognizedTexts = texts
                                        parsedMetrics = HealthMetricParser.parse(texts)
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
                            metrics = parsedMetrics,
                            rawTexts = recognizedTexts,
                            onSelect = onMetricSelected,
                            onRetry = {
                                showResults = false
                                recognizedTexts = emptyList()
                                parsedMetrics = emptyList()
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

// ── 体征识别结果列表 ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HealthMetricResultList(
    metrics: List<ParsedHealthMetric>,
    rawTexts: List<String>,
    onSelect: (ParsedHealthMetric) -> Unit,
    onRetry: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme

    Column(modifier = Modifier.fillMaxSize()) {
        // 标题区域
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp,
        ) {
            Text(
                text = if (metrics.isNotEmpty()) stringResource(R.string.ocr_health_detected)
                else stringResource(R.string.ocr_health_no_metrics),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 解析出的体征指标卡片
            if (metrics.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.ocr_health_tap_to_record),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                itemsIndexed(metrics) { index, metric ->
                    val animatedAlpha = remember { Animatable(0f) }
                    val animatedOffset = remember { Animatable(24f) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(index * 80L)
                        animatedAlpha.animateTo(
                            1f,
                            animationSpec = motionScheme.defaultEffectsSpec(),
                        )
                    }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(index * 80L)
                        animatedOffset.animateTo(
                            0f,
                            animationSpec = motionScheme.defaultEffectsSpec(),
                        )
                    }

                    ListItem(
                        onClick = { onSelect(metric) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = animatedAlpha.value
                                translationY = animatedOffset.value
                            },
                        shapes = ListItemDefaults.shapes(),
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
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
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                            )
                        },
                    ) {
                        Text(
                            text = stringResource(metric.type.labelRes),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }

            // 原始文字区域（可折叠参考）
            if (rawTexts.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.ocr_health_raw_text),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                itemsIndexed(rawTexts) { index, text ->
                    Surface(
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

/** 体征类型对应的图标（与 HealthScreen 保持一致） */
@Composable
private fun healthMetricIcon(type: HealthType) = when (type) {
    HealthType.BLOOD_PRESSURE -> Icons.Rounded.Bloodtype
    HealthType.BLOOD_GLUCOSE  -> Icons.Rounded.WaterDrop
    HealthType.WEIGHT         -> Icons.Rounded.FitnessCenter
    HealthType.HEART_RATE     -> Icons.Rounded.Favorite
    HealthType.TEMPERATURE    -> Icons.Rounded.Thermostat
    HealthType.SPO2           -> Icons.Rounded.AirlineStops
}

/** 格式化体征值（血压显示 sys/dia，其他显示值+单位） */
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

// ── ML Kit 文字识别处理 ──────────────────────────────────────────────────────

private val mainHandler = Handler(Looper.getMainLooper())

@SuppressLint("UnsafeOptInUsageError")
private fun processImage(
    imageProxy: ImageProxy,
    onResult: (List<String>) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        mainHandler.post { onResult(emptyList()) }
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    // 使用中文识别器（同时支持拉丁字母）
    val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    recognizer.process(inputImage)
        .addOnSuccessListener { visionText ->
            // 提取所有文本块（按行合并），过滤空白
            val lines = visionText.textBlocks
                .flatMap { block -> block.lines.map { it.text.trim() } }
                .filter { it.isNotBlank() }
            onResult(lines)
        }
        .addOnFailureListener { e ->
            Log.e(TAG, "OCR failed", e)
            onResult(emptyList())
        }
        .addOnCompleteListener {
            imageProxy.close()
            recognizer.close()
        }
}
