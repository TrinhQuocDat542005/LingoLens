package com.quocdat.lingolens.recognition

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.get
import kotlin.math.abs

object ImageQualityAnalyzer {
    fun analyze(bitmap: Bitmap): ImageQuality {
        val stepX = (bitmap.width / SAMPLE_TARGET).coerceAtLeast(1)
        val stepY = (bitmap.height / SAMPLE_TARGET).coerceAtLeast(1)
        var luminanceSum = 0.0
        var luminanceCount = 0
        var gradientSum = 0.0
        var gradientSquaredSum = 0.0
        var gradientCount = 0

        for (y in 0 until bitmap.height step stepY) {
            var previous = -1f
            for (x in 0 until bitmap.width step stepX) {
                val luminance = luminance(bitmap[x, y])
                luminanceSum += luminance
                luminanceCount++
                if (previous >= 0f) {
                    val gradient = abs(luminance - previous)
                    gradientSum += gradient
                    gradientSquaredSum += gradient * gradient
                    gradientCount++
                }
                previous = luminance
            }
        }

        val brightness = if (luminanceCount == 0) 0f else (luminanceSum / luminanceCount).toFloat()
        val gradientMean = if (gradientCount == 0) 0.0 else gradientSum / gradientCount
        val sharpness = if (gradientCount == 0) 0f else
            (gradientSquaredSum / gradientCount - gradientMean * gradientMean).coerceAtLeast(0.0).toFloat()
        return assess(brightness, sharpness)
    }

    internal fun assess(brightness: Float, sharpness: Float): ImageQuality {
        val warnings = buildSet {
            if (brightness < MIN_BRIGHTNESS) add(ImageQualityWarning.TOO_DARK)
            if (brightness > MAX_BRIGHTNESS) add(ImageQualityWarning.TOO_BRIGHT)
            if (sharpness < MIN_SHARPNESS) add(ImageQualityWarning.BLURRY)
        }
        return ImageQuality(brightness, sharpness, warnings)
    }

    private fun luminance(pixel: Int): Float =
        (0.2126f * Color.red(pixel) + 0.7152f * Color.green(pixel) + 0.0722f * Color.blue(pixel)) / 255f

    private const val SAMPLE_TARGET = 96
    private const val MIN_BRIGHTNESS = 0.16f
    private const val MAX_BRIGHTNESS = 0.93f
    private const val MIN_SHARPNESS = 0.0012f
}
