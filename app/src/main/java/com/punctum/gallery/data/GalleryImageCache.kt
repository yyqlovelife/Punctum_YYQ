package com.punctum.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt

object GalleryImageCache {
    private const val THUMB_SIZE = 1400
    private const val COVER_SIZE = 900
    private const val MAX_GALLERY_THUMB_BYTES = 1024L * 1024L * 1024L

    suspend fun ensureThumbnail(context: Context, galleryKey: String, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            val file = thumbFile(context, galleryKey, uri)
            if (file.exists() && file.length() > 0L) return@withContext file.absolutePath
            val bitmap = loadBitmap(context, uri, THUMB_SIZE) ?: return@withContext null
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            bitmap.recycle()
            file.absolutePath
        }

    suspend fun buildCovers(context: Context, galleryKey: String, coverUris: List<Uri>): Pair<String?, String?> =
        withContext(Dispatchers.IO) {
            val postcard = buildPostcardCover(context, galleryKey, coverUris.take(4))
            val ticket = coverUris.firstOrNull()?.let { uri ->
                ensureThumbnail(context, galleryKey, uri)
            }
            postcard to ticket
        }

    suspend fun trimGalleryThumbnails(context: Context, galleryKey: String, photosNewestFirst: List<com.punctum.gallery.model.Photo>) =
        withContext(Dispatchers.IO) {
            val dir = galleryThumbDir(context, galleryKey)
            val files = dir.listFiles()?.filter { it.isFile } ?: return@withContext
            val total = files.sumOf { it.length() }
            if (total <= MAX_GALLERY_THUMB_BYTES) return@withContext

            val keep = photosNewestFirst
                .takeWhileInclusiveBySize(MAX_GALLERY_THUMB_BYTES) { photo ->
                    photo.thumbnailPath?.let(::File)?.length() ?: 0L
                }
                .mapNotNull { it.thumbnailPath }
                .map { File(it).absolutePath }
                .toSet()
            files.forEach { file ->
                if (file.absolutePath !in keep) file.delete()
            }
        }

    fun thumbnailPath(context: Context, galleryKey: String, uri: Uri): String =
        thumbFile(context, galleryKey, uri).absolutePath

    private fun buildPostcardCover(context: Context, galleryKey: String, uris: List<Uri>): String? {
        if (uris.isEmpty()) return null
        val coverKey = safeKey(galleryKey + "|" + uris.joinToString("|") { it.toString() })
        val out = File(coverDir(context), "$coverKey-postcard.jpg")
        if (out.exists() && out.length() > 0L) return out.absolutePath
        val bitmaps = uris.mapNotNull { loadBitmapBlocking(context, it, COVER_SIZE / 2) }
        if (bitmaps.isEmpty()) return null

        val canvasBitmap = Bitmap.createBitmap(COVER_SIZE, COVER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        canvas.drawColor(Color.rgb(21, 17, 14))
        val gap = 28
        val pad = 34
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        if (bitmaps.size < 4) {
            drawCenterCrop(canvas, bitmaps.first(), Rect(pad, pad, COVER_SIZE - pad, COVER_SIZE - pad), paint)
        } else {
            val cell = (COVER_SIZE - pad * 2 - gap) / 2
            val rects = listOf(
                Rect(pad, pad, pad + cell, pad + cell),
                Rect(pad + cell + gap, pad, pad + cell * 2 + gap, pad + cell),
                Rect(pad, pad + cell + gap, pad + cell, pad + cell * 2 + gap),
                Rect(pad + cell + gap, pad + cell + gap, pad + cell * 2 + gap, pad + cell * 2 + gap),
            )
            bitmaps.take(4).forEachIndexed { index, bitmap -> drawCenterCrop(canvas, bitmap, rects[index], paint) }
        }

        out.parentFile?.mkdirs()
        val tmp = File(out.parentFile, "${out.name}.tmp")
        tmp.outputStream().use { canvasBitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        if (!tmp.renameTo(out)) {
            tmp.copyTo(out, overwrite = true)
            tmp.delete()
        }
        bitmaps.forEach { it.recycle() }
        canvasBitmap.recycle()
        return out.absolutePath
    }

    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, dst: Rect, paint: Paint) {
        val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val dstRatio = dst.width().toFloat() / dst.height().toFloat()
        val src = if (srcRatio > dstRatio) {
            val width = (bitmap.height * dstRatio).toInt()
            val left = (bitmap.width - width) / 2
            Rect(left, 0, left + width, bitmap.height)
        } else {
            val height = (bitmap.width / dstRatio).toInt()
            val top = (bitmap.height - height) / 2
            Rect(0, top, bitmap.width, top + height)
        }
        canvas.drawBitmap(bitmap, src, RectF(dst), paint)
    }

    private fun loadBitmap(context: Context, uri: Uri, size: Int): Bitmap? =
        loadBitmapBlocking(context, uri, size)

    private fun loadBitmapBlocking(context: Context, uri: Uri, size: Int): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val width = info.size.width
                    val height = info.size.height
                    val scale = size.toFloat() / max(width, height).coerceAtLeast(1)
                    if (scale < 1f) {
                        decoder.setTargetSize(
                            (width * scale).roundToInt().coerceAtLeast(1),
                            (height * scale).roundToInt().coerceAtLeast(1),
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                decodeSampledBitmap(context, uri, size)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri, size: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val maxDim = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        val sample = Integer.highestOneBit((maxDim / size).coerceAtLeast(1))
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }?.let { bitmap ->
            val rotation = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    androidx.exifinterface.media.ExifInterface(stream).rotationDegrees
                } ?: 0
            } catch (e: Exception) {
                0
            }
            if (rotation == 0) bitmap else {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    .also { bitmap.recycle() }
            }
        }
    }

    private fun thumbFile(context: Context, galleryKey: String, uri: Uri): File =
        File(galleryThumbDir(context, galleryKey), "${safeKey(uri.toString())}.jpg")

    private fun galleryThumbDir(context: Context, galleryKey: String): File =
        File(context.cacheDir, "punctum_thumbnails_v2/${safeKey(galleryKey)}")

    private fun coverDir(context: Context): File =
        File(context.cacheDir, "punctum_covers")

    private fun safeKey(raw: String): String {
        val md = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return md.joinToString("") { "%02x".format(it) }
    }

    private inline fun <T> List<T>.takeWhileInclusiveBySize(maxBytes: Long, sizeOf: (T) -> Long): List<T> {
        val kept = mutableListOf<T>()
        var total = 0L
        for (item in this) {
            val size = sizeOf(item)
            if (total + size > maxBytes && kept.isNotEmpty()) break
            total += size
            kept += item
        }
        return kept
    }
}
