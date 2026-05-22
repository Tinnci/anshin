package com.driezy.medlog.ui.ocr

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.driezy.medlog.ui.theme.MedLogSpacing
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.driezy.medlog.R
import com.driezy.medlog.ui.components.CameraPermissionGate
import com.driezy.medlog.ui.components.CameraGuidancePill
import com.driezy.medlog.ui.components.CameraReadinessPanel
import com.driezy.medlog.ui.components.ProcessingOverlay
import com.driezy.medlog.ui.utils.performConfirmHapticFeedback

private const val MEDICATION_OCR_FRAME_WIDTH = 0.88f
private const val MEDICATION_OCR_FRAME_ASPECT = 2.8f

/**
 * 通用 OCR 扫描页面：使用 CameraX 拍照 + ML Kit 文字识别。
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
    viewModel: OcrScannerViewModel = hiltViewModel(),
) {
    val motionScheme = MaterialTheme.motionScheme
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ocr_scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back_cd))
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
                            slideInVertically(motionScheme.defaultSpatialSpec()) { it / 8 })
                            .togetherWith(
                                fadeOut(motionScheme.fastEffectsSpec()) +
                                    slideOutVertically(motionScheme.fastSpatialSpec()) { -it / 8 },
                            )
                    },
                    label = "ocr_content",
                ) { resultsVisible ->
                    if (!resultsVisible) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            OcrCameraPreview(
                                modifier = Modifier.fillMaxSize(),
                                isProcessing = state.isProcessing,
                                frameWidthFraction = MEDICATION_OCR_FRAME_WIDTH,
                                frameAspectRatio = MEDICATION_OCR_FRAME_ASPECT,
                                onCaptureRequested = { viewModel.onCaptureRequested() },
                                onCapture = { imageProxy, region -> viewModel.onImageCaptured(imageProxy, region) },
                            )
                            CameraGuidancePill(
                                text = stringResource(R.string.ocr_scan_hint),
                                icon = Icons.Rounded.DocumentScanner,
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
                                text = stringResource(R.string.ocr_processing),
                                elevated = false,
                            )
                        }
                    } else {
                        OcrResultList(
                            texts = state.recognizedTexts,
                            onSelect = { text ->
                                view.performConfirmHapticFeedback()
                                onResult(text.trim())
                            },
                            onRetry = { viewModel.onRetry() },
                        )
                    }
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
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp,
        ) {
            Text(
                text = if (texts.isNotEmpty()) {
                    stringResource(R.string.ocr_result_hint)
                } else {
                    stringResource(R.string.ocr_no_text_found)
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = MedLogSpacing.Large, vertical = MedLogSpacing.Large),
            )
        }

        if (texts.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.ocr_no_text_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = MedLogSpacing.Large, vertical = MedLogSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(texts) { index, text ->
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

                    Card(
                        onClick = { onSelect(text) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = animatedAlpha.value
                                translationY = animatedOffset.value
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
