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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScannerPage(
    onResult: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
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
                if (!showResults) {
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
                    // 提示文案
                    Text(
                        text = stringResource(R.string.ocr_scan_hint),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .padding(horizontal = 32.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                        )
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

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        // 拍照按钮
        FloatingActionButton(
            onClick = {
                if (!isProcessing) {
                    onCaptureRequested()  // 在 Main 线程设置 isProcessing = true
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
                .padding(bottom = 32.dp)
                .size(72.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(
                Icons.Rounded.CameraAlt,
                contentDescription = stringResource(R.string.ocr_capture),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

// ── 识别结果列表 ─────────────────────────────────────────────────────────────

@Composable
private fun OcrResultList(
    texts: List<String>,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.ocr_result_hint),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(texts) { text ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(text) },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        // 重新拍照按钮
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
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
