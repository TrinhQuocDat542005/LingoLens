package com.quocdat.lingolens.recognition

import org.junit.Assert.assertEquals
import org.junit.Test

class BoundingBoxMapperTest {
    @Test
    fun `landscape image is letterboxed vertically`() {
        val mapped = BoundingBoxMapper.toCanvas(
            box = DetectionBox(0f, 0f, 1600f, 900f),
            imageWidth = 1600f,
            imageHeight = 900f,
            canvasWidth = 400f,
            canvasHeight = 300f
        )
        assertEquals(0f, mapped.left, 0.01f)
        assertEquals(37.5f, mapped.top, 0.01f)
        assertEquals(400f, mapped.right, 0.01f)
        assertEquals(262.5f, mapped.bottom, 0.01f)
    }

    @Test
    fun `canvas tap maps back into original image`() {
        val point = BoundingBoxMapper.toImagePoint(
            canvasX = 200f,
            canvasY = 150f,
            imageWidth = 1600f,
            imageHeight = 900f,
            canvasWidth = 400f,
            canvasHeight = 300f
        )
        assertEquals(800f, point.first, 0.01f)
        assertEquals(450f, point.second, 0.01f)
    }

    @Test
    fun `detection box contains only internal points`() {
        val box = DetectionBox(10f, 20f, 100f, 120f)
        assert(box.contains(50f, 50f))
        assert(!box.contains(5f, 50f))
    }
}
