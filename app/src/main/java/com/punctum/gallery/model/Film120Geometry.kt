package com.punctum.gallery.model

/** Ratios measured on the supplied 1519×1538 reference; only the photo opening is exactly square. */
internal object Film120Geometry {
    const val WIDTH = 1519f
    const val HEIGHT = 1538f
    const val PHOTO_LEFT = 51f
    const val PHOTO_TOP = 64f
    const val PHOTO_SIDE = 1420f
    const val RANDOM_LEFT = 830f
    const val NUMBER_LEFT = 1130f
    const val NUMBER_TOP = 1483f
    const val DIGIT_WIDTH = 34f
    const val DIGIT_HEIGHT = 56f

    fun size(maxWidth: Float, maxHeight: Float): FilmImageSize {
        val width = minOf((maxWidth - 4f).coerceAtLeast(1f), (maxHeight - 5f).coerceAtLeast(1f) * WIDTH / HEIGHT)
        return FilmImageSize(width, width * HEIGHT / WIDTH)
    }

    data class Crop(val left: Int, val top: Int, val side: Int)

    fun crop(width: Int, height: Int): Crop {
        require(width > 0 && height > 0)
        val side = minOf(width, height)
        return Crop((width - side) / 2, (height - side) / 2, side)
    }

    fun emulsionCode(identity: String): String = (10L + (identity.hashCode().toLong() and 0xffffffffL) % 90L).toString()
}
