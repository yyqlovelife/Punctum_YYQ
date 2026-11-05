package com.punctum.gallery.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.punctum.gallery.model.Photo
import com.punctum.gallery.model.filmProjectionFrame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Small pre-rendered projection light; the photo itself always uses the original-detail path. */
internal object FilmGlowCache {
    private const val TAG = "PunctumGlow"
    private const val MAX_BYTES = 16L * 1024L * 1024L
    private const val MAX_FLIGHTS = 12
    private const val DOWNSAMPLE = 3f
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val slots = Semaphore(2)
    private val cache = LinkedHashMap<Key, Entry>(24, .75f, true)
    private val flights = linkedMapOf<Key, Flight>()
    private var bytes = 0L

    private data class Key(val source: String, val width: Int, val height: Int, val margin: Float, val density: Float)
    private data class Entry(val bitmap: ImageBitmap, val bytes: Long)
    private class Flight {
        val result = CompletableDeferred<ImageBitmap>()
        lateinit var job: Job
        var started = false
    }

    private fun key(context: Context, photo: Photo, widthPx: Int, heightPx: Int, minimumMarginPx: Float) =
        Key("${photo.uri}:${photo.modifiedMillis}:${photo.fileSizeBytes}:${photo.width}x${photo.height}",
            widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), minimumMarginPx,
            context.resources.displayMetrics.density)

    fun cached(context: Context, photo: Photo, widthPx: Int, heightPx: Int, minimumMarginPx: Float): ImageBitmap? =
        synchronized(lock) {
            val key = key(context, photo, widthPx, heightPx, minimumMarginPx)
            cache[key]?.bitmap.also { image ->
                if (image != null && Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "hit id=${key.source.hashCode()} cacheBytes=$bytes")
                }
            }
        }

    /** Work survives a caller moving pages; bounded shared flights avoid duplicate per-page rendering. */
    suspend fun load(
        context: Context, photo: Photo, base: ImageBitmap,
        widthPx: Int, heightPx: Int, minimumMarginPx: Float,
    ): ImageBitmap {
        val key = key(context, photo, widthPx, heightPx, minimumMarginPx)
        val flight = synchronized(lock) {
            cache[key]?.let { return it.bitmap }
            flights[key] ?: run {
                // Do not retain arbitrarily many full-size base references in a speculative queue.
                if (flights.size >= MAX_FLIGHTS) {
                    flights.entries.firstOrNull { !it.value.started }?.let { old ->
                        flights.remove(old.key)
                        old.value.job.cancel()
                    }
                }
                val created = Flight()
                created.job = scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        val bitmap = slots.withPermit {
                            synchronized(lock) { created.started = true }
                            coroutineContext.ensureActive()
                            render(photo, base, key)
                        }
                        coroutineContext.ensureActive()
                        val value = bitmap.asImageBitmap()
                        bitmap.prepareToDraw()
                        synchronized(lock) {
                            val entry = Entry(value, bitmap.allocationByteCount.toLong())
                            bytes += entry.bytes - (cache.put(key, entry)?.bytes ?: 0L)
                            while (bytes > MAX_BYTES && cache.isNotEmpty()) {
                                val first = cache.entries.first()
                                bytes -= first.value.bytes
                                cache.remove(first.key)
                            }
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "stored id=${key.source.hashCode()} cacheBytes=$bytes entries=${cache.size}")
                            }
                        }
                        created.result.complete(value)
                    } catch (error: Exception) {
                        created.result.completeExceptionally(error)
                    }
                }
                flights[key] = created
                created.job.invokeOnCompletion { cause ->
                    if (cause != null) created.result.completeExceptionally(cause)
                    synchronized(lock) { if (flights[key] === created) flights.remove(key) }
                }
                created
            }
        }
        flight.job.start()
        return flight.result.await()
    }

    private suspend fun render(photo: Photo, base: ImageBitmap, key: Key): Bitmap {
        val started = SystemClock.elapsedRealtime()
        val width = ceil(key.width / DOWNSAMPLE).toInt().coerceAtLeast(1)
        val height = ceil(key.height / DOWNSAMPLE).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.scale(width.toFloat() / key.width, height.toFloat() / key.height)
            val frame = filmProjectionFrame(photo.aspectRatio, key.width / key.density, key.height / key.density,
                false, key.margin / key.density)
            val imageWidth = frame.size.width * key.density
            val imageHeight = frame.size.height * key.density
            val cx = key.width / 2f
            val cy = (frame.top + frame.size.height / 2f) * key.density
            val bottom = cy + imageHeight / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            val source = base.asAndroidBitmap()
            val readable = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
            try {
                val left = (cx - imageWidth * .515f).roundToInt()
                val top = (cy - imageHeight * .515f).roundToInt()
                val right = left + (imageWidth * 1.03f).roundToInt().coerceAtLeast(1)
                val lower = top + (imageHeight * 1.03f).roundToInt().coerceAtLeast(1)
                paint.alpha = (.18f * 255f).roundToInt()
                canvas.drawBitmap(readable, null, RectF(left.toFloat(), top.toFloat(), right.toFloat(), lower.toFloat()), paint)
            } finally { if (readable !== source) readable.recycle() }
            paint.alpha = 255
            paint.shader = RadialGradient(cx, cy, (max(imageWidth, imageHeight) * .8f).coerceAtLeast(1f),
                intArrayOf(bone(.045f), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, key.width.toFloat(), key.height.toFloat(), paint)
            if (bottom < key.height) {
                val beam = Path().apply {
                    moveTo(cx - imageWidth * .48f, bottom)
                    lineTo(cx + imageWidth * .48f, bottom)
                    lineTo(cx + 12f * key.density, key.height + 24f * key.density)
                    lineTo(cx - 12f * key.density, key.height + 24f * key.density)
                    close()
                }
                paint.shader = LinearGradient(0f, bottom, 0f, key.height.toFloat(),
                    intArrayOf(bone(.012f), bone(.035f)), null, Shader.TileMode.CLAMP)
                canvas.drawPath(beam, paint)
            }
            paint.shader = null
            coroutineContext.ensureActive()
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val sigma = 16f * key.density * (width.toFloat() / key.width)
            blurFilmGlow(pixels, width, height, sigma)
            coroutineContext.ensureActive()
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "render id=${key.source.hashCode()} size=${width}x$height ms=${SystemClock.elapsedRealtime() - started}")
            }
            return bitmap
        } catch (error: Exception) {
            bitmap.recycle()
            throw error
        }
    }

    private fun bone(alpha: Float): Int = Color.argb((alpha * 255f).roundToInt(), 237, 232, 221)
}

