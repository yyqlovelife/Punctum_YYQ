package com.punctum.gallery

import android.os.Bundle
import android.Manifest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.database.ContentObserver
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import com.punctum.gallery.ui.AlbumPickerDialog
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.punctum.gallery.model.Gallery
import com.punctum.gallery.model.GalleryOverview
import com.punctum.gallery.model.InvitationCardStyle
import com.punctum.gallery.ui.DetailScreen
import com.punctum.gallery.ui.EmptyScreen
import com.punctum.gallery.ui.GalleryScreen
import com.punctum.gallery.ui.SwitcherScreen
import com.punctum.gallery.ui.theme.Bone
import com.punctum.gallery.ui.theme.Gold
import com.punctum.gallery.ui.theme.Ink
import com.punctum.gallery.ui.theme.Muted
import com.punctum.gallery.ui.theme.PunctumTheme
import com.punctum.gallery.ui.theme.Surface1

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            PunctumTheme {
                val vm: GalleryViewModel = viewModel()
                val mediaPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { vm.refreshCurrentData() }
                val deleteConfirmationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    vm.onDeleteConfirmationHandled(result.resultCode == Activity.RESULT_OK)
                }
                val moveConfirmationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    vm.onMoveConfirmationHandled(result.resultCode == Activity.RESULT_OK)
                }
                val mediaManagementLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) {
                    val granted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        MediaStore.canManageMedia(this@MainActivity)
                    vm.onMediaManagementPermissionHandled(granted)
                }

                LaunchedEffect(Unit) {
                    vm.start()
                    vm.startForegroundSync()
                    val hasImagePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.READ_MEDIA_IMAGES,
                        ) == PackageManager.PERMISSION_GRANTED
                    val hasLocationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.ACCESS_MEDIA_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED
                    val hasLegacyWritePermission = Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ) == PackageManager.PERMISSION_GRANTED
                    val permissions = buildList {
                        if (!hasImagePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.READ_MEDIA_IMAGES)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                            }
                        }
                        if (!hasLocationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            add(Manifest.permission.ACCESS_MEDIA_LOCATION)
                        }
                        if (!hasLegacyWritePermission && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                    if (permissions.isNotEmpty()) {
                        mediaPermissionLauncher.launch(permissions.toTypedArray())
                    }
                }
                DisposableEffect(vm) {
                    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            vm.refreshCurrentData()
                        }
                    }
                    contentResolver.registerContentObserver(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        true,
                        observer,
                    )
                    onDispose { contentResolver.unregisterContentObserver(observer) }
                }
                DisposableEffect(vm) {
                    val lifecycleObserver = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> vm.startForegroundSync()
                            Lifecycle.Event.ON_PAUSE -> vm.stopForegroundSync()
                            else -> Unit
                        }
                    }
                    lifecycle.addObserver(lifecycleObserver)
                    onDispose {
                        vm.stopForegroundSync()
                        lifecycle.removeObserver(lifecycleObserver)
                    }
                }
                val pendingDeleteConfirmation = vm.pendingDeleteConfirmation
                LaunchedEffect(pendingDeleteConfirmation) {
                    pendingDeleteConfirmation?.let { pending ->
                        deleteConfirmationLauncher.launch(
                            IntentSenderRequest.Builder(pending.intentSender).build()
                        )
                    }
                }
                val pendingMoveConfirmation = vm.pendingMoveConfirmation
                LaunchedEffect(pendingMoveConfirmation) {
                    pendingMoveConfirmation?.let { pending ->
                        moveConfirmationLauncher.launch(
                            IntentSenderRequest.Builder(pending.intentSender).build()
                        )
                    }
                }
                val pendingMediaManagementPermission = vm.pendingMediaManagementPermission
                LaunchedEffect(pendingMediaManagementPermission) {
                    if (pendingMediaManagementPermission) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_MANAGE_MEDIA,
                                Uri.parse("package:$packageName"),
                            )
                            if (intent.resolveActivity(packageManager) != null) {
                                mediaManagementLauncher.launch(intent)
                            } else {
                                vm.onMediaManagementPermissionHandled(false)
                            }
                        } else {
                            vm.onMediaManagementPermissionHandled(false)
                        }
                    }
                }

                PunctumApp(vm = vm)
            }
        }
    }
}

