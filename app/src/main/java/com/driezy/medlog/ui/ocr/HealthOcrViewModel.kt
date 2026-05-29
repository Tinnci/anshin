package com.driezy.medlog.ui.ocr

import androidx.camera.core.ImageProxy
import com.driezy.medlog.data.model.OcrParseResult
import com.driezy.medlog.di.HealthOcr
import com.driezy.medlog.domain.health.AiExecutionStatus
import com.driezy.medlog.domain.health.AiFallbackReason
import com.driezy.medlog.domain.health.HealthCloudImageAnalysisUseCase
import com.driezy.medlog.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class HealthOcrUiState(
    val parseResult: OcrParseResult = OcrParseResult(emptyList(), emptyList(), emptyList()),
    val recognitionOutput: OcrRecognitionOutput = OcrRecognitionOutput.Empty,
    val isProcessing: Boolean = false,
    val processingStage: Int = 0, // 0=idle, 1=recognizing, 2=parsing
    val showResults: Boolean = false,
    val canRunCloudAnalysis: Boolean = false,
    val isCloudAnalyzing: Boolean = false,
    val cloudAnalysisFailed: Boolean = false,
    val cloudAnalysisStatus: AiExecutionStatus = AiExecutionStatus.LocalOnly,
)

@HiltViewModel
class HealthOcrViewModel @Inject constructor(
    @param:HealthOcr private val pipeline: OcrPipeline,
    private val cloudImageAnalysis: HealthCloudImageAnalysisUseCase,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(HealthOcrUiState())
    val uiState: StateFlow<HealthOcrUiState> = _uiState.asStateFlow()
    private var capturedCloudImageBytes: ByteArray? = null
    private val capturedCloudImageMimeType = "image/jpeg"

    fun onCaptureRequested() {
        _uiState.update { it.copy(isProcessing = true, processingStage = 0) }
    }

    fun onImageCaptured(imageProxy: ImageProxy, recognitionRegion: OcrRecognitionRegion) {
        capturedCloudImageBytes = imageProxy.toCloudAnalysisJpegBytes()
        _uiState.update { it.copy(processingStage = 1) }
        pipeline.recognize(imageProxy, recognitionRegion) { output ->
            _uiState.update { it.copy(processingStage = 2) }
            val result = HealthMetricParser.parseAll(output.mergedTexts)
            _uiState.update {
                it.copy(
                    parseResult = result,
                    recognitionOutput = output,
                    isProcessing = false,
                    processingStage = 0,
                    showResults = true,
                    cloudAnalysisFailed = false,
                    cloudAnalysisStatus = AiExecutionStatus.LocalOnly,
                )
            }
            refreshCloudAnalysisAvailability()
        }
    }

    fun onCloudAnalyzeRequested() {
        val imageBytes = capturedCloudImageBytes ?: return
        safeLaunch(
            onError = {
                _uiState.update { state ->
                    state.copy(
                        isCloudAnalyzing = false,
                        cloudAnalysisFailed = true,
                        cloudAnalysisStatus = AiExecutionStatus.failedFrom(it),
                    )
                }
            },
        ) {
            val availability = cloudImageAnalysis.availability()
            if (!availability.isAvailable) {
                _uiState.update {
                    it.copy(
                        canRunCloudAnalysis = false,
                        isCloudAnalyzing = false,
                        cloudAnalysisFailed = true,
                        cloudAnalysisStatus = AiExecutionStatus.unavailable(
                            availability.reason?.let(AiFallbackReason::from) ?: AiFallbackReason.UNKNOWN_ERROR,
                        ),
                    )
                }
                return@safeLaunch
            }

            _uiState.update { it.copy(isCloudAnalyzing = true, cloudAnalysisFailed = false) }
            val result = cloudImageAnalysis.analyze(
                imageBytes = imageBytes,
                mimeType = capturedCloudImageMimeType,
            )
            _uiState.update {
                it.copy(
                    parseResult = result,
                    isCloudAnalyzing = false,
                    cloudAnalysisFailed = false,
                    canRunCloudAnalysis = true,
                    cloudAnalysisStatus = AiExecutionStatus.CloudSuccess,
                )
            }
        }
    }

    fun onRetry() {
        capturedCloudImageBytes = null
        _uiState.update {
            HealthOcrUiState()
        }
    }

    override fun onCleared() {
        super.onCleared()
        (pipeline as? AutoCloseable)?.close()
    }

    private fun refreshCloudAnalysisAvailability() {
        safeLaunch {
            val availability = cloudImageAnalysis.availability()
            _uiState.update {
                it.copy(canRunCloudAnalysis = availability.isAvailable && capturedCloudImageBytes != null)
            }
        }
    }
}
