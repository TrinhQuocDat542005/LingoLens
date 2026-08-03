package com.quocdat.lingolens.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSamplingTest {
    @Test
    fun `small image is decoded at original size`() {
        assertEquals(1, calculateSampleSize(width = 1280, height = 720, maxSide = 1600))
    }

    @Test
    fun `large image uses power of two sampling`() {
        assertEquals(4, calculateSampleSize(width = 6000, height = 4000, maxSide = 1600))
    }

    @Test
    fun `portrait image is sampled by its longest side`() {
        assertEquals(2, calculateSampleSize(width = 1080, height = 2400, maxSide = 1600))
    }
}
