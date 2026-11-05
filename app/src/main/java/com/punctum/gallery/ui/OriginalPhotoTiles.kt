package com.punctum.gallery.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.exifinterface.media.ExifInterface
import com.punctum.gallery.data.openStillStream
import com.punctum.gallery.model.OriginalOrientation
import com.punctum.gallery.model.OriginalPixelRect
import com.punctum.gallery.model.OriginalTileKey
import com.punctum.gallery.model.Photo
import com.punctum.gallery.model.PhotoZoomState
import com.punctum.gallery.model.originalTileRequestPlan
import com.punctum.gallery.model.originalTileSample
import com.punctum.gallery.model.originalTilesForWindow
import com.punctum.gallery.model.originalVisibleRect
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.Closeable
import java.io.InputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private const val ORIGINAL_LOG_TAG = "PunctumOriginal"
private val originalDecodeSlots = Semaphore(2)

private fun traceOriginal(message: String) {
    if (Log.isLoggable(ORIGINAL_LOG_TAG, Log.DEBUG)) Log.d(ORIGINAL_LOG_TAG, message)
}

private data class OriginalTile(
    val key: OriginalTileKey,
    val rect: OriginalPixelRect,
    val bitmap: Bitmap,
) {
    val bytes: Long get() = bitmap.allocationByteCount.toLong()
}

private class OriginalTileDrawing {
    // Read in Canvas' draw phase. A completed tile invalidates this overlay, not the parent's glow/layout.
    var tiles by mutableStateOf<List<OriginalTile>>(emptyList())
    var generation = 0L
    var width = 1
    var height = 1
}

private data class OriginalLayoutStamp(val size: IntSize, val origin: Offset, val xEdge: Offset, val yEdge: Offset)

private data class OriginalTileWindow(
    val visible: OriginalPixelRect,
    val sample: Int,
    val tiles: List<OriginalTileKey>,
)

/**
 * Transparent original-pixel overlay. Keep the screen-sized base underneath at all times.
 * Mount in the same image-only transform as the base; viewport is the owning page/projection's
 * window rectangle, so the table, surrounding paper and text never become part of the image.
 */
