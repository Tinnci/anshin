package com.driezy.medlog.ui.ocr

import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.driezy.medlog.R
import com.driezy.medlog.ui.components.ViewfinderOverlay
import com.driezy.medlog.ui.utils.performConfirmHapticFeedback
import java.util.concurrent.Executors

private const val TAG = "OcrCameraPreview"

/**
 * 可复用的 CameraX 预览 + 拍照组件。
 *
 * 底部浮动工具栏包含拍照按钮和闪光灯切换。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("UnsafeOptInUsageError")
@Composable
internal fun OcrCameraPreview(
    modifier: Modifier = Modifier,
    isProcessing: Boolean,
    frameWidthFraction: Float = 0.82f,
    frameAspectRatio: Float = 1.5f,
    onCaptureRequested: () -> Unit,
    onCapture: (ImageProxy, OcrRecognitionRegion) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    var isFlashOn by rememberSaveable { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var frozenBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // 处理完毕后清除冻结帧（恢复实时预览）
    LaunchedEffect(isProcessing) {
        if (!isProcessing) frozenBitmap = null
    }

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
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
        }.onFailure { Log.e(TAG, "Camera bind failed", it) }
    }

    LaunchedEffect(isFlashOn, camera) {
        camera?.cameraControl?.enableTorch(isFlashOn)
    }

    val motionScheme = MaterialTheme.motionScheme
    val recognitionRegion = remember(frameWidthFraction, frameAspectRatio) {
        OcrRecognitionRegion(
            enabled = true,
            widthFraction = frameWidthFraction,
            aspectRatio = frameAspectRatio,
        )
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        // 拍照后显示冻结帧，覆盖实时预览
        frozenBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        // 取景框引导（处理中或冻结时隐藏）
        if (!isProcessing && frozenBitmap == null) {
            ViewfinderOverlay(
                widthFraction = frameWidthFraction,
                aspectRatio = frameAspectRatio,
            )
        }

        HorizontalFloatingToolbar(
            expanded = true,
            floatingActionButton = {
                FloatingToolbarDefaults.VibrantFloatingActionButton(
                    onClick = {
                        if (!isProcessing) {
                            view.performConfirmHapticFeedback()
                            // 冻结预览帧：捕获当前画面作为静态图
                            frozenBitmap = previewView.bitmap
                            onCaptureRequested()
                            imageCapture.takePicture(
                                executor,
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        onCapture(image, recognitionRegion)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        Log.e(TAG, "Capture failed", exception)
                                    }
                                },
                            )
                        }
                    },
                ) {
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
                            LoadingIndicator(modifier = Modifier.size(36.dp))
                        } else {
                            MedLogIcon(
                                MedLogIcons.CameraAlt,
                                contentDescription = stringResource(R.string.ocr_capture),
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
        ) {
            FilledTonalIconButton(
                onClick = { isFlashOn = !isFlashOn },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isFlashOn) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (isFlashOn) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
            ) {
                MedLogIcon(
                    if (isFlashOn) MedLogIcons.FlashOn else MedLogIcons.FlashOff,
                    contentDescription = stringResource(R.string.ocr_flash_toggle),
                )
            }
        }
    }
}
