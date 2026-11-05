package com.punctum.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import coil.size.Scale
import com.punctum.gallery.model.Photo
import com.punctum.gallery.model.detailDecodeSize
import com.punctum.gallery.model.detailWarmIndices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One revision-aware base-image path for ordinary detail, film, entry and prefetch.
 * Only two decodes run at once. A visible request promotes the shared task ahead of
 * queued neighbors; updating the window cancels obsolete work without canceling a viewer.
 * Bitmaps live in Coil's existing byte-bounded memory cache, not another unbounded map.
 */
internal object DetailImagePipeline {
    private const val TAG = "PunctumDetail"
    private const val CONCURRENCY = 2
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val flights = linkedMapOf<String, Flight>()
    private var active = 0
    private var wanted = emptyMap<String, Desired>()
    private var visibleJob: Job? = null
    private var visibleGeneration = 0L
    private var detailWindow = false
    private var windowCenter: Int? = null

    private data class Desired(val context: Context, val photo: Photo, val rank: Int, val index: Int)
    private class Flight(val key: String, val context: Context, val photo: Photo, var rank: Int) {
        val result = CompletableDeferred<Bitmap?>()
        var viewers = 0
        var job: Job? = null
    }

    fun cacheKey(context: Context, photo: Photo): String {
        val metrics = context.resources.displayMetrics
        val size = detailDecodeSize(photo.width, photo.height, metrics.widthPixels, metrics.heightPixels)
        return "detail-native-v1:${photo.uri}:${photo.modifiedMillis}:${photo.fileSizeBytes}:${photo.stillImageByteCount ?: 0L}:${size.width}x${size.height}"
    }

    fun request(context: Context, photo: Photo): ImageRequest {
        val metrics = context.resources.displayMetrics
        val size = detailDecodeSize(photo.width, photo.height, metrics.widthPixels, metrics.heightPixels)
        return ImageRequest.Builder(context)
            .data(PhotoStill.forDetail(photo))
            .memoryCacheKey(cacheKey(context, photo))
            .diskCacheKey("detail-source-v1:${photo.uri}:${photo.modifiedMillis}:${photo.fileSizeBytes}:${photo.stillImageByteCount ?: 0L}")
            .size(size.width, size.height)
            .scale(Scale.FIT)
            .precision(Precision.EXACT)
            // The base and region layers use the same color space/configuration.
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .colorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            .allowHardware(false)
            .crossfade(false)
            .build()
    }

    fun cachedBitmap(context: Context, photo: Photo): Bitmap? =
        Coil.imageLoader(context).memoryCache?.get(MemoryCache.Key(cacheKey(context, photo)))?.bitmap
            ?.takeUnless { it.isRecycled }

