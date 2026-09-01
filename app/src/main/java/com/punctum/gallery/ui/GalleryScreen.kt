package com.punctum.gallery.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import com.punctum.gallery.data.PhotoStill
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    onDeletePhoto: (Photo) -> Unit,
    onWarmThumbnails: (Int, Int) -> Unit,
    onContentReady: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val firstRowUris = photos.take(2).map { it.uri.toString() }.toSet()
    val firstRowKey = firstRowUris.joinToString("|")
    var readyFirstRowUris by remember(gallery.uri, firstRowKey) { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(gallery.uri, loading, photos.isEmpty()) {
        if (loading || photos.isEmpty()) onContentReady()
    }

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

            else -> items(
                count = (photos.size + 1) / 2,
                // 刷新前几张完整元数据时，照片的行组合可能发生变化。
                // 行按位置保持稳定，单元格继续按 URI 隔离，避免旧行与新行短暂重叠。
                key = { rowIndex -> "photo-row-$rowIndex" },
            ) { rowIndex ->
                val rowStartIndex = rowIndex * 2
                val row = photos.subList(
                    fromIndex = rowStartIndex,
                    toIndex = (rowStartIndex + 2).coerceAtMost(photos.size),
                )
                OriginalRatioRow(
                    row = row,
                    rowStartIndex = rowStartIndex,
                    onSelectPhoto = onSelectPhoto,
                    onDeletePhoto = onDeletePhoto,
                    onPhotoReady = { photo ->
                        val uriKey = photo.uri.toString()
                        if (uriKey in firstRowUris && uriKey !in readyFirstRowUris) {
                            val updated = readyFirstRowUris + uriKey
                            readyFirstRowUris = updated
                            if (updated.containsAll(firstRowUris)) onContentReady()
                        }
                    },
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
    onDeletePhoto: (Photo) -> Unit,
    onPhotoReady: (Photo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    Row(modifier = modifier.fillMaxWidth()) {
        row.forEachIndexed { offset, photo ->
            key(photo.uri) {
            val aspect = photo.aspectRatio.coerceIn(0.45f, 2.4f)
            val deleteProgress = remember(photo.uri) { Animatable(0f) }
            var showDeleteProgress by remember(photo.uri) { mutableStateOf(false) }
            val imageModel = remember(
                photo.uri,
                photo.thumbnailPath,
                photo.stillImageByteCount,
            ) {
                ImageRequest.Builder(context)
                    .data(PhotoStill.forList(photo))
                    .memoryCacheKey("gallery-thumb:${photo.uri}")
                    .diskCacheKey("gallery-thumb:${photo.uri}")
                    .placeholderMemoryCacheKey("gallery-thumb:${photo.uri}")
                    .size(900)
                    .crossfade(false)
                    .build()
            }
            Box(
                modifier = Modifier
                    .weight(aspect)
                    .aspectRatio(aspect)
                    .background(Surface1)
                    .pointerInput(photo.uri, rowStartIndex, offset) {
                        detectTapGestures(
                            onPress = {
                                coroutineScope {
                                    var pressed = true
                                    var deleteStarted = false
                                    var deleteCommitted = false
                                    val deleteJob = launch {
                                        delay(200)
                                        if (!pressed) return@launch

                                        deleteStarted = true
                                        showDeleteProgress = true
                                        deleteProgress.snapTo(0f)
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        deleteProgress.animateTo(
                                            targetValue = 1f,
                                            animationSpec = tween(
                                                durationMillis = 500,
                                                easing = LinearEasing,
                                            ),
                                        )
                                        if (pressed) {
                                            deleteCommitted = true
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDeletePhoto(photo)
                                        }
                                    }

                                    val released = tryAwaitRelease()
                                    pressed = false
                                    if (!deleteStarted && released) {
                                        onSelectPhoto(rowStartIndex + offset)
                                    }
                                    deleteJob.cancel()
                                    if (!deleteCommitted) {
                                        deleteProgress.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(durationMillis = 120),
                                        )
                                        showDeleteProgress = false
                                    }
                                }
                            },
                        )
                    },
            ) {
                PunctumImage(
                    model = imageModel,
                    contentDescription = photo.name,
                    contentScale = ContentScale.Crop,
                    animateOnLoad = false,
                    onSuccess = { onPhotoReady(photo) },
                    onError = { onPhotoReady(photo) },
                    modifier = Modifier.fillMaxSize(),
                )
                if (showDeleteProgress) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        DeleteProgressBadge(progress = deleteProgress.value)
                    }
                }
            }
            }
        }
        if (row.size == 1) {
            Spacer(Modifier.weight(row.first().aspectRatio.coerceIn(0.45f, 2.4f)))
        }
    }
}

@Composable
private fun DeleteProgressBadge(
    progress: Float,
    size: Dp = 34.dp,
) {
    val deleteRed = Color(0xFFE63B42)
    Box(
        modifier = Modifier
            .size(size)
            .background(
                color = Color.Black.copy(alpha = 0.72f),
                shape = androidx.compose.foundation.shape.CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(2.dp)) {
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = Color.White.copy(alpha = 0.28f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
            drawArc(
                color = deleteRed,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = stroke,
            )
        }
        Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = "按住删除",
            tint = Color.White,
            modifier = Modifier.size(17.dp),
        )
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
                modifier = Modifier
                    .punctumPressable(onClick = onOpenSwitcher)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
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
