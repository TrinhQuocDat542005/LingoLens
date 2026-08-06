package com.quocdat.lingolens.recognition

import org.junit.Assert.assertTrue
import org.junit.Test

class ImageQualityAnalyzerTest {
    @Test
    fun `dark image is warned`() {
        val result = ImageQualityAnalyzer.assess(brightness = 0.08f, sharpness = 0.02f)
        assertTrue(ImageQualityWarning.TOO_DARK in result.warnings)
    }

    @Test
    fun `overexposed image is warned`() {
        val result = ImageQualityAnalyzer.assess(brightness = 0.97f, sharpness = 0.02f)
        assertTrue(ImageQualityWarning.TOO_BRIGHT in result.warnings)
    }

    @Test
    fun `blurry image is warned`() {
        val result = ImageQualityAnalyzer.assess(brightness = 0.5f, sharpness = 0.0001f)
        assertTrue(ImageQualityWarning.BLURRY in result.warnings)
    }

    @Test
    fun `balanced sharp image has no warning`() {
        val result = ImageQualityAnalyzer.assess(brightness = 0.5f, sharpness = 0.02f)
        assertTrue(result.warnings.isEmpty())
    }
}
