package com.quocdat.lingolens.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class ObjectRecognitionService(private val context: Context) : AutoCloseable {
    private val detector = ObjectDetector.createFromOptions(
        context,
        ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
            .setRunningMode(RunningMode.IMAGE)
            .setMaxResults(MAX_DETECTIONS)
            .setScoreThreshold(MIN_DETECTION_CONFIDENCE)
            .build()
    )
    private val fallbackLabeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(MIN_FALLBACK_CONFIDENCE).build()
    )

    suspend fun recognize(uri: Uri): RecognitionResult = withContext(Dispatchers.Default) {
        val bitmap = decodeOrientedBitmap(uri)
            ?: error("Không thể đọc ảnh đã chụp.")
        val quality = ImageQualityAnalyzer.analyze(bitmap)
        val startedAt = System.nanoTime()
        val detectorResult = detector.detect(BitmapImageBuilder(bitmap).build())
        val inferenceMs = (System.nanoTime() - startedAt) / 1_000_000

        val allDetections = detectorResult.detections().flatMap { detection ->
            val rect = detection.boundingBox()
            detection.categories().map { category ->
                RecognitionCandidate(
                    word = SupportedVocabulary.canonicalWord(category.categoryName())
                        ?: category.categoryName().lowercase(),
                    sourceLabel = category.categoryName(),
                    confidence = category.score(),
                    boundingBox = DetectionBox(rect.left, rect.top, rect.right, rect.bottom)
                )
            }
        }.sortedByDescending(RecognitionCandidate::confidence)

        val supported = allDetections
            .filter { SupportedVocabulary.canonicalWord(it.sourceLabel) != null }
            .map { it.copy(word = SupportedVocabulary.canonicalWord(it.sourceLabel)!!) }
            .take(MAX_DETECTIONS)

        if (supported.isNotEmpty()) {
            return@withContext RecognitionResult(
                candidates = supported,
                rawLabels = allDetections.take(MAX_RAW_RESULTS),
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                inferenceTimeMs = inferenceMs,
                engine = RecognitionEngine.EFFICIENTDET,
                imageQuality = quality
            )
        }

        val fallback = runFallback(bitmap)
        RecognitionResult(
            candidates = fallback.first,
            rawLabels = (allDetections + fallback.second)
                .distinctBy { it.sourceLabel.lowercase() }
                .take(MAX_RAW_RESULTS),
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            inferenceTimeMs = inferenceMs,
            engine = RecognitionEngine.ML_KIT_FALLBACK,
            imageQuality = quality
        )
    }

    private suspend fun runFallback(bitmap: Bitmap): Pair<List<RecognitionCandidate>, List<RecognitionCandidate>> {
        val labels = suspendCancellableCoroutine { continuation ->
            fallbackLabeler.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        }
        val raw = labels.sortedByDescending { it.confidence }.take(MAX_RAW_RESULTS).map {
            RecognitionCandidate(it.text.lowercase(), it.text, it.confidence)
        }
        val supported = labels.mapNotNull { label ->
            SupportedVocabulary.canonicalWord(label.text)?.let {
                RecognitionCandidate(it, label.text, label.confidence)
            }
        }.distinctBy(RecognitionCandidate::word).sortedByDescending(RecognitionCandidate::confidence)
        return supported to raw
    }

    private fun decodeOrientedBitmap(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_SIDE)
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply {
            postRotate(degrees)
        }, true).also { if (it !== bitmap) bitmap.recycle() }
    }

    override fun close() {
        detector.close()
        fallbackLabeler.close()
    }

    private fun calculateSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (width / sample > maxSide || height / sample > maxSide) sample *= 2
        return sample
    }

    private companion object {
        const val MODEL_ASSET = "efficientdet_lite0.tflite"
        const val MAX_IMAGE_SIDE = 1600
        const val MIN_DETECTION_CONFIDENCE = 0.35f
        const val MIN_FALLBACK_CONFIDENCE = 0.35f
        const val MAX_DETECTIONS = 10
        const val MAX_RAW_RESULTS = 8
    }
}
