package com.quocdat.lingolens.recognition

data class DetectionBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val area: Float get() = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

data class RecognitionCandidate(
    val word: String,
    val sourceLabel: String,
    val confidence: Float,
    val boundingBox: DetectionBox? = null
)

enum class RecognitionEngine { EFFICIENTDET, ML_KIT_FALLBACK }

enum class ImageQualityWarning { TOO_DARK, TOO_BRIGHT, BLURRY }

data class ImageQuality(
    val brightness: Float,
    val sharpness: Float,
    val warnings: Set<ImageQualityWarning>
)

data class RecognitionResult(
    val candidates: List<RecognitionCandidate>,
    val rawLabels: List<RecognitionCandidate>,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val inferenceTimeMs: Long = 0,
    val engine: RecognitionEngine = RecognitionEngine.EFFICIENTDET,
    val imageQuality: ImageQuality? = null
) {
    val primary: RecognitionCandidate? get() = candidates.firstOrNull()
    val needsConfirmation: Boolean
        get() = primary == null || primary!!.confidence < HIGH_CONFIDENCE_THRESHOLD ||
            imageQuality?.warnings?.isNotEmpty() == true

    companion object {
        const val HIGH_CONFIDENCE_THRESHOLD = 0.65f
    }
}

sealed interface RecognitionUiState {
    data object Idle : RecognitionUiState
    data object Loading : RecognitionUiState
    data class Success(val result: RecognitionResult) : RecognitionUiState
    data class NoMatch(
        val rawLabels: List<RecognitionCandidate>,
        val imageQuality: ImageQuality? = null,
        val inferenceTimeMs: Long = 0
    ) : RecognitionUiState
    data class Error(val message: String) : RecognitionUiState
}