    suspend fun load(context: Context, photo: Photo): Bitmap? {
        cachedBitmap(context, photo)?.let {
            if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "base-hit ${it.width}x${it.height}")
            return it
        }
        val task = synchronized(lock) {
            val key = cacheKey(context, photo)
            val flight = flights[key] ?: Flight(key, context.applicationContext, photo, 0).also { flights[key] = it }
            flight.viewers++
            flight.rank = 0
            dispatchLocked()
            flight
        }
        return try {
            task.result.await()
        } finally {
            synchronized(lock) {
                task.viewers--
                if (task.viewers == 0) {
                    wanted[task.key]?.let { task.rank = it.rank }
                    if (task.key !in wanted && !task.result.isCompleted) cancelLocked(task)
                }
            }
        }
    }

    fun warm(context: Context, photos: List<Photo>, centerIndex: Int, radius: Int = 5) {
        val app = context.applicationContext
        val desired = detailWarmIndices(photos.size, centerIndex, radius).mapIndexed { rank, index ->
            val photo = photos[index]
            cacheKey(app, photo) to Desired(app, photo, 10 + rank, index)
        }.toMap()
        synchronized(lock) {
            visibleGeneration++
            visibleJob?.cancel()
            visibleJob = null
            detailWindow = true
            windowCenter = centerIndex
            replaceWindowLocked(desired)
            traceWindowLocked()
        }
    }

    /** Call only after list scrolling has stopped. Fast list scrolling uses pauseVisible(). */
    fun warmVisible(context: Context, photos: List<Photo>, first: Int, last: Int) {
        if (photos.isEmpty()) return
        val app = context.applicationContext
        val start = first.coerceIn(0, photos.lastIndex)
        val end = last.coerceIn(start, photos.lastIndex)
        // At most twelve photos: visible cells first, then one adjacent candidate each way.
        val indices = ((start..end).toList() + listOf(end + 1, start - 1))
            .filter { it in photos.indices }.distinct().take(12)
        synchronized(lock) {
            // Film's table remains mounted while projecting. It must not replace the
            // selected image's five-neighbor detail window with a lower-priority list.
            if (detailWindow) return
            visibleGeneration++
            val generation = visibleGeneration
            visibleJob?.cancel()
            visibleJob = scope.launch {
                delay(160)
                val desired = indices.mapIndexed { rank, index ->
                    val photo = photos[index]
                    cacheKey(app, photo) to Desired(app, photo, 100 + rank, index)
                }.toMap()
                synchronized(lock) {
                    if (generation == visibleGeneration && !detailWindow) {
                        windowCenter = null
                        replaceWindowLocked(desired)
                    }
                }
            }
        }
    }

    /** Leaves a selected detail window and every actively displayed image untouched. */
    fun pauseVisible() = synchronized(lock) {
        visibleGeneration++
        visibleJob?.cancel()
        visibleJob = null
        if (!detailWindow) replaceWindowLocked(emptyMap())
    }

    /** Call when leaving the gallery or losing foreground; existing displayed bitmaps stay valid. */
    fun pause() = synchronized(lock) {
        visibleGeneration++
        visibleJob?.cancel()
        visibleJob = null
        detailWindow = false
        windowCenter = null
        replaceWindowLocked(emptyMap())
    }

    private fun replaceWindowLocked(next: Map<String, Desired>) {
        wanted = next
        flights.values.toList().forEach { flight ->
            val desired = next[flight.key]
            if (desired == null && flight.viewers == 0) cancelLocked(flight)
            else if (flight.viewers == 0 && desired != null) flight.rank = desired.rank
        }
        next.forEach { (key, desired) ->
            if (key !in flights && cachedBitmap(desired.context, desired.photo) == null) {
                flights[key] = Flight(key, desired.context, desired.photo, desired.rank)
            }
        }
        dispatchLocked()
    }

    private fun cancelLocked(flight: Flight) {
        if (flights[flight.key] === flight) flights.remove(flight.key)
        flight.result.cancel()
        flight.job?.cancel()
    }

    private fun traceWindowLocked() {
        val center = windowCenter ?: return
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        val ready = wanted.filter { (_, desired) -> cachedBitmap(desired.context, desired.photo) != null }
        val left = wanted.count { it.value.index < center }
        val right = wanted.count { it.value.index > center }
        Log.d(TAG, "window center=$center ready=${ready.size}/${wanted.size} " +
            "left=${ready.count { it.value.index < center }}/$left " +
            "right=${ready.count { it.value.index > center }}/$right")
    }

    /** Called with lock held; completed workers free a slot and pick the newest highest priority. */
    private fun dispatchLocked() {
        while (active < CONCURRENCY) {
            val flight = flights.values.filter { it.job == null && !it.result.isCompleted }.minByOrNull { it.rank } ?: return
            active++
            val job = scope.launch(start = CoroutineStart.LAZY) {
                val start = android.os.SystemClock.uptimeMillis()
                try {
                    val bitmap = cachedBitmap(flight.context, flight.photo) ?: run {
                        val result = Coil.imageLoader(flight.context).execute(request(flight.context, flight.photo))
                        (result as? SuccessResult)?.drawable?.toBitmap()
                    }
                    bitmap?.prepareToDraw()
                    flight.result.complete(bitmap)
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "base-ready ${bitmap?.width}x${bitmap?.height} ms=${android.os.SystemClock.uptimeMillis() - start} rank=${flight.rank}")
                    }
                } catch (cancelled: CancellationException) {
                    flight.result.cancel(cancelled)
                } catch (error: Exception) {
                    flight.result.complete(null)
                    if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "base-failed ${error.javaClass.simpleName}")
                }
            }
            flight.job = job
            // Completion also runs when cancellation happens before the lazy body starts;
            // a finally inside that body would otherwise leak one of the two decode slots.
            job.invokeOnCompletion { cause ->
                if (cause != null) flight.result.cancel()
                synchronized(lock) {
                    active--
                    if (flights[flight.key] === flight) flights.remove(flight.key)
                    traceWindowLocked()
                    dispatchLocked()
                }
            }
            job.start()
        }
    }
}
