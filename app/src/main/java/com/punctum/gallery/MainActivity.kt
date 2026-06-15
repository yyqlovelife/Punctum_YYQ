package com.punctum.gallery

import android.os.Bundle
import android.Manifest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.database.ContentObserver
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.punctum.gallery.model.Gallery
import com.punctum.gallery.model.GalleryOverview
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
                val picker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree()
                ) { uri -> uri?.let { vm.addGallery(it) } }
                val mediaPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { vm.refreshCurrentData() }
                val deleteConfirmationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    vm.onDeleteConfirmationHandled(result.resultCode == Activity.RESULT_OK)
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

                PunctumApp(vm = vm, onPickFolder = { picker.launch(null) })
            }
        }
    }
}

@Composable
private fun PunctumApp(vm: GalleryViewModel, onPickFolder: () -> Unit) {
    var renameTarget by remember { mutableStateOf<Gallery?>(null) }
    val postcardListState = rememberLazyListState()
    val ticketListState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize().background(Ink)) {
        val current = vm.currentGallery
        AnimatedContent(
            targetState = current?.uri?.toString(),
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.94f)) togetherWith
                    (fadeOut() + scaleOut(targetScale = 1.04f))
            },
            label = "home-gallery",
        ) { currentKey ->
            val visibleGallery = vm.galleries.firstOrNull { it.uri.toString() == currentKey }
            if (visibleGallery == null) {
                if (vm.galleries.isEmpty()) {
                    EmptyScreen(onPickFolder = onPickFolder)
                } else {
                    val ordered = vm.galleries.map { g ->
                        vm.overviews[g.uri.toString()] ?: com.punctum.gallery.model.GalleryOverview(g, loading = true)
                    }
                    SwitcherScreen(
                        overviews = ordered,
                        canClose = false,
                        title = "Your Punctums",
                        subtitle = vm.homeSubtitle,
                        invitationStyle = vm.invitationStyle,
                        postcardListState = postcardListState,
                        ticketListState = ticketListState,
                        onSelect = vm::selectGallery,
                        onAdd = onPickFolder,
                        onToggleInvitationStyle = vm::toggleInvitationStyle,
                        onRename = { renameTarget = it },
                        onMove = vm::moveGallery,
                        onDelete = vm::removeGallery,
                        onClose = vm::closeSwitcher,
                    )
                }
            } else {
                key(visibleGallery.uri.toString()) {
                    val listState = rememberLazyListState()
                    GalleryScreen(
                        gallery = visibleGallery,
                        photos = vm.photos,
                        overview = vm.overviews[visibleGallery.uri.toString()],
                        loading = vm.loadingPhotos,
                        listState = listState,
                        onOpenSwitcher = vm::openSwitcher,
                        onRename = { renameTarget = it },
                        onSelectPhoto = vm::openDetail,
                        onWarmThumbnails = vm::warmGalleryThumbnails,
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
                onSelect = vm::selectGallery,
                onAdd = onPickFolder,
                onToggleInvitationStyle = vm::toggleInvitationStyle,
                onRename = { renameTarget = it },
                onMove = vm::moveGallery,
                onDelete = vm::removeGallery,
                onClose = vm::closeSwitcher,
            )
        }

        val detailIndex = vm.selectedIndex
        AnimatedVisibility(
            visible = detailIndex != null && vm.photos.isNotEmpty(),
            enter = fadeIn() + scaleIn(initialScale = 0.94f),
            exit = fadeOut() + scaleOut(targetScale = 1.04f),
        ) {
            DetailScreen(
                photos = vm.photos,
                startIndex = detailIndex ?: 0,
                onDelete = vm::deletePhoto,
                onClose = vm::closeDetail,
                onWarmImages = vm::warmDetailImages,
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