@Composable
internal fun OriginalPhotoTiles(
    photo: Photo,
    pose: () -> PhotoZoomState,
    viewportInWindow: () -> Rect,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    baseBitmap: Bitmap? = null,
) {
    val context = LocalContext.current.applicationContext
    val identity = "${photo.uri}:${photo.modifiedMillis}:${photo.fileSizeBytes}:${photo.stillImageByteCount}"
    val drawing = remember(identity) { OriginalTileDrawing() }
    var coordinates by remember(identity) { mutableStateOf<LayoutCoordinates?>(null) }
    var layoutSize by remember(identity) { mutableStateOf(IntSize.Zero) }
    var positionVersion by remember(identity) { mutableIntStateOf(0) }
    val currentPose by rememberUpdatedState(pose)
    val currentViewport by rememberUpdatedState(viewportInWindow)
    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG) }
    val lastGeometry = remember(identity) { arrayOfNulls<OriginalLayoutStamp>(1) }

    val currentBase by rememberUpdatedState(baseBitmap)
    val originalNeeded by remember(identity) { derivedStateOf {
        val zoom = currentPose().scale
        val base = currentBase
        val measured = layoutSize
        if (base == null || measured.width <= 0 || measured.height <= 0) false
        else {
            // The parent photo layer applies zoom; its other reveal/info transforms only shrink.
            // No window-coordinate queries in composition on the quick-cut path.
            val fit = originalFitRect(base.width, base.height, measured.width.toFloat(), measured.height.toFloat())
            base.width + 2f < fit.width * zoom || base.height + 2f < fit.height * zoom
        }
    } }
    // At rest, the native-sized base already contains the required screen pixels.
    // Additional original regions start only when the displayed pixel density needs them.
    val loadOriginal = enabled && originalNeeded
    LaunchedEffect(identity, loadOriginal) {
        val generation = ++drawing.generation
        drawing.tiles = emptyList()
        if (!loadOriginal) return@LaunchedEffect
        var source: OriginalTileSource? = null
        val jobs = mutableMapOf<OriginalTileKey, Job>()
        val cache = LinkedHashMap<OriginalTileKey, OriginalTile>(32, .75f, true)
        var wanted = emptySet<OriginalTileKey>()
        var visible: OriginalPixelRect? = null
        var bytes = 0L
        var budgetBytes = 32L * 1024L * 1024L
        var pinned = emptySet<OriginalTileKey>()
        var readyWindow: Set<OriginalTileKey>? = null

        fun publish() {
            if (drawing.generation != generation) return
            // Keep guard/fallback tiles recorded too. Panning across an already prepared tile then
            // changes only the parent's layer matrix, rather than rebuilding this display list.
            drawing.tiles = cache.values.sortedWith(compareByDescending<OriginalTile> { it.key.sample }
                .thenBy { it.rect.top }.thenBy { it.rect.left })
            val currentVisible = visible ?: return
            val required = wanted.filter { it.rect.intersects(currentVisible) }.toSet()
            if (required.isNotEmpty() && required != readyWindow && required.all(cache::containsKey)) {
                readyWindow = required
                traceOriginal("ready id=${identity.hashCode()} sample=${required.first().sample}" +
                    " visibleTiles=${required.size} cacheBytes=$bytes zoom=${currentPose().scale}")
            }
        }

        fun trim() {
            while (bytes > budgetBytes && cache.isNotEmpty()) {
                // Pin every visible tile at the current sample. Older sampling layers and guard
                // tiles can be evicted; a static view must never lose its already decoded center.
                val key = cache.entries.firstOrNull { it.key !in pinned }?.key ?: break
                bytes -= cache.remove(key)?.bytes ?: 0L
                // No recycle: a previous draw/snapshot may still reference this bitmap.
            }
        }

        try {
            withContext(Dispatchers.IO) {
                originalDecodeSlots.withPermit {
                    ensureActive()
                    source = openOriginalTileSource(context, photo)
                }
            }
            val opened = source ?: return@LaunchedEffect
            drawing.width = opened.orientation.width
            drawing.height = opened.orientation.height
            coroutineScope {
                snapshotFlow {
                    // Observe pose/layout/viewport changes without reading animation state in composition.
                    currentPose()
                    positionVersion
                    val bounds = currentViewport()
                    val coords = coordinates
                    val measured = layoutSize
                    if (coords == null || !coords.isAttached || measured.width <= 0 || measured.height <= 0 ||
                        bounds.width <= 0f || bounds.height <= 0f
                    ) null else runCatching {
                        val corners = listOf(bounds.topLeft, bounds.topRight, bounds.bottomLeft, bounds.bottomRight)
                            .map(coords::windowToLocal)
                        val fit = originalFitRect(drawing.width, drawing.height, measured.width.toFloat(), measured.height.toFloat())
                        val left = corners.minOf { it.x }.coerceAtLeast(fit.left)
                        val top = corners.minOf { it.y }.coerceAtLeast(fit.top)
                        val right = corners.maxOf { it.x }.coerceAtMost(fit.right)
                        val bottom = corners.maxOf { it.y }.coerceAtMost(fit.bottom)
                        if (right <= left || bottom <= top) null else {
                            val original = originalVisibleRect((left - fit.left) / fit.width * drawing.width,
                                (top - fit.top) / fit.height * drawing.height, (right - fit.left) / fit.width * drawing.width,
                                (bottom - fit.top) / fit.height * drawing.height, drawing.width, drawing.height)
                            val origin = coords.localToWindow(fit.topLeft)
                            val xEdge = coords.localToWindow(fit.topRight) - origin
                            val yEdge = coords.localToWindow(fit.bottomLeft) - origin
                            val displayedWidth = hypot(xEdge.x, xEdge.y).coerceAtLeast(1f)
                            val displayedHeight = hypot(yEdge.x, yEdge.y).coerceAtLeast(1f)
                            val sample = originalTileSample(min(drawing.width / displayedWidth, drawing.height / displayedHeight))
                            original?.let { OriginalTileWindow(it, sample,
                                originalTilesForWindow(it, drawing.width, drawing.height, sample)) }
                        }
                    }.getOrNull()
                }.distinctUntilChanged { previous, next ->
                    if (previous == null || next == null) previous == next
                    else previous.sample == next.sample && previous.tiles.toSet() == next.tiles.toSet() &&
                        previous.tiles.filter { it.rect.intersects(previous.visible) }.toSet() ==
                        next.tiles.filter { it.rect.intersects(next.visible) }.toSet()
                }.collect { window ->
                    if (window == null) {
                        wanted = emptySet()
                        jobs.values.toList().forEach { it.cancel() }
                        jobs.clear()
                        return@collect
                    }
                    visible = window.visible
                    val requests = originalTileRequestPlan(window.visible, window.tiles)
                    pinned = requests.visibleKeys
                    wanted = requests.requests.toSet()
                    budgetBytes = requests.budgetBytes
                    trim()
                    traceOriginal("window id=${identity.hashCode()} sample=${window.sample}" +
                        " visible=${window.visible.left},${window.visible.top},${window.visible.right},${window.visible.bottom}" +
                        " tiles=${requests.requests.size} pinned=${pinned.size} budgetBytes=$budgetBytes zoom=${currentPose().scale}")
                    jobs.keys.toList().filterNot { it in wanted }.forEach { jobs.remove(it)?.cancel() }
                    requests.requests.forEach { key ->
                        if (cache[key] != null || jobs[key]?.isActive == true) return@forEach
                        val job = launch(start = CoroutineStart.LAZY) {
                            val ownJob = currentCoroutineContext().job
                            try {
                                val decoded = withContext(Dispatchers.IO) {
                                    originalDecodeSlots.withPermit {
                                        ensureActive()
                                        opened.decode(key)
                                    }
                                } ?: return@launch
                                ensureActive()
                                if (key !in wanted) return@launch
                                val previous = cache.put(key, decoded)
                                bytes += decoded.bytes - (previous?.bytes ?: 0L)
                                trim()
                                publish()
                            } finally {
                                if (jobs[key] === ownJob) jobs.remove(key)
                            }
                        }
                        jobs[key] = job
                        job.start()
                    }
                    publish()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            traceOriginal("tiles-unavailable id=${identity.hashCode()} error=${error.javaClass.simpleName}")
        } catch (memory: OutOfMemoryError) {
            // A provider/native decoder may allocate outside our tile budget; retain the base.
            traceOriginal("tiles-unavailable id=${identity.hashCode()} error=OutOfMemoryError")
        } finally {
            withContext(NonCancellable) {
                jobs.values.toList().forEach { it.cancelAndJoin() }
                withContext(Dispatchers.IO) { source?.close() }
            }
            cache.clear()
            if (drawing.generation == generation) drawing.tiles = emptyList()
        }
    }

    Canvas(modifier.clipToBounds().onGloballyPositioned {
        coordinates = it
        layoutSize = it.size
        val stamp = OriginalLayoutStamp(it.size, it.localToWindow(Offset.Zero),
            it.localToWindow(Offset(it.size.width.toFloat(), 0f)),
            it.localToWindow(Offset(0f, it.size.height.toFloat())))
        if (lastGeometry[0] != stamp) {
            lastGeometry[0] = stamp
            positionVersion++
        }
    }) {
        val tiles = drawing.tiles
        // Every destination pixel is drawn once: base OR its best available tile.
        // This also preserves alpha PNG/WebP without an extra full-photo offscreen buffer.
        drawIntoCanvas { canvas ->
            val sourceFit = originalFitRect(drawing.width, drawing.height, size.width, size.height)
            fun target(rect: OriginalPixelRect) = RectF(
                sourceFit.left + rect.left.toFloat() / drawing.width * sourceFit.width,
                sourceFit.top + rect.top.toFloat() / drawing.height * sourceFit.height,
                sourceFit.left + rect.right.toFloat() / drawing.width * sourceFit.width,
                sourceFit.top + rect.bottom.toFloat() / drawing.height * sourceFit.height)
            val native = canvas.nativeCanvas
            val cores = tiles.map { target(it.key.rect) }
            baseBitmap?.let { base ->
                native.save()
                cores.forEach { native.clipOutRect(it) }
                val fit = originalFitRect(base.width, base.height, size.width, size.height)
                native.drawBitmap(base, null, RectF(fit.left, fit.top, fit.right, fit.bottom), paint)
                native.restore()
            }
            for ((index, tile) in tiles.withIndex()) {
                native.save()
                native.clipRect(cores[index])
                for (next in index + 1 until tiles.size) {
                    if (RectF.intersects(cores[index], cores[next])) native.clipOutRect(cores[next])
                }
                native.drawBitmap(tile.bitmap, null, target(tile.rect), paint)
                native.restore()
            }
        }
    }
}

/** Native decoder access/close share one lock; cancellation can reject results but cannot interrupt native decode. */
private class OriginalTileSource(
    private val decoder: BitmapRegionDecoder,
    val orientation: OriginalOrientation,
    private val resource: Closeable?,
    private val logID: Int,
) : Closeable {
    private val lock = Any()
    private var closed = false

    fun decode(key: OriginalTileKey): OriginalTile? = synchronized(lock) {
        if (closed) return@synchronized null
        val start = SystemClock.elapsedRealtime()
        // One sampled pixel overlap prevents filtering seams at neighboring tile edges.
        val core = key.rect
        val overlap = key.sample
        val rect = OriginalPixelRect(max(0, core.left - overlap), max(0, core.top - overlap),
            min(orientation.width, core.right + overlap), min(orientation.height, core.bottom + overlap))
        val encoded = orientation.encodedRect(rect)
        if (encoded.width <= 0 || encoded.height <= 0) return@synchronized null
        val options = BitmapFactory.Options().apply {
            inSampleSize = key.sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        val raw = decoder.decodeRegion(AndroidRect(encoded.left, encoded.top, encoded.right, encoded.bottom), options)
            ?: return@synchronized null
        val oriented = orientOriginalBitmap(raw, orientation.exif)
        if (oriented !== raw) raw.recycle()
        oriented.prepareToDraw()
        traceOriginal("tile id=$logID sample=${key.sample} rect=${rect.left},${rect.top},${rect.right},${rect.bottom}" +
            " decoded=${oriented.width}x${oriented.height} ms=${SystemClock.elapsedRealtime() - start}")
        OriginalTile(key, rect, oriented)
    }

    override fun close() = synchronized(lock) {
        if (!closed) {
            closed = true
            decoder.recycle()
            runCatching { resource?.close() }
        }
    }
}

@Suppress("DEPRECATION")
private fun openOriginalTileSource(context: Context, photo: Photo): OriginalTileSource? {
    val id = "${photo.uri}:${photo.modifiedMillis}:${photo.fileSizeBytes}".hashCode()
    val started = SystemClock.elapsedRealtime()
    val orientation = runCatching {
        openStillStream(context, photo.uri, photo.stillImageByteCount)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    var resource: Closeable? = null
    try {
        val decoder = if (photo.stillImageByteCount != null) {
            // Motion JPEG only: never feed its trailing MP4 to this decoder.
            val stream: InputStream = openStillStream(context, photo.uri, photo.stillImageByteCount) ?: return null
            resource = stream
            BitmapRegionDecoder.newInstance(stream, false)
        } else {
            var descriptor: ParcelFileDescriptor? = null
            try {
                descriptor = context.contentResolver.openFileDescriptor(photo.uri, "r")
                resource = descriptor
                descriptor?.let { BitmapRegionDecoder.newInstance(it.fileDescriptor, false) }
                    ?: throw IllegalStateException("No seekable descriptor")
            } catch (error: Exception) {
                runCatching { descriptor?.close() }
                val stream = openStillStream(context, photo.uri, null) ?: return null
                resource = stream
                BitmapRegionDecoder.newInstance(stream, false)
            }
        }
        if (decoder == null) { resource?.close(); return null }
        val sourceOrientation = OriginalOrientation(decoder.width, decoder.height, orientation)
        traceOriginal("source id=$id encoded=${decoder.width}x${decoder.height} exif=$orientation" +
            " oriented=${sourceOrientation.width}x${sourceOrientation.height} ms=${SystemClock.elapsedRealtime() - started}")
        return OriginalTileSource(decoder, sourceOrientation, resource, id)
    } catch (error: Exception) {
        runCatching { resource?.close() }
        traceOriginal("source-unavailable id=$id error=${error.javaClass.simpleName}")
        return null
    }
}

private fun orientOriginalBitmap(bitmap: Bitmap, exif: Int): Bitmap {
    if (exif !in 2..8) return bitmap
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val values = when (exif) {
        2 -> floatArrayOf(-1f, 0f, w, 0f, 1f, 0f, 0f, 0f, 1f)
        3 -> floatArrayOf(-1f, 0f, w, 0f, -1f, h, 0f, 0f, 1f)
        4 -> floatArrayOf(1f, 0f, 0f, 0f, -1f, h, 0f, 0f, 1f)
        5 -> floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        6 -> floatArrayOf(0f, -1f, h, 1f, 0f, 0f, 0f, 0f, 1f)
        7 -> floatArrayOf(0f, -1f, h, -1f, 0f, w, 0f, 0f, 1f)
        else -> floatArrayOf(0f, 1f, 0f, -1f, 0f, w, 0f, 0f, 1f)
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
        Matrix().apply { setValues(values) }, true)
}

private fun originalFitRect(sourceWidth: Int, sourceHeight: Int, width: Float, height: Float): Rect {
    val scale = min(width / sourceWidth.coerceAtLeast(1), height / sourceHeight.coerceAtLeast(1))
    val w = sourceWidth * scale
    val h = sourceHeight * scale
    return Rect((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
}
