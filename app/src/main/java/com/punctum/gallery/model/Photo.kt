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
    val gpsReadAttempted: Boolean = false,
    val shutter: String? = null,
    val iso: String? = null,
    val aperture: String? = null,
    val focalLength: String? = null,
    val device: String? = null,
    val lens: String? = null,
    val fileSizeBytes: Long = 0L,
    val modifiedMillis: Long = 0L,
    val thumbnailPath: String? = null,
    val motionPhotoVideoLength: Long = 0L,
    val motionPhotoPresentationTimestampUs: Long = 0L,
    val motionPhotoChecked: Boolean = false,
    val metadataVersion: Int = 0,
) {
    /** 显示用宽高比（已按 EXIF 方向校正）。无法读取时回退为 1。 */
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f

    val coordinateText: String?
        get() = latLong?.let { String.format(Locale.US, "%.5f, %.5f", it[0], it[1]) }

    val resolutionText: String?
        get() = if (width > 0 && height > 0) "$width × $height" else null

    val isMotionPhoto: Boolean
        get() = motionPhotoVideoLength > 0L

    /** 实况文件里静态 JPEG 的字节长度；普通照片为 null，交给系统按整文件解码。 */
    val stillImageByteCount: Long?
        get() = motionPhotoVideoLength
            .takeIf { it > 0L && fileSizeBytes > it }
            ?.let { fileSizeBytes - it }

    val fileSizeText: String?
        get() = when {
            fileSizeBytes <= 0L -> null
            fileSizeBytes >= 1024L * 1024L * 1024L ->
                String.format(Locale.US, "%.2f GB", fileSizeBytes / (1024.0 * 1024.0 * 1024.0))
            fileSizeBytes >= 1024L * 1024L ->
                String.format(Locale.US, "%.1f MB", fileSizeBytes / (1024.0 * 1024.0))
            fileSizeBytes >= 1024L ->
                String.format(Locale.US, "%.0f KB", fileSizeBytes / 1024.0)
            else -> "$fileSizeBytes B"
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Photo) return false
        return uri == other.uri &&
            name == other.name &&
            width == other.width &&
            height == other.height &&
            takenMillis == other.takenMillis &&
            dateTaken == other.dateTaken &&
            latLong.contentEqualsNullable(other.latLong) &&
            gpsReadAttempted == other.gpsReadAttempted &&
            shutter == other.shutter &&
            iso == other.iso &&
            aperture == other.aperture &&
            focalLength == other.focalLength &&
            device == other.device &&
            lens == other.lens &&
            fileSizeBytes == other.fileSizeBytes &&
            modifiedMillis == other.modifiedMillis &&
            thumbnailPath == other.thumbnailPath &&
            motionPhotoVideoLength == other.motionPhotoVideoLength &&
            motionPhotoPresentationTimestampUs == other.motionPhotoPresentationTimestampUs &&
            motionPhotoChecked == other.motionPhotoChecked &&
            metadataVersion == other.metadataVersion
    }

    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + takenMillis.hashCode()
        result = 31 * result + (dateTaken?.hashCode() ?: 0)
        result = 31 * result + (latLong?.contentHashCode() ?: 0)
        result = 31 * result + gpsReadAttempted.hashCode()
        result = 31 * result + (shutter?.hashCode() ?: 0)
        result = 31 * result + (iso?.hashCode() ?: 0)
        result = 31 * result + (aperture?.hashCode() ?: 0)
        result = 31 * result + (focalLength?.hashCode() ?: 0)
        result = 31 * result + (device?.hashCode() ?: 0)
        result = 31 * result + (lens?.hashCode() ?: 0)
        result = 31 * result + fileSizeBytes.hashCode()
        result = 31 * result + modifiedMillis.hashCode()
        result = 31 * result + (thumbnailPath?.hashCode() ?: 0)
        result = 31 * result + motionPhotoVideoLength.hashCode()
        result = 31 * result + motionPhotoPresentationTimestampUs.hashCode()
        result = 31 * result + motionPhotoChecked.hashCode()
        result = 31 * result + metadataVersion
        return result
    }
}

private fun DoubleArray?.contentEqualsNullable(other: DoubleArray?): Boolean =
    when {
        this == null -> other == null
        other == null -> false
        else -> contentEquals(other)
    }
