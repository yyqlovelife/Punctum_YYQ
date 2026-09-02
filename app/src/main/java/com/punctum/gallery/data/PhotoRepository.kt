package com.punctum.gallery.data

import android.content.Context
import android.Manifest
import android.content.ContentValues
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
import com.punctum.gallery.model.SystemAlbum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.coroutines.resume

/**
 * 核心能力：通过 SAF tree URI 读取用户授权文件夹里的照片与 EXIF。
 * 读取的是原始文件字节，GPS 不被系统抹除，且无需任何媒体/位置权限。
 *
 * 性能：批量加载复用缓存，未命中时限制 EXIF 并发，并刻意「不做」逆地理编码（避免逐张联网）；
 * 地名反查只在照片详情里按需进行。
 */
object PhotoRepository {

    private val exifReadSemaphore = Semaphore(4)

    sealed interface DeleteResult {
        data object Deleted : DeleteResult
        data object NeedsMediaManagementPermission : DeleteResult
        data class NeedsUserAction(
            val pendingIntent: PendingIntent,
            val pendingUris: Set<Uri>? = null,
        ) : DeleteResult
        data class PartialFailure(val undeletedUris: Set<Uri>) : DeleteResult
        data object Failed : DeleteResult
    }

    sealed interface MoveResult {
        data object Moved : MoveResult
        data object SameAlbum : MoveResult
        data class NeedsUserAction(val pendingIntent: PendingIntent) : MoveResult
        data object Failed : MoveResult
    }

    fun displayName(context: Context, treeUri: Uri): String? =
        if (SystemAlbum.isAlbumUri(treeUri)) {
            treeUri.getQueryParameter("name")
        } else {
            DocumentFile.fromTreeUri(context, treeUri)?.name
        }

