package com.punctum.gallery.model

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Coordinates are edges between pixels, so mirrored right/bottom edges may equal width/height. */
internal data class OriginalPixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    fun intersects(other: OriginalPixelRect): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top
}

internal data class OriginalTileKey(val sample: Int, val rect: OriginalPixelRect)

internal class OriginalOrientation(val encodedWidth: Int, val encodedHeight: Int, val exif: Int) {
    val swapsAxes: Boolean get() = exif in 5..8
    val width: Int get() = if (swapsAxes) encodedHeight else encodedWidth
    val height: Int get() = if (swapsAxes) encodedWidth else encodedHeight

    fun encodedRect(rect: OriginalPixelRect): OriginalPixelRect {
        fun point(x: Int, y: Int): Pair<Int, Int> = when (exif) {
            2 -> encodedWidth - x to y
            3 -> encodedWidth - x to encodedHeight - y
            4 -> x to encodedHeight - y
            5 -> y to x
            6 -> y to encodedHeight - x
            7 -> encodedWidth - y to encodedHeight - x
            8 -> encodedWidth - y to x
            else -> x to y
        }
        val points = listOf(point(rect.left, rect.top), point(rect.right, rect.top),
            point(rect.left, rect.bottom), point(rect.right, rect.bottom))
        return OriginalPixelRect(points.minOf { it.first }.coerceIn(0, encodedWidth),
            points.minOf { it.second }.coerceIn(0, encodedHeight),
            points.maxOf { it.first }.coerceIn(0, encodedWidth),
            points.maxOf { it.second }.coerceIn(0, encodedHeight))
    }
}

/** Never downsample farther than one decoded pixel per displayed physical pixel. */
internal fun originalTileSample(sourcePixelsPerScreenPixel: Float): Int {
    if (!sourcePixelsPerScreenPixel.isFinite() || sourcePixelsPerScreenPixel <= 1f) return 1
    var sample = 1
    while (sample < 64 && sample * 2f <= sourcePixelsPerScreenPixel) sample *= 2
    return sample
}

internal fun originalVisibleRect(
    left: Float, top: Float, right: Float, bottom: Float, width: Int, height: Int,
): OriginalPixelRect? {
    if (width <= 0 || height <= 0 || !listOf(left, top, right, bottom).all { it.isFinite() }) return null
    val rect = OriginalPixelRect(floor(left.toDouble()).toInt().coerceIn(0, width),
        floor(top.toDouble()).toInt().coerceIn(0, height),
        ceil(right.toDouble()).toInt().coerceIn(0, width),
        ceil(bottom.toDouble()).toInt().coerceIn(0, height))
    return rect.takeIf { it.width > 0 && it.height > 0 }
}

/** Visible center first, then visible edges, then a one-tile guard ring. */
internal fun originalTilesForWindow(
    visible: OriginalPixelRect, width: Int, height: Int, sample: Int, tilePixels: Int = 512,
): List<OriginalTileKey> {
    if (width <= 0 || height <= 0 || sample <= 0 || tilePixels <= 0) return emptyList()
    val side = tilePixels * sample
    val x0 = max(0, visible.left / side - 1)
    val y0 = max(0, visible.top / side - 1)
    val x1 = min((width - 1) / side, (visible.right - 1) / side + 1)
    val y1 = min((height - 1) / side, (visible.bottom - 1) / side + 1)
    val centerX = (visible.left + visible.right) / 2.0
    val centerY = (visible.top + visible.bottom) / 2.0
    return buildList {
        for (y in y0..y1) for (x in x0..x1) {
            add(OriginalTileKey(sample, OriginalPixelRect(x * side, y * side,
                min(width, (x + 1) * side), min(height, (y + 1) * side))))
        }
    }.sortedWith(compareBy<OriginalTileKey> { !it.rect.intersects(visible) }.thenBy {
        val dx = (it.rect.left + it.rect.right) / 2.0 - centerX
        val dy = (it.rect.top + it.rect.bottom) / 2.0 - centerY
        dx * dx + dy * dy
    })
}

internal data class OriginalTileRequestPlan(
    val visibleKeys: Set<OriginalTileKey>,
    val requests: List<OriginalTileKey>,
    val budgetBytes: Long,
)

/** Includes a sampled-pixel halo and row/alignment allowance without allocating a bitmap. */
internal fun originalTileEstimatedBytes(key: OriginalTileKey): Long {
    val width = (key.rect.width + key.sample - 1) / key.sample + 3L
    val height = (key.rect.height + key.sample - 1) / key.sample + 3L
    return width * height * 4L
}

/** Reserve the complete native-quality visible layer before spending any memory on guard tiles. */
internal fun originalTileRequestPlan(visible: OriginalPixelRect, tiles: List<OriginalTileKey>): OriginalTileRequestPlan {
    val visibleKeys = tiles.filter { it.rect.intersects(visible) }.toSet()
    val visibleBytes = visibleKeys.sumOf(::originalTileEstimatedBytes)
    val minBudget = 32L * 1024 * 1024
    val usualMaximum = 64L * 1024 * 1024
    // Very high-resolution displays can need more than the usual cap at one sampling boundary.
    // That exception admits only the actual visible layer, bounded by the viewport, with no guard.
    val budget = max(visibleBytes, min(usualMaximum, max(minBudget, visibleBytes + 8L * 1024 * 1024)))
    var guardAllowance = budget - visibleBytes
    val requests = buildList {
        addAll(tiles.filter { it in visibleKeys })
        for (key in tiles) if (key !in visibleKeys) {
            val bytes = originalTileEstimatedBytes(key)
            if (bytes <= guardAllowance) { add(key); guardAllowance -= bytes }
        }
    }
    return OriginalTileRequestPlan(visibleKeys, requests, budget)
}
