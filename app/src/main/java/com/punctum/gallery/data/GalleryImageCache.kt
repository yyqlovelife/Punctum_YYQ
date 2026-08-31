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
    const val TICKET_COLOR_VERSION = 5

    private const val THUMB_SIZE = 1400
    private const val COVER_SIZE = 900
    private const val DOMINANT_COLOR_SAMPLE_SIZE = 128
    private const val MAX_GALLERY_THUMB_BYTES = 1024L * 1024L * 1024L

    data class CoverAssets(
        val postcardPath: String?,
        val ticketPath: String?,
        val ticketDominantColorArgb: Int?,
        val ticketColorVersion: Int,
    )

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

    suspend fun buildCovers(context: Context, galleryKey: String, coverUris: List<Uri>): CoverAssets =
        withContext(Dispatchers.IO) {
            val postcard = buildPostcardCover(context, galleryKey, coverUris.take(4))
            val ticket = coverUris.firstOrNull()?.let { uri ->
                ensureThumbnail(context, galleryKey, uri)
            }
            CoverAssets(
                postcardPath = postcard,
                ticketPath = ticket,
                ticketDominantColorArgb = ticket?.let(::dominantColorFromFile),
                ticketColorVersion = TICKET_COLOR_VERSION,
            )
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
        systemThumbnail(context, uri)?.let { return it }
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

    /**
     * 从票根封面缩略图中提取出现频率最高的非黑、非白量化颜色。
     * 先缩小到约 128px，再将 RGB 各压缩为 4 bit（4096 个色桶），
     * 避免 JPEG 噪点把视觉上相同的颜色拆成大量近似值。
     */
    private fun dominantColorFromFile(path: String): Int? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val maxDimension = max(bounds.outWidth, bounds.outHeight)
        val sample = Integer.highestOneBit(
            (maxDimension / DOMINANT_COLOR_SAMPLE_SIZE).coerceAtLeast(1)
        )
        val bitmap = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null

        return try {
            val buckets = IntArray(16 * 16 * 16)
            val pixels = IntArray(bitmap.width)
            for (y in 0 until bitmap.height) {
                bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
                for (pixel in pixels) {
                    if (Color.alpha(pixel) < 128) continue
                    val r = Color.red(pixel) ushr 4
                    val g = Color.green(pixel) ushr 4
                    val b = Color.blue(pixel) ushr 4
                    buckets[(r shl 8) or (g shl 4) or b] += 1
                }
            }
            val rankedBuckets = buckets.indices
                .asSequence()
                .filter { buckets[it] > 0 }
                .sortedByDescending { buckets[it] }
                .filterNot(::isBlackOrWhiteBucket)
                .take(TICKET_COLOR_CANDIDATE_COUNT)
                .toList()
            val bucket = rankedBuckets.firstOrNull {
                bucketSaturation(it) >= MIN_CANDIDATE_SATURATION
            } ?: rankedBuckets.firstOrNull() ?: return null
            val red = (((bucket ushr 8) and 0xF) shl 4) or 0x8
            val green = (((bucket ushr 4) and 0xF) shl 4) or 0x8
            val blue = ((bucket and 0xF) shl 4) or 0x8
            normalizeTicketColor(Color.rgb(red, green, blue))
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * JPEG 与量化会让纯黑/纯白产生少量偏差，因此按近似极值过滤。
     * 排名第一的颜色命中极值时，序列会自然转向第二（或下一）候选色。
     */
    private fun isBlackOrWhiteBucket(bucket: Int): Boolean {
        val red = (((bucket ushr 8) and 0xF) shl 4) or 0x8
        val green = (((bucket ushr 4) and 0xF) shl 4) or 0x8
        val blue = ((bucket and 0xF) shl 4) or 0x8
        val nearBlack = maxOf(red, green, blue) <= 24
        val nearWhite = minOf(red, green, blue) >= 240
        return nearBlack || nearWhite
    }

    private fun bucketSaturation(bucket: Int): Float {
        val red = (((bucket ushr 8) and 0xF) shl 4) or 0x8
        val green = (((bucket ushr 4) and 0xF) shl 4) or 0x8
        val blue = ((bucket and 0xF) shl 4) or 0x8
        val hsv = FloatArray(3)
        Color.RGBToHSV(red, green, blue, hsv)
        return hsv[1]
    }

    /**
     * 将照片主色收敛到 Punctum 的票根色域：
     * 有色照片提高最低饱和度，并将最高明度压至更深的区间；
     * 在保持照片色相特征的同时，避免票根出现发灰、发白的浅色。
     * 真正的灰度照片保留中性灰，不根据微小色差强行染色。
     */
    private fun normalizeTicketColor(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        if (hsv[1] < GRAYSCALE_SATURATION_THRESHOLD) {
            hsv[1] = 0f
            hsv[2] = hsv[2].coerceIn(MIN_GRAY_VALUE, MAX_GRAY_VALUE)
        } else {
            hsv[1] = hsv[1].coerceIn(MIN_TICKET_SATURATION, MAX_TICKET_SATURATION)
            hsv[2] = hsv[2].coerceIn(MIN_TICKET_VALUE, MAX_TICKET_VALUE)
        }
        return Color.HSVToColor(hsv)
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

    private const val TICKET_COLOR_CANDIDATE_COUNT = 12
    private const val MIN_CANDIDATE_SATURATION = 0.12f
    private const val GRAYSCALE_SATURATION_THRESHOLD = 0.08f
    private const val MIN_TICKET_SATURATION = 0.36f
    private const val MAX_TICKET_SATURATION = 0.68f
    private const val MIN_TICKET_VALUE = 0.30f
    private const val MAX_TICKET_VALUE = 0.48f
    private const val MIN_GRAY_VALUE = 0.32f
    private const val MAX_GRAY_VALUE = 0.44f
}