    /** DCIM、Pictures 大目录下的系统图集，按名称字母序。 */
    suspend fun listDcimAndPicturesAlbums(context: Context): List<SystemAlbum> =
        withContext(Dispatchers.IO) {
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            val projection = buildList {
                add(MediaStore.Images.Media.BUCKET_ID)
                add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                if (hasRelativePath) {
                    add(MediaStore.Images.Media.RELATIVE_PATH)
                } else {
                    add(MediaStore.Images.Media.DATA)
                }
            }.toTypedArray()
            data class Acc(var name: String, var path: String, var count: Int)
            val buckets = linkedMapOf<Long, Acc>()
            try {
                context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                    val bucketIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                    val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val pathIndex = if (hasRelativePath) {
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                    } else {
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    }
                    while (cursor.moveToNext()) {
                        val relativePath = if (hasRelativePath) {
                            cursor.getString(pathIndex)
                        } else {
                            relativePathFromData(cursor.getString(pathIndex))
                        } ?: continue
                        if (!isDcimOrPicturesPath(relativePath)) continue
                        val folderName = folderNameFromRelativePath(relativePath)
                        if (folderName.startsWith(".")) continue
                        val bucketId = cursor.getLong(bucketIndex)
                        val displayName = cursor.getString(nameIndex)
                            ?.trim()
                            ?.ifBlank { null }
                            ?: folderName
                        val normalizedPath = normalizeRelativePath(relativePath)
                        val acc = buckets.getOrPut(bucketId) {
                            Acc(displayName, normalizedPath, 0)
                        }
                        acc.count += 1
                        if (normalizedPath.length < acc.path.length) acc.path = normalizedPath
                    }
                }
            } catch (_: Exception) {
                return@withContext emptyList()
            }
            val collator = Collator.getInstance(Locale.getDefault())
            buckets.map { (bucketId, acc) ->
                SystemAlbum(
                    bucketId = bucketId,
                    displayName = acc.name,
                    relativePath = acc.path,
                    itemCount = acc.count,
                )
            }.sortedWith(compareBy(collator) { it.displayName })
        }

    /** 进入画廊时优先完整读取前几张的 EXIF 与尺寸，避免首屏短暂显示错误时间或 1:1 比例。 */
    suspend fun loadLatestPhotos(
        context: Context,
        gallery: Gallery,
        cachedPhotos: List<Photo> = emptyList(),
    ): List<Photo> =
        withContext(Dispatchers.IO) {
            sortFilesByCaptureTime(
                context = context,
                files = scanFiles(context, gallery.uri),
                cachedPhotos = cachedPhotos,
            ).take(COVER_COUNT).map { (file, _) ->
                readPhoto(
                    context = context,
                    uri = file.uri,
                    name = file.name,
                    fallbackTime = file.takenMillis ?: file.modifiedMillis,
                    fileSizeBytes = file.sizeBytes,
                    modifiedMillis = file.modifiedMillis,
                    thumbnailPath = GalleryImageCache.cachedThumbnailPath(
                        context,
                        gallery.uri.toString(),
                        file.uri,
                    ),
                    motionPhotoVideoLength = file.motionPhotoVideoLength,
                    motionPhotoPresentationTimestampUs =
                        file.motionPhotoPresentationTimestampUs,
                    motionPhotoChecked = file.motionPhotoChecked,
                )
            }.sortedWith(PHOTO_NEWEST_FIRST)
        }

    suspend fun loadPhotos(context: Context, treeUri: Uri, cachedPhotos: List<Photo> = emptyList()): List<Photo> =
        withContext(Dispatchers.IO) {
            val galleryKey = treeUri.toString()
            val cachedByUri = cachedPhotos.associateBy { it.uri.toString() }
            val cachedBySignature = cachedPhotos.associateBy { "${it.name}|${it.fileSizeBytes}" }
            val files = scanFiles(context, treeUri)
            val photos = files.map { file ->
                val cached = cachedByUri[file.uri.toString()]
                    ?: cachedBySignature["${file.name}|${file.sizeBytes}"]
                if (cached != null &&
                    cachedMatchesFile(cached, file) &&
                    cached.metadataVersion >= METADATA_VERSION
                ) {
                    val targetThumb = GalleryImageCache.cachedThumbnailPath(context, galleryKey, file.uri)
                    val resolvedTakenMillis = cached.takenMillis
                        .takeIf { it > 0L }
                        ?: file.takenMillis
                        ?: file.modifiedMillis
                    cached.copy(
                        uri = file.uri,
                        modifiedMillis = file.modifiedMillis,
                        fileSizeBytes = file.sizeBytes,
                        takenMillis = resolvedTakenMillis,
                        dateTaken = formatDate(resolvedTakenMillis),
                        thumbnailPath = targetThumb,
                        motionPhotoVideoLength = if (file.motionPhotoChecked) {
                            file.motionPhotoVideoLength
                        } else {
                            cached.motionPhotoVideoLength
                        },
                        motionPhotoPresentationTimestampUs =
                            if (file.motionPhotoChecked) {
                                file.motionPhotoPresentationTimestampUs
                            } else {
                                cached.motionPhotoPresentationTimestampUs
                            },
                        motionPhotoChecked =
                            file.motionPhotoChecked || cached.motionPhotoChecked,
                    )
                } else {
                    readPhoto(
                        context = context,
                        uri = file.uri,
                        name = file.name,
                        fallbackTime = file.takenMillis ?: file.modifiedMillis,
                        fileSizeBytes = file.sizeBytes,
                        modifiedMillis = file.modifiedMillis,
                        thumbnailPath = GalleryImageCache.cachedThumbnailPath(context, galleryKey, file.uri),
                        motionPhotoVideoLength = file.motionPhotoVideoLength,
                        motionPhotoPresentationTimestampUs =
                            file.motionPhotoPresentationTimestampUs,
                        motionPhotoChecked = file.motionPhotoChecked,
                    )
                }
            }
            photos.sortedWith(PHOTO_NEWEST_FIRST)
        }

    /** 未缓存照片时，单独扫描计算画廊概览（数量 / 时间跨度 / 封面）。 */
    suspend fun loadOverview(
        context: Context,
        gallery: Gallery,
        cachedPhotos: List<Photo> = emptyList(),
    ): GalleryOverview =
        withContext(Dispatchers.IO) {
            val files = scanFiles(context, gallery.uri)
            if (files.isEmpty()) return@withContext GalleryOverview(
                gallery = gallery,
                count = 0,
                timeSpan = "",
                coverUris = emptyList(),
                loading = false,
            )
            val datedFiles = sortFilesByCaptureTime(
                context = context,
                files = files,
                cachedPhotos = cachedPhotos,
            )
            val times = datedFiles.map { it.second }.sorted()
            val covers = datedFiles.take(COVER_COUNT).map { it.first.uri }
            val coverPaths = GalleryImageCache.buildCovers(context, gallery.uri.toString(), covers)
            GalleryOverview(
                gallery = gallery,
                count = files.size,
                timeSpan = formatSpan(times),
                coverUris = covers,
                postcardCoverPath = coverPaths.postcardPath,
                ticketCoverPath = coverPaths.ticketPath,
                ticketDominantColorArgb = coverPaths.ticketDominantColorArgb,
                ticketColorVersion = coverPaths.ticketColorVersion,
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
        val newestFirst = photos.sortedWith(PHOTO_NEWEST_FIRST)
        val span = formatSpan(newestFirst.map { it.takenMillis }.filter { it > 0 }.sorted())
        val covers = newestFirst.take(COVER_COUNT).map { it.uri }
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
            base.copy(
                postcardCoverPath = coverPaths.postcardPath,
                ticketCoverPath = coverPaths.ticketPath,
                ticketDominantColorArgb = coverPaths.ticketDominantColorArgb,
                ticketColorVersion = coverPaths.ticketColorVersion,
            )
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
            val mediaUri = uri.toMediaStoreUri(context)
            try {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && mediaUri != null -> {
                        if (MediaStore.canManageMedia(context)) {
                            val values = ContentValues().apply {
                                put(MediaStore.MediaColumns.IS_TRASHED, 1)
                            }
                            val movedToTrash = runCatching {
                                context.contentResolver.update(mediaUri, values, null, null)
                            }.getOrDefault(0) > 0
                            if (movedToTrash) {
                                DeleteResult.Deleted
                            } else {
                                DeleteResult.NeedsUserAction(
                                    MediaStore.createTrashRequest(
                                        context.contentResolver,
                                        listOf(mediaUri),
                                        true,
                                    ),
                                )
                            }
                        } else {
                            DeleteResult.NeedsUserAction(
                                MediaStore.createTrashRequest(
                                    context.contentResolver,
                                    listOf(mediaUri),
                                    true,
                                ),
                            )
                        }
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mediaUri != null -> {
                        val pending = MediaStore.createTrashRequest(
                            context.contentResolver,
                            listOf(mediaUri),
                            true,
                        )
                        DeleteResult.NeedsUserAction(pending)
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> DeleteResult.Failed
                    DocumentsContract.isDocumentUri(context, uri) ->
                        if (DocumentsContract.deleteDocument(context.contentResolver, uri)) DeleteResult.Deleted
                        else DeleteResult.Failed
                    deleteFromAuthorizedTree(context, galleryTreeUri, photo) -> DeleteResult.Deleted
                    DocumentFile.fromSingleUri(context, uri)?.delete() == true -> DeleteResult.Deleted
                    context.contentResolver.delete(uri, null, null) > 0 -> DeleteResult.Deleted
                    else -> DeleteResult.Failed
                }
            } catch (e: Exception) {
                DeleteResult.Failed
            }
        }

    /**
     * 大图页退出确认后的批量删除。现代 Android 优先合并为一次系统回收站请求；
     * 已获得媒体管理权限时直接逐项移入回收站，失败项再交给系统统一确认。
     */
    suspend fun deletePhotos(
        context: Context,
        galleryTreeUri: Uri,
        photos: List<Photo>,
    ): DeleteResult = withContext(Dispatchers.IO) {
        val uniquePhotos = photos.distinctBy { it.uri }
        if (uniquePhotos.isEmpty()) return@withContext DeleteResult.Deleted
        if (uniquePhotos.size == 1) {
            return@withContext deletePhoto(context, galleryTreeUri, uniquePhotos.first())
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return@withContext DeleteResult.Failed
        }

        val mediaUris = uniquePhotos.mapNotNull { it.uri.toMediaStoreUri(context) }.distinct()
        if (mediaUris.size != uniquePhotos.size) {
            return@withContext DeleteResult.Failed
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context)) {
                val failedUris = mediaUris.filterNot { mediaUri ->
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_TRASHED, 1)
                    }
                    runCatching {
                        context.contentResolver.update(mediaUri, values, null, null)
                    }.getOrDefault(0) > 0
                }
                if (failedUris.isEmpty()) {
                    DeleteResult.Deleted
                } else {
                    runCatching {
                        DeleteResult.NeedsUserAction(
                            pendingIntent = MediaStore.createTrashRequest(
                                context.contentResolver,
                                failedUris,
                                true,
                            ),
                            pendingUris = failedUris.toSet(),
                        )
                    }.getOrElse {
                        DeleteResult.PartialFailure(failedUris.toSet())
                    }
                }
            } else {
                DeleteResult.NeedsUserAction(
                    pendingIntent = MediaStore.createTrashRequest(
                        context.contentResolver,
                        mediaUris,
                        true,
                    ),
                    pendingUris = mediaUris.toSet(),
                )
            }
        } catch (_: Exception) {
            DeleteResult.Failed
        }
    }

    suspend fun movePhoto(
        context: Context,
        photo: Photo,
        destination: SystemAlbum,
    ): MoveResult =
        withContext(Dispatchers.IO) {
            val destPath = normalizeRelativePath(destination.relativePath)
            if (destPath.isBlank()) return@withContext MoveResult.Failed
            val mediaUri = photo.uri.toMediaStoreUri(context) ?: return@withContext MoveResult.Failed
            try {
                val currentPath = queryRelativePath(context, mediaUri)
                if (currentPath != null &&
                    normalizeRelativePath(currentPath) == destPath
                ) {
                    return@withContext MoveResult.SameAlbum
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, destPath)
                }
                val updated = runCatching {
                    context.contentResolver.update(mediaUri, values, null, null)
                }.getOrDefault(0)
                when {
                    updated > 0 -> MoveResult.Moved
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                        MoveResult.NeedsUserAction(
                            MediaStore.createWriteRequest(context.contentResolver, listOf(mediaUri)),
                        )
                    else -> MoveResult.Failed
                }
            } catch (_: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    MoveResult.NeedsUserAction(
                        MediaStore.createWriteRequest(context.contentResolver, listOf(mediaUri)),
                    )
                } else {
                    MoveResult.Failed
                }
            } catch (_: Exception) {
                MoveResult.Failed
            }
        }

    private fun queryRelativePath(context: Context, mediaUri: Uri): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            context.contentResolver.query(
                mediaUri,
                arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(0)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun Uri.toMediaStoreUri(context: Context): Uri? = when {
        isMediaStoreUri(this) -> this
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            runCatching { MediaStore.getMediaUri(context, this) }.getOrNull()
        else -> null
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

    private suspend fun sortFilesByCaptureTime(
        context: Context,
        files: List<PhotoFile>,
        cachedPhotos: List<Photo>,
    ): List<Pair<PhotoFile, Long>> {
        val cachedByUri = cachedPhotos.associateBy { it.uri.toString() }
        val cachedBySignature = cachedPhotos.associateBy {
            "${it.name}|${it.fileSizeBytes}"
        }
        return coroutineScope {
            files.map { file ->
                async {
                    val cached = cachedByUri[file.uri.toString()]
                        ?: cachedBySignature["${file.name}|${file.sizeBytes}"]
                    val cachedTakenMillis = cached
                        ?.takeIf {
                            cachedMatchesFile(it, file) &&
                                it.metadataVersion >= METADATA_VERSION
                        }
                        ?.takenMillis
                        ?.takeIf { it > 0L }
                    val fallback = file.takenMillis ?: file.modifiedMillis
                    file to (
                        cachedTakenMillis
                            ?: exifReadSemaphore.withPermit {
                                readTakenMillis(context, file.uri, fallback)
                            }
                        )
                }
            }.awaitAll()
        }.sortedWith(
            compareByDescending<Pair<PhotoFile, Long>> { it.second }
                .thenByDescending { it.first.modifiedMillis }
                .thenBy { it.first.uri.toString() },
        )
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
        motionPhotoVideoLength: Long = 0L,
        motionPhotoPresentationTimestampUs: Long = 0L,
        motionPhotoChecked: Boolean = false,
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
        var resolvedMotionPhotoVideoLength = motionPhotoVideoLength
        var resolvedMotionPhotoPresentationTimestampUs =
            motionPhotoPresentationTimestampUs
        var resolvedMotionPhotoChecked = motionPhotoChecked

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
            motionPhotoVideoLength = resolvedMotionPhotoVideoLength,
            motionPhotoPresentationTimestampUs =
                resolvedMotionPhotoPresentationTimestampUs,
            motionPhotoChecked = resolvedMotionPhotoChecked,
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
        val motionPhotoVideoLength: Long = 0L,
        val motionPhotoPresentationTimestampUs: Long = 0L,
        val motionPhotoChecked: Boolean = false,
    )

    private fun cachedMatchesFile(cached: Photo, file: PhotoFile): Boolean {
        if (cached.fileSizeBytes != file.sizeBytes || cached.name != file.name) return false
        return if (cached.uri == file.uri) {
            cached.modifiedMillis == file.modifiedMillis
        } else {
            abs(cached.modifiedMillis - file.modifiedMillis) <= FILE_TIME_TOLERANCE_MILLIS
        }
    }

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
        if (SystemAlbum.isAlbumUri(treeUri)) {
            val bucketId = SystemAlbum.bucketId(treeUri) ?: return emptyList()
            return scanMediaStoreByBucket(context, bucketId)
        }
        val media = scanMediaStore(context, treeUri)
        // 完整媒体权限下，以 MediaStore 为唯一有效集合；其默认排除了系统回收站内容。
        if (hasFullMediaAccess(context)) return media

        val saf = scanSaf(context, treeUri)
        if (saf.isEmpty()) return media
        if (media.isEmpty()) return saf

        // SAF URI 是用户持久授权后的稳定身份；MediaStore 只用于补充拍摄时间。
        // 避免同一文件在两种 URI 之间切换，导致封面键每次启动都变化。
        val mediaByNameAndSize = media.groupBy { "${it.name}|${it.sizeBytes}" }
        return saf.map { safFile ->
            val matchedMedia = mediaByNameAndSize["${safFile.name}|${safFile.sizeBytes}"]
                ?.minByOrNull { abs(it.modifiedMillis - safFile.modifiedMillis) }
            safFile.copy(
                takenMillis = matchedMedia?.takenMillis,
                motionPhotoVideoLength = matchedMedia?.motionPhotoVideoLength ?: 0L,
                motionPhotoPresentationTimestampUs =
                    matchedMedia?.motionPhotoPresentationTimestampUs ?: 0L,
                motionPhotoChecked = matchedMedia?.motionPhotoChecked == true,
            )
        }
    }

    private fun scanMediaStore(context: Context, treeUri: Uri): List<PhotoFile> {
        val relativePrefix = treeUri.toMediaStoreRelativePath() ?: return emptyList()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
        return queryMediaStore(context, collection, selection, arrayOf(relativePrefix), true)
            ?: queryMediaStore(context, collection, selection, arrayOf(relativePrefix), false)
            ?: emptyList()
    }

    private fun scanMediaStoreByBucket(context: Context, bucketId: Long): List<PhotoFile> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val args = arrayOf(bucketId.toString())
        return queryMediaStore(context, collection, selection, args, true)
            ?: queryMediaStore(context, collection, selection, args, false)
            ?: emptyList()
    }

    private fun queryMediaStore(
        context: Context,
        collection: Uri,
        selection: String,
        selectionArgs: Array<String>,
        includeOplusMotion: Boolean,
    ): List<PhotoFile>? {
        val baseProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.RELATIVE_PATH,
        )
        val projection = if (includeOplusMotion) {
            baseProjection + arrayOf(
                OPLUS_IS_LIVE_PHOTO,
                OPLUS_VIDEO_SIZE,
                OPLUS_COVER_TIMESTAMP_US,
            )
        } else {
            baseProjection
        }
        val result = mutableListOf<PhotoFile>()
        return try {
            val cursor = context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC, " +
                    "${MediaStore.Images.Media.DATE_MODIFIED} DESC, " +
                    "${MediaStore.Images.Media._ID} DESC",
            ) ?: return null
            cursor.use {
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val takenIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val isLiveIndex = if (includeOplusMotion) {
                    cursor.getColumnIndexOrThrow(OPLUS_IS_LIVE_PHOTO)
                } else {
                    -1
                }
                val videoSizeIndex = if (includeOplusMotion) {
                    cursor.getColumnIndexOrThrow(OPLUS_VIDEO_SIZE)
                } else {
                    -1
                }
                val coverTimestampIndex = if (includeOplusMotion) {
                    cursor.getColumnIndexOrThrow(OPLUS_COVER_TIMESTAMP_US)
                } else {
                    -1
                }
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val taken = cursor.getLong(takenIndex).takeIf { it > 0L }
                    val isLivePhoto =
                        includeOplusMotion && cursor.getLong(isLiveIndex) == 1L
                    val videoLength = if (isLivePhoto) {
                        cursor.getLong(videoSizeIndex).coerceAtLeast(0L)
                    } else {
                        0L
                    }
                    result += PhotoFile(
                        uri = uri,
                        name = cursor.getString(nameIndex) ?: "未命名",
                        modifiedMillis = cursor.getLong(modifiedIndex) * 1000L,
                        sizeBytes = cursor.getLong(sizeIndex),
                        takenMillis = taken,
                        motionPhotoVideoLength = videoLength,
                        motionPhotoPresentationTimestampUs = if (isLivePhoto) {
                            cursor.getLong(coverTimestampIndex).coerceAtLeast(0L)
                        } else {
                            0L
                        },
                        motionPhotoChecked =
                            includeOplusMotion && (!isLivePhoto || videoLength > 0L),
                    )
                }
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    private fun hasFullMediaAccess(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES,
            ) == PackageManager.PERMISSION_GRANTED

    private fun Uri.toMediaStoreRelativePath(): String? {
        if (SystemAlbum.isAlbumUri(this)) {
            val path = getQueryParameter("path")?.trim('/') ?: return null
            return if (path.isBlank()) null else "$path/"
        }
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

    private fun isDcimOrPicturesPath(relativePath: String): Boolean {
        val path = relativePath.replace('\\', '/').trimStart('/')
        return path.startsWith("DCIM/", ignoreCase = true) ||
            path.equals("DCIM", ignoreCase = true) ||
            path.startsWith("Pictures/", ignoreCase = true) ||
            path.equals("Pictures", ignoreCase = true)
    }

    private fun normalizeRelativePath(relativePath: String): String {
        val path = relativePath.replace('\\', '/').trim('/')
        return if (path.isBlank()) "" else "$path/"
    }

    private fun folderNameFromRelativePath(relativePath: String): String {
        val trimmed = relativePath.replace('\\', '/').trim('/')
        return trimmed.substringAfterLast('/').ifBlank { trimmed.ifBlank { "未命名图集" } }
    }

    private fun relativePathFromData(dataPath: String?): String? {
        if (dataPath.isNullOrBlank()) return null
        val normalized = dataPath.replace('\\', '/')
        val markers = listOf("/DCIM/", "/Pictures/")
        val marker = markers.firstOrNull { marker ->
            normalized.contains(marker, ignoreCase = true)
        } ?: return null
        val index = normalized.indexOf(marker, ignoreCase = true)
        val fromRoot = normalized.substring(index + 1)
        val folder = fromRoot.substringBeforeLast('/', missingDelimiterValue = fromRoot)
        return if (folder.endsWith('/')) folder else "$folder/"
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

    private val PHOTO_NEWEST_FIRST =
        compareByDescending<Photo> { it.takenMillis }
            .thenByDescending { it.modifiedMillis }
            .thenBy { it.uri.toString() }

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
    private const val METADATA_VERSION = 3
    private const val FILE_TIME_TOLERANCE_MILLIS = 1_500L
    private const val OPLUS_IS_LIVE_PHOTO = "o_is_live_photo"
    private const val OPLUS_VIDEO_SIZE = "o_video_size"
    private const val OPLUS_COVER_TIMESTAMP_US = "o_cover_time_stamps"
}