/** Three box filters approximate the existing Gaussian glow in premultiplied RGBA. */
internal fun blurFilmGlow(pixels: IntArray, width: Int, height: Int, sigma: Float) {
    if (width <= 0 || height <= 0 || pixels.size != width * height || sigma <= 0f) return
    val channels = Array(4) { IntArray(pixels.size) }
    val scratch = IntArray(pixels.size)
    for (i in pixels.indices) {
        val c = pixels[i]
        val a = c ushr 24
        channels[0][i] = a
        channels[1][i] = ((c ushr 16 and 255) * a + 127) / 255
        channels[2][i] = ((c ushr 8 and 255) * a + 127) / 255
        channels[3][i] = ((c and 255) * a + 127) / 255
    }
    var lower = kotlin.math.floor(sqrt(4.0 * sigma * sigma + 1.0)).toInt()
    if (lower % 2 == 0) lower--
    lower = lower.coerceAtLeast(1)
    val upper = lower + 2
    val countLower = ((12.0 * sigma * sigma - 3.0 * lower * lower - 12.0 * lower - 9.0) /
        (-4.0 * lower - 4.0)).roundToInt().coerceIn(0, 3)
    for (pass in 0 until 3) {
        val radius = ((if (pass < countLower) lower else upper) - 1) / 2
        if (radius <= 0) continue
        for (channel in channels) {
            boxGlow(channel, scratch, width, height, radius, horizontal = true)
            boxGlow(scratch, channel, width, height, radius, horizontal = false)
        }
    }
    for (i in pixels.indices) {
        val a = channels[0][i].coerceIn(0, 255)
        if (a == 0) pixels[i] = 0 else {
            val r = ((channels[1][i] * 255 + a / 2) / a).coerceIn(0, 255)
            val g = ((channels[2][i] * 255 + a / 2) / a).coerceIn(0, 255)
            val b = ((channels[3][i] * 255 + a / 2) / a).coerceIn(0, 255)
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}

private fun boxGlow(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val stride = if (horizontal) 1 else width
    val window = radius * 2 + 1
    for (line in 0 until lines) {
        val start = if (horizontal) line * width else line
        var sum = 0
        for (i in -radius..radius) sum += input[start + i.coerceIn(0, length - 1) * stride]
        for (i in 0 until length) {
            output[start + i * stride] = (sum + window / 2) / window
            sum -= input[start + (i - radius).coerceIn(0, length - 1) * stride]
            sum += input[start + (i + radius + 1).coerceIn(0, length - 1) * stride]
        }
    }
}
