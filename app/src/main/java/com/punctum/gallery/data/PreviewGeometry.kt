package com.punctum.gallery.data

/** Allow integer rounding, but reject embedded previews with different framing/aspect. */
internal fun previewAspectMismatch(
    previewWidth: Int, previewHeight: Int, sourceWidth: Int, sourceHeight: Int,
): Boolean {
    if (minOf(previewWidth, previewHeight, sourceWidth, sourceHeight) <= 0) return false
    val relativeRatio = (previewWidth.toDouble() * sourceHeight) / (previewHeight.toDouble() * sourceWidth)
    return kotlin.math.abs(relativeRatio - 1.0) > 0.02
}
