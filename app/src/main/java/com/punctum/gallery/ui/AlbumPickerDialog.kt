package com.punctum.gallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.punctum.gallery.data.PhotoRepository
import com.punctum.gallery.model.SystemAlbum
import com.punctum.gallery.ui.theme.Bone
import com.punctum.gallery.ui.theme.Gold
import com.punctum.gallery.ui.theme.Ink
import com.punctum.gallery.ui.theme.Muted
import com.punctum.gallery.ui.theme.galleryTitleFont

@Composable
internal fun AlbumPickerDialog(
    existingAlbumKeys: Set<String>,
    onConfirm: (List<SystemAlbum>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var albums by remember { mutableStateOf<List<SystemAlbum>?>(null) }
    var selectedKeys by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        albums = PhotoRepository.listDcimAndPicturesAlbums(context)
    }

    val loaded = albums
    val selectableKeys = remember(loaded, existingAlbumKeys) {
        loaded.orEmpty()
            .map { it.uri.toString() }
            .filterNot { it in existingAlbumKeys }
            .toSet()
    }
    val confirmEnabled = selectedKeys.any { it in selectableKeys }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = confirmEnabled,
                onClick = {
                    val chosen = loaded.orEmpty().filter { it.uri.toString() in selectedKeys }
                    onConfirm(chosen)
                },
            ) {
                Text("确定", color = if (confirmEnabled) Gold else Muted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Muted)
            }
        },
        title = {
            Text("选择图集", style = MaterialTheme.typography.titleLarge, color = Bone)
        },
        text = {
            when (loaded) {
                null -> {
                    Text(
                        "正在读取 DCIM 与 Pictures…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
                emptyList<SystemAlbum>() -> {
                    Text(
                        "没有找到 DCIM 或 Pictures 下的图集。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                    ) {
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
        containerColor = Ink,
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
                if (alreadyAdded) "已添加 · ${album.itemCount} 项" else "${album.itemCount} 项",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
    }
}

@Composable
internal fun MoveAlbumPickerDialog(
    currentAlbumKey: String?,
    onPick: (SystemAlbum) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var albums by remember { mutableStateOf<List<SystemAlbum>?>(null) }

    LaunchedEffect(Unit) {
        albums = PhotoRepository.listDcimAndPicturesAlbums(context)
    }

    val loaded = albums
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Muted)
            }
        },
        title = {
            Text("移动到图集", style = MaterialTheme.typography.titleLarge, color = Bone)
        },
        text = {
            when (loaded) {
                null -> {
                    Text(
                        "正在读取 DCIM 与 Pictures…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
                emptyList<SystemAlbum>() -> {
                    Text(
                        "没有找到 DCIM 或 Pictures 下的图集。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                    ) {
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
        containerColor = Ink,
    )
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
