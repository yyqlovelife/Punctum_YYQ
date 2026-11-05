package com.punctum.gallery.model

import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

internal data class DetailDecodeSize(val width: Int, val height: Int)

/** Screen-native base layer. Larger source detail belongs to the bounded region layer. */
internal fun detailDecodeSize(
    sourceWidth: Int, sourceHeight: Int, screenWidth: Int, screenHeight: Int,
    maxPixels: Long = 4_500_000L,
): DetailDecodeSize {
    val viewportWidth = screenWidth.coerceAtLeast(1)
    val viewportHeight = screenHeight.coerceAtLeast(1)
    if (sourceWidth <= 0 || sourceHeight <= 0) {
        val width = ceil(viewportWidth * 1.125).toInt()
        val height = ceil(viewportHeight * 1.125).toInt()
        val ratio = min(1.0, sqrt(maxPixels.coerceAtLeast(1).toDouble() / (width.toDouble() * height)))
        return DetailDecodeSize((width * ratio).toInt().coerceAtLeast(1), (height * ratio).toInt().coerceAtLeast(1))
    }
    // Ordinary detail uses screen width even for a scrolling portrait. Film shares this
    // same base and cache key, so its smaller projection never causes a lower-detail reuse.
    var scale = min(1.0, viewportWidth * 1.125 / sourceWidth)
    val pixels = sourceWidth.toDouble() * sourceHeight * scale * scale
    if (pixels > maxPixels.coerceAtLeast(1)) scale *= sqrt(maxPixels.coerceAtLeast(1) / pixels)
    return DetailDecodeSize(ceil(sourceWidth * scale).toInt().coerceAtLeast(1),
        ceil(sourceHeight * scale).toInt().coerceAtLeast(1))
}

/** Current, next, previous, ...; quick cuts have five prepared neighbors in either direction. */
internal fun detailWarmIndices(count: Int, center: Int, radius: Int = 5): List<Int> {
    if (count <= 0 || center !in 0 until count) return emptyList()
    return buildList {
        add(center)
        for (distance in 1..radius.coerceIn(0, 5)) {
            if (center + distance < count) add(center + distance)
            if (center - distance >= 0) add(center - distance)
        }
    }
}
