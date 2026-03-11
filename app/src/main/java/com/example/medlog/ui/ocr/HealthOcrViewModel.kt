package com.example.medlog.ui.ocr

import android.app.Application
import androidx.camera.core.ImageProxy
import androidx.lifecycle.viewModelScope
import com.example.medlog.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HealthOcrUiState(
    val parseResult: OcrParseResult = OcrParseResult(emptyList(), emptyList(), emptyList()),
    val isProcessing: Boolean = false,
    val processingStage: Int = 0, // 0=idle, 1=recognizing, 2=parsing
    val showResults: Boolean = false,
)

@HiltViewModel
class HealthOcrViewModel @Inject constructor(
    application: Application,
) : BaseViewModel() {

    private val sevenSegRecognizer = SevenSegmentRecognizer(application)
    private val lcdDetector = LcdDisplayDetector(application)

    private val _uiState = MutableStateFlow(HealthOcrUiState())
    val uiState: StateFlow<HealthOcrUiState> = _uiState.asStateFlow()

    fun onCaptureRequested() {
        _uiState.update { it.copy(isProcessing = true, processingStage = 0) }
    }

    fun onImageCaptured(imageProxy: ImageProxy) {
        _uiState.update { it.copy(processingStage = 1) }
        processImage(imageProxy, sevenSegRecognizer, lcdDetector) { texts ->
            _uiState.update { it.copy(processingStage = 2) }
            val result = HealthMetricParser.parseAll(texts)
            _uiState.update {
                it.copy(
                    parseResult = result,
                    isProcessing = false,
                    processingStage = 0,
                    showResults = true,
                )
            }
        }
    }

    fun onRetry() {
        _uiState.update {
            HealthOcrUiState() // 重置为初始状态
        }
    }

    override fun onCleared() {
        super.onCleared()
        sevenSegRecognizer.close()
        lcdDetector.close()
    }
}
