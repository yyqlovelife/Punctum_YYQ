package com.punctum.gallery.data

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.app.PendingIntent
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.os.Build
import android.net.Uri
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.content.ContentUris
import androidx.documentfile.provider.DocumentFile
import androidx.core.content.ContextCompat
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

    sealed interface DeleteResult {
        data object Deleted : DeleteResult
        data class NeedsUserAction(val pendingIntent: PendingIntent) : DeleteResult
        data object Failed : DeleteResult
    }

    fun displayName(context: Context, treeUri: Uri): String? =
        DocumentFile.fromTreeUri(context, treeUri)?.name

    suspend fun loadPhotos(context: Context, treeUri: Uri, cachedPhotos: List<Photo> = emptyList()): List<Photo> =
        withContext(Dispatchers.IO) {
            val galleryKey = treeUri.toString()
            val cachedByUri = cachedPhotos.associateBy { it.uri.toString() }
            val cachedBySignature = cachedPhotos.associateBy { "${it.name}|${it.modifiedMillis}|${it.fileSizeBytes}" }
            val files = scanFiles(context, treeUri)
            val photos = coroutineScope {
                files.map { file ->
                    async {
                        val cached = cachedByUri[file.uri.toString()]
                            ?: cachedBySignature["${file.name}|${file.modifiedMillis}|${file.sizeBytes}"]
                        if (cached != null &&
                            cached.modifiedMillis == file.modifiedMillis &&
                            cached.fileSizeBytes == file.sizeBytes &&
                            cached.gpsReadAttempted &&
                            cached.metadataVersion >= METADATA_VERSION
                        ) {
                            val targetThumb = GalleryImageCache.thumbnailPath(context, galleryKey, file.uri)
                            cached.copy(
                                uri = file.uri,
                                thumbnailPath = cached.thumbnailPath
                                    ?.takeIf { it == targetThumb }
                                    ?: targetThumb,
                            )
                        } else {
                            readPhoto(
                                context = context,
                                uri = file.uri,
                                name = file.name,
                                fallbackTime = file.takenMillis ?: file.modifiedMillis,
                                fileSizeBytes = file.sizeBytes,
                                modifiedMillis = file.modifiedMillis,
                                thumbnailPath = GalleryImageCache.thumbnailPath(context, galleryKey, file.uri),
                            )
                        }
                    }
                }.awaitAll()
            }
            photos.sortedByDescending { it.takenMillis } // 拍摄时间新→老
        }

    /** 未缓存照片时，单独扫描计算画廊概览（数量 / 时间跨度 / 封面）。 */
    suspend fun loadOverview(context: Context, gallery: Gallery): GalleryOverview =
        withContext(Dispatchers.IO) {
            val files = scanFiles(context, gallery.uri)
            if (files.isEmpty()) return@withContext GalleryOverview(
                gallery = gallery,
                count = 0,
                timeSpan = "",
                coverUris = emptyList(),
                loading = false,
            )
            val times = coroutineScope {
                files.map { file -> async { file.takenMillis ?: readTakenMillis(context, file.uri, file.modifiedMillis) } }.awaitAll()
            }.sorted()
            val datedFiles = files.zip(times).sortedByDescending { it.second }
            val covers = datedFiles.take(COVER_COUNT).map { it.first.uri }
            val coverPaths = GalleryImageCache.buildCovers(context, gallery.uri.toString(), covers)
            GalleryOverview(
                gallery = gallery,
                count = files.size,
                timeSpan = formatSpan(times),
                coverUris = covers,
                postcardCoverPath = coverPaths.first,
                ticketCoverPath = coverPaths.second,
                loading = false,
            )
        }

    /** 已有缓存照片时，直接派生概览，避免重复 IO。 */
    fun overviewFromPhotos(gallery: Gallery, photos: List<Photo>): GalleryOverview {
        if (photos.isEmpty()) return GalleryOverview(
            gallery = gallery,
            count = 0,
            timeSpan = "",
            coverUris = emptyList(),
            loading = false,
        )
        val span = formatSpan(photos.map { it.takenMillis }.filter { it > 0 }.sorted())
        val covers = photos.take(COVER_COUNT).map { it.uri }
        return GalleryOverview(
            gallery = gallery,
            count = photos.size,
            timeSpan = span,
            coverUris = covers,
            loading = false,
        )
    }

    suspend fun buildOverviewFromPhotos(context: Context, gallery: Gallery, photos: List<Photo>): GalleryOverview =
        withContext(Dispatchers.IO) {
            val base = overviewFromPhotos(gallery, photos)
            val coverPaths = GalleryImageCache.buildCovers(context, gallery.uri.toString(), base.coverUris)
            base.copy(postcardCoverPath = coverPaths.first, ticketCoverPath = coverPaths.second)
        }

    suspend fun ensureThumbnails(context: Context, galleryKey: String, photos: List<Photo>): List<Photo> =
        withContext(Dispatchers.IO) {
            photos.map { photo ->
                val path = GalleryImageCache.ensureThumbnail(context, galleryKey, photo.uri)
                if (path != null && path != photo.thumbnailPath) photo.copy(thumbnailPath = path) else photo
            }
        }

    /** 详情里按需逆地理编码（可能因无后端而返回 null，由调用方回退到经纬度）。 */
    suspend fun reverseGeocode(context: Context, lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) return@withContext null
                val geocoder = Geocoder(context, Locale.ENGLISH)
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
                    listOfNotNull(addr.subLocality, addr.locality, addr.adminArea)
                        .distinct()
                        .joinToString(" · ")
                        .ifBlank { null }
                        ?.takeUnless { containsChinese(it) }
                }
            } catch (e: Exception) {
                null
            }
        }

    suspend fun deletePhoto(context: Context, galleryTreeUri: Uri, photo: Photo): DeleteResult =
        withContext(Dispatchers.IO) {
            val uri = photo.uri
            try {
                when {
                    DocumentsContract.isDocumentUri(context, uri) ->
                        if (DocumentsContract.deleteDocument(context.contentResolver, uri)) DeleteResult.Deleted
                        else DeleteResult.Failed
                    deleteFromAuthorizedTree(context, galleryTreeUri, photo) -> DeleteResult.Deleted
                    DocumentFile.fromSingleUri(context, uri)?.delete() == true -> DeleteResult.Deleted
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                        val pending = MediaStore.createTrashRequest(
                            context.contentResolver,
                            listOf(uri),
                            true,
                        )
                        DeleteResult.NeedsUserAction(pending)
                    }
                    context.contentResolver.delete(uri, null, null) > 0 -> DeleteResult.Deleted
                    else -> DeleteResult.Failed
                }
            } catch (e: Exception) {
                DeleteResult.Failed
            }
        }

    private fun deleteFromAuthorizedTree(context: Context, treeUri: Uri, photo: Photo): Boolean {
        return try {
            val directUri = buildChildDocumentUri(treeUri, photo.name)
            if (directUri != null && DocumentsContract.deleteDocument(context.contentResolver, directUri)) {
                return true
            }
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            val target = tree.listFiles().firstOrNull { doc ->
                doc.isFile &&
                    doc.name == photo.name &&
                    (photo.fileSizeBytes <= 0L || doc.length() == photo.fileSizeBytes)
            } ?: tree.listFiles().firstOrNull { doc ->
                doc.isFile && doc.name == photo.name
            } ?: return false
            DocumentsContract.deleteDocument(context.contentResolver, target.uri)
        } catch (e: Exception) {
            false
        }
    }

    private fun buildChildDocumentUri(treeUri: Uri, fileName: String): Uri? {
        return try {
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri).trimEnd('/')
            val childDocumentId = "$treeDocumentId/$fileName"
            DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocumentId)
        } catch (e: Exception) {
            null
        }
    }

    private fun readTakenMillis(context: Context, uri: Uri, fallback: Long): Long {
        return try {
            openExifInputStream(context, uri)?.use { s ->
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

    private fun readPhoto(
        context: Context,
        uri: Uri,
        name: String,
        fallbackTime: Long,
        fileSizeBytes: Long,
        modifiedMillis: Long,
        thumbnailPath: String?,
    ): Photo {
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

        val gpsReadAttempted = canReadUnredactedGps(context, uri)

        try {
            openExifInputStream(context, uri)?.use { s ->
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
                aperture = if (fNumber > 0.0) "F${trimNumber(fNumber)}" else null
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
            gpsReadAttempted = gpsReadAttempted,
            shutter = shutter,
            iso = iso,
            aperture = aperture,
            focalLength = focal,
            device = device,
            lens = lens,
            fileSizeBytes = fileSizeBytes,
            modifiedMillis = modifiedMillis,
            thumbnailPath = thumbnailPath,
            metadataVersion = METADATA_VERSION,
        )
    }

    private fun openExifInputStream(context: Context, uri: Uri) =
        context.contentResolver.openInputStream(originalExifUri(context, uri))

    private fun originalExifUri(context: Context, uri: Uri): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canReadUnredactedGps(context, uri)) {
            try {
                MediaStore.setRequireOriginal(uri)
            } catch (e: Exception) {
                uri
            }
        } else {
            uri
        }
    }

    private fun canReadUnredactedGps(context: Context, uri: Uri): Boolean {
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        return hasPermission || !isMediaStoreUri(uri)
    }

    private fun isMediaStoreUri(uri: Uri): Boolean =
        uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY

    private data class PhotoFile(
        val uri: Uri,
        val name: String,
        val modifiedMillis: Long,
        val sizeBytes: Long,
        val takenMillis: Long?,
    )

    private fun scanSaf(context: Context, treeUri: Uri): List<PhotoFile> {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return tree.listFiles()
            .asSequence()
            .filter { it.isFile && it.type?.startsWith("image/") == true }
            .map { doc ->
                PhotoFile(
                    uri = doc.uri,
                    name = doc.name ?: "未命名",
                    modifiedMillis = doc.lastModified(),
                    sizeBytes = doc.length(),
                    takenMillis = null,
                )
            }
            .toList()
    }

    private fun scanFiles(context: Context, treeUri: Uri): List<PhotoFile> {
        val media = scanMediaStore(context, treeUri)
        if (hasFullMediaAccess(context) && media.isNotEmpty()) return media
        val saf = scanSaf(context, treeUri)
        return when {
            media.isEmpty() -> saf
            saf.isEmpty() -> media
            media.size < saf.size -> saf
            else -> media
        }
    }

    private fun hasFullMediaAccess(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES,
            ) == PackageManager.PERMISSION_GRANTED

    private fun scanMediaStore(context: Context, treeUri: Uri): List<PhotoFile> {
        val relativePrefix = treeUri.toMediaStoreRelativePath() ?: return emptyList()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.RELATIVE_PATH,
        )
        val result = mutableListOf<PhotoFile>()
        return try {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
                arrayOf("$relativePrefix%"),
                "${MediaStore.Images.Media.DATE_TAKEN} DESC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val takenIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val taken = cursor.getLong(takenIndex).takeIf { it > 0L }
                    result += PhotoFile(
                        uri = uri,
                        name = cursor.getString(nameIndex) ?: "未命名",
                        modifiedMillis = cursor.getLong(modifiedIndex) * 1000L,
                        sizeBytes = cursor.getLong(sizeIndex),
                        takenMillis = taken,
                    )
                }
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun Uri.toMediaStoreRelativePath(): String? {
        val docId = try {
            DocumentsContract.getTreeDocumentId(this)
        } catch (e: Exception) {
            return null
        }
        val path = when {
            docId.startsWith("primary:") -> docId.removePrefix("primary:")
            docId.startsWith("home:") -> "Documents/${docId.removePrefix("home:")}"
            else -> return null
        }.trim('/')
        return if (path.isBlank()) null else "$path/"
    }

    private fun parseExifMillis(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return try {
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(raw)?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun containsChinese(text: String): Boolean =
        text.any { it.code in 0x4E00..0x9FFF }

    private fun formatDevice(makeRaw: String?, modelRaw: String?): String? {
        val make = cleanExifText(makeRaw)
        val model = cleanExifText(modelRaw)
        val raw = model ?: make ?: return null
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
        return SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.US).format(Date(millis))
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

    private const val COVER_COUNT = 4
    private const val METADATA_VERSION = 2
}
