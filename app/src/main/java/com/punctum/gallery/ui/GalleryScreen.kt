package com.punctum.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import java.io.File
import com.punctum.gallery.model.Gallery
import com.punctum.gallery.model.GalleryOverview
import com.punctum.gallery.model.GalleryStyle
import com.punctum.gallery.model.Photo
import com.punctum.gallery.ui.theme.Bone
import com.punctum.gallery.ui.theme.Gold
import com.punctum.gallery.ui.theme.Muted
import com.punctum.gallery.ui.theme.Surface1
import com.punctum.gallery.ui.theme.galleryTitleFont
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview

@Composable
@OptIn(FlowPreview::class)
internal fun GalleryScreen(
    gallery: Gallery,
    photos: List<Photo>,
    overview: GalleryOverview?,
    loading: Boolean,
    listState: LazyListState,
    onOpenSwitcher: () -> Unit,
    onRename: (Gallery) -> Unit,
    onSelectPhoto: (Int) -> Unit,
    onWarmThumbnails: (Int, Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(photos, listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .map { visible ->
                if (visible.isEmpty() || photos.isEmpty()) null
                else {
                    val firstRow = (visible.minOrNull() ?: 1) - 1
                    val lastRow = (visible.maxOrNull() ?: 1) - 1
                    val firstPhoto = (firstRow.coerceAtLeast(0)) * 2
                    val lastPhoto = ((lastRow.coerceAtLeast(0)) * 2 + 1).coerceAtMost(photos.lastIndex)
                    firstPhoto to lastPhoto
                }
            }
            .distinctUntilChanged()
            .debounce(100)
            .collectLatest { range ->
                range?.let { onWarmThumbnails(it.first, it.second) }
            }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            GalleryHeader(
                gallery = gallery,
                count = overview?.count ?: photos.size,
                timeSpan = overview?.timeSpan.orEmpty(),
                onOpenSwitcher = onOpenSwitcher,
                onRename = { onRename(gallery) },
                onDoubleTapTop = { scope.launch { listState.animateScrollToItem(0) } },
            )
        }

        when {
            loading -> item(key = "loading") {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Gold, strokeWidth = 1.dp)
                }
            }

            photos.isEmpty() -> item(key = "empty") {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "这个文件夹里还没有照片",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
            }

            else -> itemsIndexed(
                items = photos.chunked(2),
                key = { rowIndex, row -> row.joinToString("|") { it.uri.toString() } + rowIndex },
            ) { rowIndex, row ->
                OriginalRatioRow(
                    row = row,
                    rowStartIndex = rowIndex * 2,
                    onSelectPhoto = onSelectPhoto,
                )
            }
        }

        item(key = "footer") {
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}

@Composable
private fun OriginalRatioRow(
    row: List<Photo>,
    rowStartIndex: Int,
    onSelectPhoto: (Int) -> Unit,
) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth()) {
        row.forEachIndexed { offset, photo ->
            val aspect = photo.aspectRatio.coerceIn(0.45f, 2.4f)
            val imageModel = remember(photo.uri, photo.thumbnailPath) {
                ImageRequest.Builder(context)
                    .data(photo.thumbnailPath?.let { File(it) }?.takeIf { it.exists() } ?: photo.uri)
                    .memoryCacheKey("gallery-thumb:${photo.uri}")
                    .diskCacheKey("gallery-thumb:${photo.uri}")
                    .size(900)
                    .crossfade(false)
                    .build()
            }
            Box(
                modifier = Modifier
                    .weight(aspect)
                    .aspectRatio(aspect)
                    .background(Surface1)
                    .clickable { onSelectPhoto(rowStartIndex + offset) },
            ) {
                PunctumImage(
                    model = imageModel,
                    contentDescription = photo.name,
                    contentScale = ContentScale.Crop,
                    animateOnLoad = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (row.size == 1) {
            Spacer(Modifier.weight(row.first().aspectRatio.coerceIn(0.45f, 2.4f)))
        }
    }
}

@Composable
private fun GalleryHeader(
    gallery: Gallery,
    count: Int,
    timeSpan: String,
    onOpenSwitcher: () -> Unit,
    onRename: () -> Unit,
    onDoubleTapTop: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onDoubleTapTop() })
            }
            .statusBarsPadding()
            .padding(start = 24.dp, end = 12.dp, top = 18.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.clickable(onClick = onOpenSwitcher).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Collections,
                    contentDescription = "切换画廊",
                    tint = Muted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Your Punctums", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRename) {
                Icon(Icons.Outlined.Edit, contentDescription = "重命名", tint = Muted)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            gallery.displayName,
            style = MaterialTheme.typography.displayMedium.copy(fontFamily = galleryTitleFont(gallery.displayName)),
            color = Bone,
        )
        Spacer(Modifier.height(14.dp))
        if (timeSpan.isNotBlank()) {
            Text(timeSpan, style = MaterialTheme.typography.bodyMedium, color = Muted)
            Spacer(Modifier.height(6.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "关于 $count 幅作品的故事",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "风格 · ${GalleryStyle.from(gallery.styleId).label}",
                style = MaterialTheme.typography.labelSmall,
                color = Gold.copy(alpha = 0.8f),
            )
        }
        Spacer(Modifier.height(16.dp))
        HairlineDivider(Modifier.padding(end = 12.dp))
    }
}
