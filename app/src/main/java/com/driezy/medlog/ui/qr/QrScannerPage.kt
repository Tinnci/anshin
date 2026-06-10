package com.driezy.medlog.ui.qr

import com.driezy.medlog.ui.icons.MedLogIcon
import com.driezy.medlog.ui.icons.MedLogIcons

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import kotlinx.coroutines.launch
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import androidx.photopicker.compose.EmbeddedPhotoPicker
import androidx.photopicker.compose.EmbeddedPhotoPickerState
import androidx.photopicker.compose.rememberEmbeddedPhotoPickerState
import androidx.photopicker.compose.ExperimentalPhotoPickerComposeApi
import android.widget.photopicker.EmbeddedPhotoPickerFeatureInfo
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.driezy.medlog.R
import com.driezy.medlog.ui.components.CameraPermissionGate
import com.driezy.medlog.ui.components.CameraGuidancePill
import com.driezy.medlog.ui.components.ViewfinderOverlay
import com.driezy.medlog.ui.theme.MedLogSpacing
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

private const val TAG = "QrScannerPage"

/**
 * 全屏二维码扫描页面（CameraX + ML Kit）。
 *
 * @param onResult 成功扫描到有效二维码内容后回调（只触发一次）
 * @param onBack   用户按返回时回调
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalPhotoPickerComposeApi::class)
@Composable
fun QrScannerPage(
    onResult: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.qr_scan_image_not_found)

    val isSupported = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                android.os.ext.SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) >= 15
    }

    val pickerState = rememberEmbeddedPhotoPickerState()

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                runCatching {
                    val image = InputImage.fromFilePath(context, uri)
                    val options = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                    val scanner = BarcodeScanning.getClient(options)
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            val rawValue = barcodes.firstOrNull()?.rawValue
                            if (rawValue != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onResult(rawValue)
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar(errorMessage)
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Barcode scan failed from Uri", e)
                            scope.launch {
                                snackbarHostState.showSnackbar(errorMessage)
                            }
                        }
                }.onFailure { e ->
                    Log.e(TAG, "Failed to load image from Uri", e)
                    scope.launch {
                        snackbarHostState.showSnackbar(errorMessage)
                    }
                }
            }
        }
    )

    // Process URIs selected in the EmbeddedPhotoPicker
    LaunchedEffect(pickerState.selectedMedia) {
        if (pickerState.selectedMedia.isNotEmpty()) {
            val uri = pickerState.selectedMedia.first()
            runCatching {
                val image = InputImage.fromFilePath(context, uri)
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
                val scanner = BarcodeScanning.getClient(options)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val rawValue = barcodes.firstOrNull()?.rawValue
                        if (rawValue != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onResult(rawValue)
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(errorMessage)
                            }
                            scope.launch {
                                pickerState.deselectUri(uri)
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Barcode scan failed from Uri", e)
                        scope.launch {
                            snackbarHostState.showSnackbar(errorMessage)
                        }
                        scope.launch {
                            pickerState.deselectUri(uri)
                        }
                    }
            }.onFailure { e ->
                Log.e(TAG, "Failed to load image from Uri", e)
                scope.launch {
                    snackbarHostState.showSnackbar(errorMessage)
                }
                scope.launch {
                    pickerState.deselectUri(uri)
                }
            }
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded
        )
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = if (isSupported) 220.dp else 0.dp,
        sheetContent = {
            if (isSupported) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SafeEmbeddedPhotoPicker(
                        state = pickerState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(Modifier.fillMaxWidth().height(1.dp))
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qr_scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        MedLogIcon(MedLogIcons.ArrowBack, contentDescription = stringResource(R.string.common_back_cd))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            pickMediaLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Text(stringResource(R.string.qr_scan_from_gallery))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            CameraPermissionGate(
                rationaleRes = R.string.qr_scan_permission_rationale,
                grantButtonRes = R.string.qr_scan_grant_permission,
            ) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onQrScanned = { raw ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onResult(raw)
                    },
                )
                ViewfinderOverlay(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 100.dp),
                    widthFraction = 0.72f,
                    aspectRatio = 1f,
                )
                CameraGuidancePill(
                    text = stringResource(R.string.qr_scan_hint),
                    icon = MedLogIcons.QrCodeScanner,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp),
                )
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    onQrScanned: (String) -> Unit,
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var scanned        by remember { mutableStateOf(false) }
    val executor       = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    val previewView = remember {
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }

    LaunchedEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider       = cameraProviderFuture.get()

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val barcodeScanner = BarcodeScanning.getClient(options)

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { ia ->
                ia.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null && !scanned) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                barcodes.firstOrNull()?.rawValue?.let { value ->
                                    if (!scanned) {
                                        scanned = true
                                        onQrScanned(value)
                                    }
                                }
                            }
                            .addOnFailureListener { Log.w(TAG, "Barcode scan failed", it) }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }
            }

        runCatching {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis,
            )
        }.onFailure { Log.e(TAG, "Camera bind failed", it) }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@RequiresExtension(extension = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, version = 15)
@OptIn(ExperimentalPhotoPickerComposeApi::class)
@Composable
private fun SafeEmbeddedPhotoPicker(
    state: EmbeddedPhotoPickerState,
    modifier: Modifier
) {
    val featureInfo = remember {
        EmbeddedPhotoPickerFeatureInfo.Builder()
            .setAccentColor(0xFF4CAF50L) // Green scanning line color
            .setMaxSelectionLimit(1)
            .build()
    }
    EmbeddedPhotoPicker(
        state = state,
        embeddedPhotoPickerFeatureInfo = featureInfo,
        modifier = modifier
    )
}