@Composable
private fun PunctumApp(vm: GalleryViewModel) {
    var renameTarget by remember { mutableStateOf<Gallery?>(null) }
    var showAlbumPicker by remember { mutableStateOf(false) }
    var readyGalleryKey by remember { mutableStateOf<String?>(null) }
    val postcardListState = rememberLazyListState()
    val ticketListState = rememberLazyListState()
    val reversalFilmGridState = rememberLazyGridState()
    val homeToast = vm.homeToast
    LaunchedEffect(homeToast) {
        if (homeToast == null) return@LaunchedEffect
        delay(2200)
        if (vm.homeToast == homeToast) vm.clearHomeToast()
    }
    val pendingScrollIndex = vm.pendingHomeScrollIndex
    LaunchedEffect(pendingScrollIndex, vm.invitationStyle) {
        val index = pendingScrollIndex ?: return@LaunchedEffect
        snapshotFlow {
            when (vm.invitationStyle) {
                InvitationCardStyle.POSTCARD -> postcardListState.layoutInfo.totalItemsCount
                InvitationCardStyle.TICKET -> ticketListState.layoutInfo.totalItemsCount
                InvitationCardStyle.REVERSAL_FILM -> reversalFilmGridState.layoutInfo.totalItemsCount
            }
        }.first { it > index }
        when (vm.invitationStyle) {
            InvitationCardStyle.POSTCARD -> postcardListState.animateScrollToItem(index)
            InvitationCardStyle.TICKET -> ticketListState.animateScrollToItem(index)
            InvitationCardStyle.REVERSAL_FILM -> reversalFilmGridState.animateScrollToItem(index)
        }
        vm.clearPendingHomeScroll()
    }

    Box(modifier = Modifier.fillMaxSize().background(Ink)) {
        val current = vm.currentGallery
        val currentKey = current?.uri?.toString()
        LaunchedEffect(currentKey) {
            if (currentKey == null) readyGalleryKey = null
        }

        if (vm.galleries.isEmpty()) {
            EmptyScreen(onPickFolder = { showAlbumPicker = true })
        } else {
            val ordered = vm.galleries.map { gallery ->
                vm.overviews[gallery.uri.toString()] ?: GalleryOverview(gallery, loading = true)
            }
            SwitcherScreen(
                overviews = ordered,
                canClose = false,
                title = "Your Punctums",
                subtitle = vm.homeSubtitle,
                invitationStyle = vm.invitationStyle,
                postcardListState = postcardListState,
                ticketListState = ticketListState,
                reversalFilmGridState = reversalFilmGridState,
                onSelect = vm::selectGallery,
                onAdd = { showAlbumPicker = true },
                onToggleInvitationStyle = vm::toggleInvitationStyle,
                onRename = { renameTarget = it },
                onMove = vm::moveGallery,
                onDelete = vm::removeGallery,
                onClose = vm::closeSwitcher,
            )
        }

        if (current != null && currentKey != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Ink)
                    .graphicsLayer {
                        alpha = if (readyGalleryKey == currentKey) 1f else 0f
                    },
            ) {
                key(currentKey) {
                    val listState = rememberLazyListState()
                    GalleryScreen(
                        gallery = current,
                        photos = vm.photos,
                        overview = vm.overviews[currentKey],
                        loading = vm.loadingPhotos,
                        listState = listState,
                        onOpenSwitcher = vm::openSwitcher,
                        onRename = { renameTarget = it },
                        onSelectPhoto = vm::openDetail,
                        onDeletePhoto = vm::deletePhoto,
                        onWarmThumbnails = vm::warmGalleryThumbnails,
                        onContentReady = {
                            if (vm.currentUri == currentKey) readyGalleryKey = currentKey
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = vm.showSwitcher,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val ordered = vm.galleries.map { g ->
                vm.overviews[g.uri.toString()] ?: GalleryOverview(g, loading = true)
            }
            SwitcherScreen(
                overviews = ordered,
                canClose = vm.currentGallery != null,
                title = "Your Punctums",
                subtitle = vm.homeSubtitle,
                invitationStyle = vm.invitationStyle,
                postcardListState = postcardListState,
                ticketListState = ticketListState,
                reversalFilmGridState = reversalFilmGridState,
                onSelect = vm::selectGallery,
                onAdd = { showAlbumPicker = true },
                onToggleInvitationStyle = vm::toggleInvitationStyle,
                onRename = { renameTarget = it },
                onMove = vm::moveGallery,
                onDelete = vm::removeGallery,
                onClose = vm::closeSwitcher,
            )
        }

        val detailIndex = vm.selectedIndex
        if (detailIndex != null && vm.photos.isNotEmpty()) {
            DetailScreen(
                photos = vm.photos,
                startIndex = detailIndex,
                currentAlbumKey = currentKey,
                availableSystemAlbums = vm.systemAlbums,
                completedMove = vm.completedMove,
                moveError = vm.moveError,
                onDelete = vm::queueDetailDeletion,
                onClose = vm::closeDetail,
                onWarmImages = vm::warmDetailImages,
                onRequestSystemAlbums = vm::loadSystemAlbums,
                onMovePhoto = vm::movePhoto,
                onAcknowledgeMove = vm::acknowledgeCompletedMove,
                onClearMoveError = vm::clearMoveError,
            )
        }

        if (showAlbumPicker) {
            AlbumPickerDialog(
                albums = vm.systemAlbums,
                existingAlbumKeys = vm.galleries.map { it.uri.toString() }.toSet(),
                onRequestAlbums = vm::loadSystemAlbums,
                onConfirm = { albums ->
                    vm.addSystemAlbums(albums)
                    showAlbumPicker = false
                },
                onDismiss = { showAlbumPicker = false },
            )
        }

        AnimatedVisibility(
            visible = homeToast != null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160)),
        ) {
            Text(
                homeToast.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = Bone.copy(alpha = 0.86f),
                modifier = Modifier
                    .background(Color(0xFF191919).copy(alpha = 0.82f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }

    BackHandler(enabled = vm.selectedIndex != null || vm.showSwitcher || vm.currentGallery != null) {
        when {
            vm.selectedIndex != null -> vm.closeDetail()
            vm.showSwitcher -> vm.closeSwitcher()
            vm.currentGallery != null -> vm.goHome()
        }
    }

    vm.pendingDetailDeleteConfirmation?.let { pendingPhotos ->
        AlertDialog(
            onDismissRequest = vm::cancelDetailDeletion,
            confirmButton = {
                TextButton(onClick = vm::confirmDetailDeletion) {
                    Text("确定删除", color = Color(0xFFE24646))
                }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelDetailDeletion) {
                    Text("取消", color = Muted)
                }
            },
            title = {
                Text(
                    "本次删除 ${pendingPhotos.size} 项",
                    style = MaterialTheme.typography.titleLarge,
                    color = Bone,
                )
            },
            text = {
                Text(
                    "确定删除后，所选照片将移入系统相册回收站",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            },
            containerColor = Surface1,
            titleContentColor = Bone,
            textContentColor = Muted,
        )
    }

    renameTarget?.let { target ->
        RenameDialog(
            initial = target.displayName,
            onConfirm = {
                vm.renameGallery(target.uri.toString(), it)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("保存", color = Gold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Muted)
            }
        },
        title = {
            Text("重命名画廊", style = MaterialTheme.typography.titleLarge, color = Bone)
        },
        text = {
            Column {
                Text(
                    "仅修改在 Punctum 中显示的名称，不会改动系统文件夹本身。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Bone,
                        unfocusedTextColor = Bone,
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = Muted,
                        cursorColor = Gold,
                    ),
                )
            }
        },
        containerColor = Surface1,
    )
}
