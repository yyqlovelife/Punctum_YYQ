package com.punctum.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
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

                LaunchedEffect(Unit) { vm.start() }

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
