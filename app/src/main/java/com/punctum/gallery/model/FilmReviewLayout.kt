package com.punctum.gallery.model

import kotlin.math.abs
import kotlin.math.min

internal const val FILM_PAGE_SIZE = 6
internal const val FILM_INFO_SIDE_MARGIN = 24f
internal const val FILM_INFO_HEIGHT = 76f
internal const val FILM_INFO_GAP = 12f
internal const val FILM_INFO_MAX_LIFT = 18f

internal data class FilmImageSize(val width: Float, val height: Float)

internal data class FilmProjectionFrame(val size: FilmImageSize, val top: Float)

/** Equal margins against the actual screen top and table, with a separate portrait height budget. */
internal fun filmProjectionFrame(
    aspect: Float, width: Float, height: Float, infoVisible: Boolean, minimumMargin: Float = 24f,
): FilmProjectionFrame {
    val usableHeight = height.coerceAtLeast(0f)
    val margin = minimumMargin.coerceIn(0f, usableHeight / 4f)
    val maxWidth = if (aspect.isFinite() && aspect > 1f) {
        (width - FILM_INFO_SIDE_MARGIN * 2f).coerceAtLeast(0f)
    } else width * .8f
    val restingSize = fitFilmImage(aspect, maxWidth, usableHeight - margin * 2f)
    val resting = FilmProjectionFrame(restingSize, (usableHeight - restingSize.height) / 2f)
    if (!infoVisible) return resting
    val bottom = (usableHeight - FILM_INFO_HEIGHT - FILM_INFO_GAP).coerceAtLeast(0f)
    val overlap = (resting.top + restingSize.height - bottom).coerceAtLeast(0f)
    if (overlap == 0f) return resting
    // Move only when necessary; portraits shrink to make room instead of jumping far upward.
    val lift = min(overlap, FILM_INFO_MAX_LIFT)
    val center = (usableHeight / 2f - lift).coerceIn(0f, bottom)
    val safeMargin = min(margin, bottom / 4f)
    val halfHeight = min((center - safeMargin).coerceAtLeast(0f), (bottom - center).coerceAtLeast(0f))
    // Extremely short windows cannot retain the resting center; center within the remaining area.
    val finalCenter = if (halfHeight > 0f) center else bottom / 2f
    val available = if (halfHeight > 0f) halfHeight * 2f else (bottom - safeMargin * 2f).coerceAtLeast(0f)
    val size = fitFilmImage(aspect, restingSize.width, min(restingSize.height, available))
    return FilmProjectionFrame(size, (finalCenter - size.height / 2f).coerceAtLeast(0f))
}

/** The slide always fits inside a fixed projection envelope, including panoramas. */
internal fun fitFilmImage(aspect: Float, maxWidth: Float, maxHeight: Float): FilmImageSize {
    val ratio = aspect.takeIf { it.isFinite() && it > 0f } ?: 1f
    val width = min(maxWidth.coerceAtLeast(0f), maxHeight.coerceAtLeast(0f) * ratio)
    return FilmImageSize(width, width / ratio)
}

internal enum class FilmGesture { NONE, PREVIOUS, NEXT, DELETE }

/** Taps in the outer quarters switch photos; the middle stays inert. */
internal fun filmEdgeTap(x: Float, width: Float): FilmGesture = when {
    width <= 0f || x < 0f || x > width -> FilmGesture.NONE
    x < width * .25f -> FilmGesture.PREVIOUS
    x > width * .75f -> FilmGesture.NEXT
    else -> FilmGesture.NONE
}

/** Direction dominance prevents a diagonal page swipe from becoming a deletion. */
internal fun filmGesture(dx: Float, dy: Float, threshold: Float): FilmGesture = when {
    dy < -threshold && -dy > abs(dx) * 1.4f -> FilmGesture.DELETE
    abs(dx) > threshold && abs(dx) > abs(dy) * 1.2f ->
        if (dx < 0f) FilmGesture.NEXT else FilmGesture.PREVIOUS
    else -> FilmGesture.NONE
}

internal fun filmReplacement(ids: List<String>, removed: String): String? {
    val index = ids.indexOf(removed)
    return if (index < 0) null else ids.getOrNull(index + 1) ?: ids.getOrNull(index - 1)
}

/** Compress the repository's Chinese month spans without changing the represented dates. */
internal fun filmCompactTimeSpan(value: String): String {
    val sameYear = Regex("^(\\d{4})年(\\d{1,2})-(\\d{1,2})月$").matchEntire(value)
    if (sameYear != null) {
        val (year, start, end) = sameYear.destructured
        return "$year.${start.padStart(2, '0')} — $year.${end.padStart(2, '0')}"
    }
    return Regex("(\\d{4})年(\\d{1,2})月").replace(value) {
        "${it.groupValues[1]}.${it.groupValues[2].padStart(2, '0')}"
    }.replace(Regex("\\s+[-–—]\\s+"), " — ")
}
