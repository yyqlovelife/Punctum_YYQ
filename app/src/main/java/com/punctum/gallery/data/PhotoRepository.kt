package com.punctum.gallery.data

import android.content.Context
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.os.Build
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import com.punctum.gallery.model.Gallery
import com.punctum.gallery.model.GalleryOverview
import com.punctum.gallery.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.coroutines.resume

/**
 * 核心能力：通过 SAF tree URI 读取用户授权文件夹里的照片与 EXIF。
 * 读取的是原始文件字节，GPS 不被系统抹除，且无需任何媒体/位置权限。
 *
 * 性能：批量加载时并行读取，并刻意「不做」逆地理编码（避免逐张联网）；
 * 地名反查只在照片详情里按需进行。
 */
object PhotoRepository {

    fun displayName(context: Context, treeUri: Uri): String? =
        DocumentFile.fromTreeUri(context, treeUri)?.name

    suspend fun loadPhotos(context: Context, treeUri: Uri): List<Photo> =
        withContext(Dispatchers.IO) {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
            val files = tree.listFiles().filter { it.isFile && it.type?.startsWith("image/") == true }
            val photos = coroutineScope {
                files.map { doc ->
                    async { readPhoto(context, doc.uri, doc.name ?: "未命名", doc.lastModified()) }
                }.awaitAll()
            }
            photos.sortedByDescending { it.takenMillis } // 拍摄时间新→老
        }

    /** 未缓存照片时，单独扫描计算画廊概览（数量 / 时间跨度 / 封面）。 */
    suspend fun loadOverview(context: Context, gallery: Gallery): GalleryOverview =
        withContext(Dispatchers.IO) {
            val tree = DocumentFile.fromTreeUri(context, gallery.uri)
                ?: return@withContext GalleryOverview(gallery, loading = false)
            val files = tree.listFiles().filter { it.isFile && it.type?.startsWith("image/") == true }
            if (files.isEmpty()) return@withContext GalleryOverview(gallery, 0, "", emptyList(), false)
            val times = coroutineScope {
                files.map { doc -> async { readTakenMillis(context, doc.uri, doc.lastModified()) } }.awaitAll()
            }.sorted()
            val covers = files.sortedByDescending { it.lastModified() }.take(COVER_COUNT).map { it.uri }
            GalleryOverview(gallery, files.size, formatSpan(times), covers, false)
        }

    /** 已有缓存照片时，直接派生概览，避免重复 IO。 */
    fun overviewFromPhotos(gallery: Gallery, photos: List<Photo>): GalleryOverview {
        if (photos.isEmpty()) return GalleryOverview(gallery, 0, "", emptyList(), false)
        val span = formatSpan(photos.map { it.takenMillis }.filter { it > 0 }.sorted())
        val covers = photos.take(COVER_COUNT).map { it.uri }
        return GalleryOverview(gallery, photos.size, span, covers, false)
    }

