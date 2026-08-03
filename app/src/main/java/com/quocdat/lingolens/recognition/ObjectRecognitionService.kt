package com.quocdat.lingolens.recognition

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class ObjectRecognitionService(private val context: Context) : AutoCloseable {
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(MIN_MODEL_CONFIDENCE)
            .build()
    )

    suspend fun recognize(uri: Uri): RecognitionResult {
        val input = InputImage.fromFilePath(context, uri)
        val labels = suspendCancellableCoroutine { continuation ->
            labeler.process(input)
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        }

        val raw = labels
            .sortedByDescending { it.confidence }
            .take(MAX_RAW_RESULTS)
            .map { RecognitionCandidate(it.text.lowercase(), it.text, it.confidence) }

        val supported = labels
            .mapNotNull { label ->
                SupportedVocabulary.canonicalWord(label.text)?.let { word ->
                    RecognitionCandidate(word, label.text, label.confidence)
                }
            }
            .groupBy(RecognitionCandidate::word)
            .mapNotNull { (_, candidates) -> candidates.maxByOrNull(RecognitionCandidate::confidence) }
            .sortedByDescending(RecognitionCandidate::confidence)
            .take(MAX_SUPPORTED_RESULTS)

        return RecognitionResult(supported, raw)
    }

    override fun close() = labeler.close()

    private companion object {
        const val MIN_MODEL_CONFIDENCE = 0.35f
        const val MAX_RAW_RESULTS = 5
        const val MAX_SUPPORTED_RESULTS = 3
    }
}
