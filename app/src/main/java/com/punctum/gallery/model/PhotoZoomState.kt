package com.punctum.gallery.model

/** Image-only pose around the original frame's center, retained between gestures. */
internal data class PhotoZoomState(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    val isZoomed: Boolean get() = scale > 1f

    fun transform(
        factor: Float,
        focalX: Float,
        focalY: Float,
        panX: Float,
        panY: Float,
        width: Float,
        height: Float,
    ): PhotoZoomState {
        if (!factor.isFinite() || factor <= 0f || !focalX.isFinite() ||
            !focalY.isFinite() || !panX.isFinite() || !panY.isFinite() ||
            !width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f
        ) return this
        val nextScale = (scale * factor).coerceIn(1f, 5f)
        val ratio = nextScale / scale
        val maxX = (nextScale - 1f) * width / 2f
        val maxY = (nextScale - 1f) * height / 2f
        return PhotoZoomState(
            nextScale,
            (offsetX * ratio + focalX * (1f - ratio) + panX).coerceIn(-maxX, maxX),
            (offsetY * ratio + focalY * (1f - ratio) + panY).coerceIn(-maxY, maxY),
        )
    }
}
