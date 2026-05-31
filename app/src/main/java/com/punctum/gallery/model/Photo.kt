package com.punctum.gallery.model

import android.net.Uri
import java.util.Locale

/** 一幅作品：照片本身 + 轻量级 EXIF。 */
data class Photo(
    val uri: Uri,
    val name: String,
    val width: Int = 0,
    val height: Int = 0,
    val takenMillis: Long = 0L,
    val dateTaken: String? = null,
    val latLong: DoubleArray? = null,
    val shutter: String? = null,
    val iso: String? = null,
    val aperture: String? = null,
    val focalLength: String? = null,
    val device: String? = null,
    val lens: String? = null,
) {
    /** 显示用宽高比（已按 EXIF 方向校正）。无法读取时回退为 1。 */
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f

    val coordinateText: String?
        get() = latLong?.let { String.format(Locale.US, "%.5f, %.5f", it[0], it[1]) }

    override fun equals(other: Any?): Boolean = other is Photo && other.uri == uri
    override fun hashCode(): Int = uri.hashCode()
}
