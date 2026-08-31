package com.punctum.gallery.data

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import com.punctum.gallery.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** 将 Motion Photo 尾部内嵌的 MP4 按需提取到应用私有缓存。 */
object MotionPhotoService {

    internal data class Metadata(
        val videoLength: Long,
        val presentationTimestampUs: Long,
    )

    suspend fun resolvePhoto(context: Context, photo: Photo): Photo =
        withContext(Dispatchers.IO) {
            if (photo.motionPhotoChecked) return@withContext photo
            val mediaStoreLookup = queryOplusMetadata(context, photo)
            val metadata = if (mediaStoreLookup != null) {
                mediaStoreLookup.metadata
            } else {
                runCatching {
                    context.contentResolver.openInputStream(photo.uri)?.use { input ->
                        metadata(ExifInterface(input))
                    }
                }.getOrNull()
            }
            photo.copy(
                motionPhotoVideoLength = metadata?.videoLength ?: 0L,
                motionPhotoPresentationTimestampUs =
                    metadata?.presentationTimestampUs ?: 0L,
                motionPhotoChecked = true,
            )
        }

    private data class MetadataLookup(val metadata: Metadata?)

    private fun queryOplusMetadata(context: Context, photo: Photo): MetadataLookup? =
        runCatching {
            context.contentResolver.query(
                photo.uri,
                arrayOf(
                    OPLUS_IS_LIVE_PHOTO,
                    OPLUS_VIDEO_SIZE,
                    OPLUS_COVER_TIMESTAMP_US,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val isLivePhoto =
                    cursor.getLong(cursor.getColumnIndexOrThrow(OPLUS_IS_LIVE_PHOTO)) == 1L
                if (!isLivePhoto) return@use MetadataLookup(null)
                val videoLength =
                    cursor.getLong(cursor.getColumnIndexOrThrow(OPLUS_VIDEO_SIZE))
                if (videoLength <= 0L) return@use null
                MetadataLookup(
                    Metadata(
                        videoLength = videoLength,
                        presentationTimestampUs = cursor.getLong(
                            cursor.getColumnIndexOrThrow(OPLUS_COVER_TIMESTAMP_US),
                        ).coerceAtLeast(0L),
                    ),
                )
            }
        }.getOrNull()

    internal fun metadata(exif: ExifInterface): Metadata? {
        val xmp = exif.getAttributeBytes(ExifInterface.TAG_XMP)
            ?.toString(Charsets.UTF_8)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val isMotionPhoto =
            Regex("""GCamera:(?:MotionPhoto|MicroVideo)\s*=\s*["']1["']""")
                .containsMatchIn(xmp)
        if (!isMotionPhoto) return null

        val motionItem = Regex("""<Container:Item\b[^>]*>""")
            .findAll(xmp)
            .map { it.value }
            .firstOrNull {
                Regex("""Item:Semantic\s*=\s*["']MotionPhoto["']""").containsMatchIn(it)
            }
        val containerVideoLength = motionItem
            ?.let {
                Regex("""Item:Length\s*=\s*["'](\d+)["']""")
                    .find(it)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toLongOrNull()
            }
        val legacyVideoLength = Regex("""GCamera:MicroVideoOffset\s*=\s*["'](\d+)["']""")
            .find(xmp)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
        val videoLength = containerVideoLength ?: legacyVideoLength ?: return null
        if (videoLength <= 0L) return null

        val presentationTimestampUs = Regex(
            """GCamera:(?:MotionPhotoPresentationTimestampUs|MicroVideoPresentationTimestampUs)\s*=\s*["'](\d+)["']""",
        ).find(xmp)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: 0L
        return Metadata(videoLength, presentationTimestampUs)
    }

    suspend fun videoFile(context: Context, photo: Photo): File? =
        withContext(Dispatchers.IO) {
            val videoLength = photo.motionPhotoVideoLength
            if (videoLength <= 0L) return@withContext null

            val cacheDir = File(context.cacheDir, "motion_photos").apply { mkdirs() }
            val cacheKey = buildString {
                append(photo.uri.toString().hashCode().toUInt().toString(16))
                append('_')
                append(photo.modifiedMillis)
                append('_')
                append(videoLength)
            }
            val target = File(cacheDir, "$cacheKey.mp4")
            if (target.length() == videoLength && containsFtyp(target)) {
                target.setLastModified(System.currentTimeMillis())
                return@withContext target
            }

            val temporary = File(cacheDir, "$cacheKey.tmp")
            temporary.delete()
            val extracted = runCatching {
                context.contentResolver.openFileDescriptor(photo.uri, "r")?.use { descriptor ->
                    val sourceLength = descriptor.statSize
                        .takeIf { it > 0L }
                        ?: photo.fileSizeBytes.takeIf { it > 0L }
                        ?: return@use false
                    val videoOffset = sourceLength - videoLength
                    if (videoOffset < 0L) return@use false

                    FileInputStream(descriptor.fileDescriptor).use { input ->
                        input.channel.position(videoOffset)
                        FileOutputStream(temporary).use { output ->
                            var remaining = videoLength
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                            while (remaining > 0L) {
                                val count = input.read(
                                    buffer,
                                    0,
                                    minOf(buffer.size.toLong(), remaining).toInt(),
                                )
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                remaining -= count
                            }
                            output.fd.sync()
                            remaining == 0L
                        }
                    }
                } == true
            }.getOrDefault(false)

            if (!extracted || temporary.length() != videoLength || !containsFtyp(temporary)) {
                temporary.delete()
                return@withContext null
            }
            target.delete()
            if (!temporary.renameTo(target)) {
                temporary.delete()
                return@withContext null
            }
            trimCache(cacheDir, target)
            target
        }

    private fun containsFtyp(file: File): Boolean =
        runCatching {
            FileInputStream(file).use { input ->
                val prefix = ByteArray(32)
                val count = input.read(prefix)
                if (count <= 0) return@use false
                String(prefix, 0, count, Charsets.ISO_8859_1).contains("ftyp")
            }
        }.getOrDefault(false)

    private fun trimCache(cacheDir: File, keep: File) {
        val files = cacheDir.listFiles()
            ?.filter { it.extension == "mp4" }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        var retainedBytes = 0L
        files.forEach { file ->
            retainedBytes += file.length()
            if (file != keep && retainedBytes > MAX_CACHE_BYTES) file.delete()
        }
    }

    private const val MAX_CACHE_BYTES = 256L * 1024L * 1024L
    private const val OPLUS_IS_LIVE_PHOTO = "o_is_live_photo"
    private const val OPLUS_VIDEO_SIZE = "o_video_size"
    private const val OPLUS_COVER_TIMESTAMP_US = "o_cover_time_stamps"
}
