package com.punctum.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import coil.size.pxOrElse
import com.punctum.gallery.model.Photo
import okio.buffer
import okio.source
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import kotlin.math.max

/**
 * 给 Coil 的静态画面源：列表优先走系统缩略图，实况照片只读 JPEG 前缀，
 * 避免把尾部数 MB 的 MP4 一起送进解码器。
 */
data class PhotoStill(
    val uri: Uri,
    val jpegByteCount: Long? = null,
    val preferThumbnail: Boolean = false,
) {
    companion object {
        fun forList(photo: Photo): Any =
            photo.thumbnailPath
                ?.let(::File)
                ?.takeIf { it.exists() && it.length() > 0L }
                ?: PhotoStill(
                    uri = photo.uri,
                    jpegByteCount = photo.stillImageByteCount,
                    preferThumbnail = true,
                )

        fun forDetail(photo: Photo): PhotoStill =
            PhotoStill(
                uri = photo.uri,
                jpegByteCount = photo.stillImageByteCount,
                preferThumbnail = false,
            )
    }
}

class PhotoStillFetcher(
    private val data: PhotoStill,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val context = options.context
        if (data.preferThumbnail) {
            systemThumbnail(context, data.uri, options)?.let { bitmap ->
                return DrawableResult(
                    drawable = BitmapDrawable(context.resources, bitmap),
                    isSampled = true,
                    dataSource = DataSource.DISK,
                )
            }
        }
        val jpegLimit = data.jpegByteCount?.takeIf { it > 0L }
        val stream = openStillStream(context, data.uri, jpegLimit) ?: return null
        return SourceResult(
            source = ImageSource(stream.source().buffer(), context),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<PhotoStill> {
        override fun create(
            data: PhotoStill,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = PhotoStillFetcher(data, options)
    }
}

internal fun systemThumbnail(context: Context, uri: Uri, options: Options? = null): Bitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    return runCatching {
        val requested = options
            ?.size
            ?.let { size ->
                val width = size.width.pxOrElse { 900 }
                val height = size.height.pxOrElse { 900 }
                max(width, height).coerceIn(256, 1400)
            }
            ?: 900
        context.contentResolver.loadThumbnail(uri, Size(requested, requested), null)
    }.getOrNull()
}

internal fun openStillStream(
    context: Context,
    uri: Uri,
    jpegByteCount: Long?,
): InputStream? {
    val raw = context.contentResolver.openInputStream(uri) ?: return null
    val limit = jpegByteCount?.takeIf { it > 0L } ?: return raw
    return LimitedInputStream(raw, limit)
}

private class LimitedInputStream(
    source: InputStream,
    private val limit: Long,
) : FilterInputStream(source) {
    private var remaining = limit

    override fun read(): Int {
        if (remaining <= 0L) return -1
        val value = super.read()
        if (value >= 0) remaining--
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining <= 0L) return -1
        val count = super.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (count > 0) remaining -= count
        return count
    }

    override fun skip(byteCount: Long): Long {
        if (remaining <= 0L || byteCount <= 0L) return 0L
        val skipped = super.skip(minOf(byteCount, remaining))
        remaining -= skipped
        return skipped
    }

    override fun available(): Int = minOf(super.available().toLong(), remaining).toInt()
}
