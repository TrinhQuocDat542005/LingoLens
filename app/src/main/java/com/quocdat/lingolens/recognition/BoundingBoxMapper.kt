package com.quocdat.lingolens.recognition

import kotlin.math.min

object BoundingBoxMapper {
    fun toCanvas(
        box: DetectionBox,
        imageWidth: Float,
        imageHeight: Float,
        canvasWidth: Float,
        canvasHeight: Float
    ): DetectionBox {
        val transform = transform(imageWidth, imageHeight, canvasWidth, canvasHeight)
        return DetectionBox(
            left = box.left * transform.scale + transform.offsetX,
            top = box.top * transform.scale + transform.offsetY,
            right = box.right * transform.scale + transform.offsetX,
            bottom = box.bottom * transform.scale + transform.offsetY
        )
    }

    fun toImagePoint(
        canvasX: Float,
        canvasY: Float,
        imageWidth: Float,
        imageHeight: Float,
        canvasWidth: Float,
        canvasHeight: Float
    ): Pair<Float, Float> {
        val transform = transform(imageWidth, imageHeight, canvasWidth, canvasHeight)
        return ((canvasX - transform.offsetX) / transform.scale) to
            ((canvasY - transform.offsetY) / transform.scale)
    }

    private fun transform(iw: Float, ih: Float, cw: Float, ch: Float): Transform {
        if (iw <= 0f || ih <= 0f || cw <= 0f || ch <= 0f) return Transform(1f, 0f, 0f)
        val scale = min(cw / iw, ch / ih)
        return Transform(scale, (cw - iw * scale) / 2f, (ch - ih * scale) / 2f)
    }

    private data class Transform(val scale: Float, val offsetX: Float, val offsetY: Float)
}
