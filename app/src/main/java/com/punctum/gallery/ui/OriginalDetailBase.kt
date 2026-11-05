package com.punctum.gallery.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.punctum.gallery.model.PhotoZoomState
import com.punctum.gallery.data.DetailImagePipeline
import com.punctum.gallery.data.PhotoStill
import com.punctum.gallery.model.Photo
import coil.request.ImageRequest

/** A matching cached thumbnail remains visible while the original-derived screen image is prepared. */
@Composable
internal fun OriginalDetailBase(photo: Photo, contentDescription: String, onSuccess: () -> Unit, modifier: Modifier,
    pose: () -> PhotoZoomState, viewportInWindow: () -> Rect, tilesEnabled: Boolean) {
    val context = LocalContext.current
    val identity = DetailImagePipeline.cacheKey(context, photo)
    var bitmap by remember(identity) { mutableStateOf(DetailImagePipeline.cachedBitmap(context, photo)) }
    val ready by rememberUpdatedState(onSuccess)
    LaunchedEffect(identity) {
        if (bitmap != null) ready()
        val decoded = DetailImagePipeline.load(context, photo)
        if (decoded != null) { bitmap = decoded; ready() }
    }
    val image = bitmap
    if (image != null) OriginalPhotoTiles(photo, pose, viewportInWindow,
        modifier.semantics { this.contentDescription = contentDescription },
        enabled = tilesEnabled, baseBitmap = image)
    else PunctumImage(
        model = remember(identity, photo.thumbnailPath) {
            ImageRequest.Builder(context).data(PhotoStill.forList(photo))
                .placeholderMemoryCacheKey(photo.thumbnailPath?.let { "gallery-hq-thumb-fit-v1:${photo.uri}:$it" }
                    ?: "gallery-system-thumb-fit-v3:${photo.uri}:${photo.modifiedMillis}")
                .size(1400).crossfade(false).build()
        }, contentDescription = contentDescription, contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxSize(), animateOnLoad = false,
    )
}
