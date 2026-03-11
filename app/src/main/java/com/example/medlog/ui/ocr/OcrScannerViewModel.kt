package com.example.medlog.ui.ocr

import androidx.camera.core.ImageProxy
import com.example.medlog.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class OcrScannerUiState(
    val recognizedTexts: List<String> = emptyList(),
    val isProcessing: Boolean = false,
    val showResults: Boolean = false,
)

@HiltViewModel
class OcrScannerViewModel @Inject constructor() : BaseViewModel() {

    private val _uiState = MutableStateFlow(OcrScannerUiState())
    val uiState: StateFlow<OcrScannerUiState> = _uiState.asStateFlow()

    fun onCaptureRequested() {
        _uiState.update { it.copy(isProcessing = true) }
    }

    fun onImageCaptured(imageProxy: ImageProxy) {
        processImage(imageProxy, sevenSegRecognizer = null) { texts ->
            _uiState.update {
                it.copy(
                    recognizedTexts = texts,
                    isProcessing = false,
                    showResults = texts.isNotEmpty(),
                )
            }
        }
    }

    fun onRetry() {
        _uiState.update { OcrScannerUiState() }
    }
}
