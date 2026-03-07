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
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.medlog.R
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
                                        CircularWavyProgressIndicator(
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
                    CircularWavyProgressIndicator(
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
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 序号标识
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
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
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
