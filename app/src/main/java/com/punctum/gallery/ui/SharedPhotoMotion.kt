package com.punctum.gallery.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import coil.imageLoader
import coil.memory.MemoryCache
import com.punctum.gallery.data.PhotoStill
import com.punctum.gallery.model.Photo

internal const val SHARED_MOTION_MILLIS = 280
internal val SharedMotionEase = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

internal const val DETAIL_ENTRY_MILLIS = 350
internal val DetailEntryEase = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

// A flight exists only during route navigation. Pager changes never create one.
internal class PhotoFlight(
    val photoID: String,
    val bitmap: Bitmap,
    val source: Rect,
    val unifiedEntry: Boolean = false,
) {
    var destination by mutableStateOf(source)
    val progress = Animatable(0f)
    val bounds: Rect
        get() {
            val p = progress.value
            fun mix(a: Float, b: Float) = a + (b - a) * p
            return Rect(mix(source.left, destination.left), mix(source.top, destination.top),
                mix(source.right, destination.right), mix(source.bottom, destination.bottom))
        }
}

internal data class SharedPhotoMotion(
    val listBounds: MutableMap<String, Rect>,
    val detailBounds: MutableMap<String, Rect>,
    val flight: PhotoFlight?,
    val informationAlpha: () -> Float,
)

internal val LocalSharedPhotoMotion = compositionLocalOf<SharedPhotoMotion?> { null }
internal val LocalHomeAnchors = compositionLocalOf<MutableMap<String, Rect>?> { null }

@Composable
internal fun Modifier.homeMotionAnchor(id: String): Modifier {
    val anchors = LocalHomeAnchors.current ?: return this
    return onGloballyPositioned { anchors[id] = it.boundsInRoot() }
}

@Composable
internal fun Modifier.sharedPhotoMotion(id: String, detail: Boolean): Modifier {
    val motion = LocalSharedPhotoMotion.current ?: return this
    val anchors = if (detail) motion.detailBounds else motion.listBounds
    return onGloballyPositioned { anchors[id] = it.boundsInRoot() }
        .graphicsLayer {
            val flight = motion.flight
            alpha = if (flight?.photoID == id && (!detail || !flight.unifiedEntry)) 0f else 1f
        }
}

// Picture, spacing and typography share one layer and one transform. The
// image's final bounds anchor the whole page to the clicked thumbnail.
@Composable
internal fun Modifier.detailNavigationGroup(id: String): Modifier {
    val flight = LocalSharedPhotoMotion.current?.flight ?: return this
    if (!flight.unifiedEntry || flight.photoID != id) return this
    // Resolve visibility in composition alongside the temporary thumbnail overlay.
    val ready = flight.destination != flight.source
    return graphicsLayer {
        val target = flight.destination
        alpha = if (ready) 1f else 0f
        if (ready) {
            val bounds = flight.bounds
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = bounds.width / target.width.coerceAtLeast(1f)
            scaleY = bounds.height / target.height.coerceAtLeast(1f)
            translationX = bounds.left - target.left * scaleX
            translationY = bounds.top - target.top * scaleY
        }
    }
}

internal fun photoMotionBitmap(context: Context, photo: Photo, detail: Boolean): Bitmap? {
    val listKeys = listOfNotNull(
        if (PhotoStill.SINGLE_PASS_LIST) "gallery-final-thumb-fit-v2:${photo.uri}:${photo.modifiedMillis}" else null,
        photo.thumbnailPath?.let { "gallery-hq-thumb-fit-v1:${photo.uri}:$it" },
        "gallery-system-thumb-fit-v3:${photo.uri}:${photo.modifiedMillis}",
    )
    val keys = if (detail) listOf("detail:${photo.uri}") + listKeys else listKeys
    return keys.firstNotNullOfOrNull { context.imageLoader.memoryCache?.get(MemoryCache.Key(it))?.bitmap }
}

// Render one cached bitmap at fixed layout size. Only its transform changes per frame,
// avoiding live pager/layout/decoder updates in the moving layer.
@Composable
internal fun PhotoFlightOverlay(flight: PhotoFlight) {
    val density = LocalDensity.current
    val target = flight.destination
    Image(
        bitmap = flight.bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .requiredSize(with(density) { target.width.toDp() }, with(density) { target.height.toDp() })
            .graphicsLayer {
                val bounds = flight.bounds
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = bounds.left
                translationY = bounds.top
                scaleX = bounds.width / target.width.coerceAtLeast(1f)
                scaleY = bounds.height / target.height.coerceAtLeast(1f)
            },
    )
}

internal fun Modifier.navigationMotionGuard(blocked: Boolean): Modifier = pointerInput(blocked) {
    if (blocked) awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}
