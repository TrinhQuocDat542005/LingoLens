package com.quocdat.lingolens.recognition

data class RecognitionCandidate(
    val word: String,
    val sourceLabel: String,
    val confidence: Float
)

data class RecognitionResult(
    val candidates: List<RecognitionCandidate>,
    val rawLabels: List<RecognitionCandidate>
) {
    val primary: RecognitionCandidate? get() = candidates.firstOrNull()
    val needsConfirmation: Boolean
        get() = primary == null || primary!!.confidence < HIGH_CONFIDENCE_THRESHOLD

    companion object {
        const val HIGH_CONFIDENCE_THRESHOLD = 0.65f
    }
}

sealed interface RecognitionUiState {
    data object Idle : RecognitionUiState
    data object Loading : RecognitionUiState
    data class Success(val result: RecognitionResult) : RecognitionUiState
    data class NoMatch(val rawLabels: List<RecognitionCandidate>) : RecognitionUiState
    data class Error(val message: String) : RecognitionUiState
}
