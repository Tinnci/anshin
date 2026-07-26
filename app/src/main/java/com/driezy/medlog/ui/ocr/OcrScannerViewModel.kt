package com.driezy.medlog.ui.ocr

import androidx.camera.core.ImageProxy
import com.driezy.medlog.di.MlKitOcr
import com.driezy.medlog.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class OcrScannerUiState(
    val recognitionOutput: OcrRecognitionOutput = OcrRecognitionOutput.Empty,
    val isProcessing: Boolean = false,
    val showResults: Boolean = false,
    val hasEmptyResult: Boolean = false,
)

@HiltViewModel
class OcrScannerViewModel @Inject constructor(@param:MlKitOcr private val pipeline: OcrPipeline) : BaseViewModel() {

    private val _uiState = MutableStateFlow(OcrScannerUiState())
    val uiState: StateFlow<OcrScannerUiState> = _uiState.asStateFlow()

    fun onCaptureRequested() {
        _uiState.update { it.copy(isProcessing = true) }
    }

    fun onImageCaptured(imageProxy: ImageProxy, recognitionRegion: OcrRecognitionRegion) {
        pipeline.recognize(imageProxy, recognitionRegion) { output ->
            _uiState.update {
                it.copy(
                    recognitionOutput = output,
                    isProcessing = false,
                    showResults = true,
                    hasEmptyResult = output.mergedTexts.isEmpty(),
                )
            }
        }
    }

    fun onRetry() {
        _uiState.update { OcrScannerUiState() }
    }
}