    /** 详情里按需逆地理编码（可能因无后端而返回 null，由调用方回退到经纬度）。 */
    suspend fun reverseGeocode(context: Context, lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) return@withContext null
                val geocoder = Geocoder(context, Locale.CHINA)
                val results = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(lat, lon, 1) { addresses ->
                            if (continuation.isActive) continuation.resume(addresses)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lon, 1)
                }
                results?.firstOrNull()?.let { addr ->
                    listOfNotNull(addr.adminArea, addr.locality, addr.featureName)
                        .distinct().joinToString(" · ").ifBlank { null }
                }
            } catch (e: Exception) {
                null
            }
        }

    suspend fun deletePhoto(context: Context, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                when {
                    DocumentsContract.isDocumentUri(context, uri) ->
                        DocumentsContract.deleteDocument(context.contentResolver, uri)
                    DocumentFile.fromSingleUri(context, uri)?.delete() == true -> true
                    else -> context.contentResolver.delete(uri, null, null) > 0
                }
            } catch (e: Exception) {
                false
            }
        }

    private fun readTakenMillis(context: Context, uri: Uri, fallback: Long): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { s ->
                val exif = ExifInterface(s)
                parseExifMillis(
                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                ) ?: fallback
            } ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }

    private fun readPhoto(context: Context, uri: Uri, name: String, fallbackTime: Long): Photo {
        var width = 0
        var height = 0
        var rotation = 0
        var millis = fallbackTime
        var latLong: DoubleArray? = null
        var shutter: String? = null
        var iso: String? = null
        var aperture: String? = null
        var focal: String? = null
        var device: String? = null
        var lens: String? = null

        try {
            context.contentResolver.openInputStream(uri)?.use { s ->
                val exif = ExifInterface(s)
                rotation = exif.rotationDegrees
                parseExifMillis(
                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                )?.let { millis = it }
                latLong = exif.latLong
                val exposure = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                val fNumber = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
                val focalLen = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                val isoValue = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
                shutter = if (exposure > 0.0) formatShutter(exposure) else null
                aperture = if (fNumber > 0.0) "f/${trimNumber(fNumber)}" else null
                focal = if (focalLen > 0.0) "${trimNumber(focalLen)} mm" else null
                iso = if (isoValue > 0) "ISO $isoValue" else null
                device = formatDevice(
                    exif.getAttribute(ExifInterface.TAG_MAKE),
                    exif.getAttribute(ExifInterface.TAG_MODEL),
                )
                lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()?.ifBlank { null }
            }
        } catch (e: Exception) {
            // 保留默认值
        }

        try {
            context.contentResolver.openInputStream(uri)?.use { s ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(s, null, opts)
                width = opts.outWidth
                height = opts.outHeight
            }
        } catch (e: Exception) {
            // 尺寸读不到则回退为 1:1
        }
        if (rotation == 90 || rotation == 270) {
            val tmp = width; width = height; height = tmp
        }

        return Photo(
            uri = uri,
            name = name,
            width = width,
            height = height,
            takenMillis = millis,
            dateTaken = formatDate(millis),
            latLong = latLong,
            shutter = shutter,
            iso = iso,
            aperture = aperture,
            focalLength = focal,
            device = device,
            lens = lens,
        )
    }

    private fun parseExifMillis(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return try {
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(raw)?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun formatDevice(makeRaw: String?, modelRaw: String?): String? {
        val make = cleanExifText(makeRaw)
        val model = cleanExifText(modelRaw)
        val raw = when {
            make == null -> model
            model == null -> make
            model.contains(make, ignoreCase = true) -> model
            else -> "$make $model"
        } ?: return null
        return collapseRepeatedLeadingWords(raw).ifBlank { null }
    }

    private fun cleanExifText(raw: String?): String? =
        raw?.trim()?.replace(Regex("\\s+"), " ")?.ifBlank { null }

    private fun collapseRepeatedLeadingWords(raw: String): String {
        val words = raw.trim().split(Regex("\\s+"))
        if (words.size < 2) return raw.trim()
        return if (words[0].equals(words[1], ignoreCase = true)) {
            words.drop(1).joinToString(" ")
        } else {
            raw.trim()
        }
    }

    private fun formatDate(millis: Long): String? {
        if (millis <= 0L) return null
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))
    }

    /** 年-月时间跨度：同年同月→2025年1月；同年→2025年1-5月；跨年→2025年1月 - 2026年10月。 */
    private fun formatSpan(timesAsc: List<Long>): String {
        val valid = timesAsc.filter { it > 0 }
        if (valid.isEmpty()) return ""
        val cal = Calendar.getInstance()
        cal.timeInMillis = valid.first()
        val y1 = cal.get(Calendar.YEAR); val m1 = cal.get(Calendar.MONTH) + 1
        cal.timeInMillis = valid.last()
        val y2 = cal.get(Calendar.YEAR); val m2 = cal.get(Calendar.MONTH) + 1
        return when {
            y1 == y2 && m1 == m2 -> "${y1}年${m1}月"
            y1 == y2 -> "${y1}年${m1}-${m2}月"
            else -> "${y1}年${m1}月 - ${y2}年${m2}月"
        }
    }

    private fun formatShutter(seconds: Double): String =
        if (seconds >= 1.0) "${trimNumber(seconds)} s" else "1/${(1.0 / seconds).roundToInt()} s"

    private fun trimNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format(Locale.US, "%.1f", value)

    private const val COVER_COUNT = 3
}
