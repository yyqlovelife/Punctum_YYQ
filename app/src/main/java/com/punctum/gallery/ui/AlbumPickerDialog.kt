package com.punctum.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.punctum.gallery.model.SystemAlbum
import com.punctum.gallery.ui.theme.Bone
import com.punctum.gallery.ui.theme.Gold
import com.punctum.gallery.ui.theme.Ink
import com.punctum.gallery.ui.theme.Muted
import com.punctum.gallery.ui.theme.galleryTitleFont

@Composable
internal fun AlbumPickerDialog(
    albums: List<SystemAlbum>?,
    existingAlbumKeys: Set<String>,
    onRequestAlbums: () -> Unit,
    onConfirm: (List<SystemAlbum>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedKeys by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(albums) {
        if (albums == null) onRequestAlbums()
    }
    val loaded = albums ?: return
    val listState = rememberLazyListState()

    val selectableKeys = remember(loaded, existingAlbumKeys) {
        loaded
            .map { it.uri.toString() }
            .filterNot { it in existingAlbumKeys }
            .toSet()
    }
    val confirmEnabled = selectedKeys.any { it in selectableKeys }

    PunctumOverlayDialog(
        title = "选择图集",
        onDismissRequest = onDismiss,
        animateScrim = false,
        actions = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Muted)
            }
            TextButton(
                enabled = confirmEnabled,
                onClick = {
                    val chosen = loaded.filter {
                        val key = it.uri.toString()
                        key in selectedKeys && key in selectableKeys
                    }
                    onConfirm(chosen)
                },
            ) {
                Text("确定", color = if (confirmEnabled) Gold else Muted)
            }
        },
        content = {
            when {
                loaded.isEmpty() -> {
                    Text(
                        "没有找到 DCIM 或 Pictures 下的图集。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
                else -> {
                    ScrollableAlbumList(state = listState) {
                        items(loaded, key = { it.uri.toString() }) { album ->
                            val key = album.uri.toString()
                            val alreadyAdded = key in existingAlbumKeys
                            val checked = alreadyAdded || key in selectedKeys
                            AlbumPickerRow(
                                album = album,
                                checked = checked,
                                alreadyAdded = alreadyAdded,
                                onToggle = {
                                    selectedKeys = if (key in selectedKeys) {
                                        selectedKeys - key
                                    } else {
                                        selectedKeys + key
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun AlbumPickerRow(
    album: SystemAlbum,
    checked: Boolean,
    alreadyAdded: Boolean,
    onToggle: () -> Unit,
) {
    val enabled = !alreadyAdded
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { if (enabled) onToggle() },
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = Gold,
                uncheckedColor = Muted,
                checkmarkColor = Ink,
                disabledCheckedColor = Gold.copy(alpha = 0.38f),
                disabledUncheckedColor = Muted.copy(alpha = 0.38f),
            ),
        )
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                album.displayName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = galleryTitleFont(album.displayName),
                ),
                color = if (enabled) Bone else Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (alreadyAdded) "已添加" else "${album.itemCount} 项",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
    }
}

@Composable
internal fun MoveAlbumPickerDialog(
    albums: List<SystemAlbum>?,
    currentAlbumKey: String?,
    onRequestAlbums: () -> Unit,
    onPick: (SystemAlbum) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(albums) {
        if (albums == null) onRequestAlbums()
    }
    val loaded = albums ?: return
    val listState = rememberLazyListState()

    PunctumOverlayDialog(
        title = "移动到图集",
        onDismissRequest = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Muted)
            }
        },
        content = {
            when {
                loaded.isEmpty() -> {
                    Text(
                        "没有找到 DCIM 或 Pictures 下的图集。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
                else -> {
                    ScrollableAlbumList(state = listState) {
                        items(loaded, key = { it.uri.toString() }) { album ->
                            val key = album.uri.toString()
                            val isCurrent = key == currentAlbumKey
                            MoveAlbumPickerRow(
                                album = album,
                                enabled = !isCurrent,
                                statusText = if (isCurrent) "当前图集" else null,
                                onPick = { onPick(album) },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ScrollableAlbumList(
    state: LazyListState,
    content: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val thumbHeight = 36.dp
    val layoutInfo = state.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    val showsScrollbar = state.canScrollBackward || state.canScrollForward
    val averageItemHeightPx = if (visibleItems.isEmpty()) {
        1f
    } else {
        visibleItems.sumOf { it.size }.toFloat() / visibleItems.size
    }
    val viewportHeightPx = layoutInfo.viewportSize.height.toFloat()
    val scrollRangePx =
        (averageItemHeightPx * layoutInfo.totalItemsCount - viewportHeightPx).coerceAtLeast(1f)
    val estimatedScrollOffsetPx =
        state.firstVisibleItemIndex * averageItemHeightPx + state.firstVisibleItemScrollOffset
    val scrollProgress = (estimatedScrollOffsetPx / scrollRangePx).coerceIn(0f, 1f)
    val thumbOffsetY = with(density) {
        ((viewportHeightPx - thumbHeight.toPx()).coerceAtLeast(0f) * scrollProgress).toDp()
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            content = content,
        )
        if (showsScrollbar) {
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = thumbOffsetY)
                        .padding(end = 2.dp)
                        .width(3.dp)
                        .height(thumbHeight)
                        .clip(RoundedCornerShape(50))
                        .background(Muted.copy(alpha = 0.72f)),
                )
            }
        }
    }
}

@Composable
private fun MoveAlbumPickerRow(
    album: SystemAlbum,
    enabled: Boolean,
    statusText: String?,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onPick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                album.displayName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = galleryTitleFont(album.displayName),
                ),
                color = if (enabled) Bone else Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                statusText ?: "${album.itemCount} 项",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
    }
}
