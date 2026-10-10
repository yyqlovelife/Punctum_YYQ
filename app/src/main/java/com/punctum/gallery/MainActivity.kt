package com.punctum.gallery

import androidx.compose.foundation.interaction.collectIsDraggedAsState

import android.animation.ValueAnimator
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import com.punctum.gallery.ui.LocalHomeAnchors
import com.punctum.gallery.ui.LocalSharedPhotoMotion
import com.punctum.gallery.ui.PhotoFlight
import com.punctum.gallery.ui.PhotoFlightOverlay
import com.punctum.gallery.ui.photoMotionBitmap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalContext
import com.punctum.gallery.ui.SharedPhotoMotion
import com.punctum.gallery.ui.SHARED_MOTION_MILLIS
import com.punctum.gallery.ui.DETAIL_MOTION_MILLIS
import com.punctum.gallery.ui.DetailMotionEase
import com.punctum.gallery.ui.navigationMotionGuard
import com.punctum.gallery.ui.SharedMotionEase
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

private enum class HomeGalleryTransitionMode {
    LEGACY,
    LAYERED,
}

// One-switch rollback: LEGACY restores the original instant page reveal below.
private val HomeGalleryTransition = HomeGalleryTransitionMode.LAYERED
private const val HOME_GALLERY_ENTER_DURATION_MILLIS = SHARED_MOTION_MILLIS
private const val HOME_GALLERY_EXIT_DURATION_MILLIS = SHARED_MOTION_MILLIS
private val HomeGalleryEnterOffset = 8.dp
private val HomeGalleryEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

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
    val homeAnchors = remember { mutableMapOf<String, Rect>() }
    var galleryOrigin by remember { mutableStateOf<Rect?>(null) }
    var detailClosing by remember { mutableStateOf(false) }
    var activePhotoID by remember { mutableStateOf<String?>(null) }
    var detailOpening by remember { mutableStateOf(false) }
    var photoFlight by remember { mutableStateOf<PhotoFlight?>(null) }
    val context = LocalContext.current
    val current = vm.currentGallery
    val currentKey = current?.uri?.toString()
    val listPhotoBounds = remember(currentKey) { mutableStateMapOf<String, Rect>() }
    val detailPhotoBounds = remember(currentKey) { mutableStateMapOf<String, Rect>() }

    LaunchedEffect(vm.selectedIndex, vm.detailReturnPending) {
        if (vm.selectedIndex == null) {
            photoFlight = null
            detailOpening = false
            detailClosing = false
            return@LaunchedEffect
        }
        if (!detailOpening || vm.detailReturnPending) return@LaunchedEffect
        val flight = photoFlight ?: return@LaunchedEffect
        val target = kotlinx.coroutines.withTimeoutOrNull(500) {
            snapshotFlow { detailPhotoBounds[flight.photoID] }.first { it != null }
        }
        if (target != null) {
            androidx.compose.runtime.withFrameNanos { }
            flight.destination = detailPhotoBounds[flight.photoID] ?: target
            // The detail page now moves as one picture-and-information group.
            flight.progress.animateTo(1f, tween(DETAIL_MOTION_MILLIS, easing = DetailMotionEase))
        }
        photoFlight = null
        detailOpening = false
    }
    var renameTarget by remember { mutableStateOf<Gallery?>(null) }
    var showAlbumPicker by remember { mutableStateOf(false) }
    var readyGalleryKey by remember(currentKey) { mutableStateOf<String?>(null) }
    var galleryExitInProgress by remember(currentKey) { mutableStateOf(false) }
    val transitionScope = rememberCoroutineScope()
    val galleryTransitionProgress = remember(currentKey) { Animatable(0f) }
    val density = LocalDensity.current
    val galleryEnterOffsetPx = with(density) { HomeGalleryEnterOffset.toPx() }
    val layeredHomeGalleryMotion =
        HomeGalleryTransition == HomeGalleryTransitionMode.LAYERED &&
            ValueAnimator.areAnimatorsEnabled()
    val postcardListState = rememberLazyListState()
    val ticketListState = rememberLazyListState()
    val reversalFilmGridState = rememberLazyGridState()
    val homeToast = vm.homeToast
    LaunchedEffect(homeToast) {
        if (homeToast == null) return@LaunchedEffect
        delay(if (homeToast == GalleryViewModel.PHOTO_SORT_HINT) 2000 else 2200)
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

    LaunchedEffect(currentKey, readyGalleryKey, layeredHomeGalleryMotion) {
        if (currentKey == null || readyGalleryKey != currentKey) return@LaunchedEffect
        if (layeredHomeGalleryMotion) {
            galleryTransitionProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = HOME_GALLERY_ENTER_DURATION_MILLIS,
                    easing = HomeGalleryEaseOut,
                ),
            )
        } else {
            galleryTransitionProgress.snapTo(1f)
        }
    }

    fun requestHome() {
        if (currentKey == null || galleryExitInProgress) return
        if (!layeredHomeGalleryMotion) {
            vm.goHome()
            return
        }
        galleryExitInProgress = true
        transitionScope.launch {
            galleryTransitionProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = HOME_GALLERY_EXIT_DURATION_MILLIS,
                    easing = HomeGalleryEaseOut,
                ),
            )
            if (vm.currentUri == currentKey) vm.goHome()
            galleryExitInProgress = false
        }
    }

    CompositionLocalProvider(
        LocalHomeAnchors provides homeAnchors,
        LocalSharedPhotoMotion provides SharedPhotoMotion(
            listPhotoBounds, detailPhotoBounds, photoFlight,

        ),
    ) {
    Box(modifier = Modifier.fillMaxSize().background(Ink)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Keep the home surface mounted underneath the gallery during
                    // the transition so a cancelled/interrupted animation cannot
                    // expose the Ink window background.
                    alpha = 1f
                },
        ) {
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
                    onSelect = { id ->
                        galleryOrigin = homeAnchors[id]
                        vm.selectGallery(id)
                    },
                    onAdd = { showAlbumPicker = true },
                    onToggleInvitationStyle = vm::toggleInvitationStyle,
                    onRename = { renameTarget = it },
                    onMove = vm::moveGallery,
                    onDelete = vm::removeGallery,
                    onClose = vm::closeSwitcher,
                )
            }
        }

        if (current != null && currentKey != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationMotionGuard(galleryExitInProgress || readyGalleryKey != currentKey)
                    .graphicsLayer {
                        alpha = when {
                            readyGalleryKey != currentKey -> 0f
                            layeredHomeGalleryMotion -> galleryTransitionProgress.value
                            else -> 1f
                        }
                        val origin = galleryOrigin
                        if (layeredHomeGalleryMotion && origin != null && size.width > 0 && size.height > 0) {
                            val amount = galleryTransitionProgress.value
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = origin.width / size.width + (1f - origin.width / size.width) * amount
                            scaleY = origin.height / size.height + (1f - origin.height / size.height) * amount
                            translationX = origin.left * (1f - amount)
                            translationY = origin.top * (1f - amount)
                            clip = true
                        } else {
                            translationY = if (layeredHomeGalleryMotion) (1f - galleryTransitionProgress.value) * galleryEnterOffsetPx else 0f
                        }
                    }
                    .background(Ink),
            ) {
                key(currentKey) {
                    val listState = rememberLazyListState()
                    var detailEntryVisibleIDs by remember { mutableStateOf(emptySet<String>()) }
                    val listDragged by listState.interactionSource.collectIsDraggedAsState()
                    val returnRow = galleryReturnRow(
                        vm.photos.map { it.uri.toString() }, vm.galleryReturnPhotoID,
                    )
                    LaunchedEffect(listDragged) {
                        if (listDragged && vm.selectedIndex == null) vm.clearGalleryReturnPosition()
                    }
                    LaunchedEffect(returnRow, vm.detailReturnPending, vm.selectedIndex, vm.loadingPhotos) {
                        if (vm.loadingPhotos) return@LaunchedEffect
                        if (vm.detailReturnPending) {
                            // Keep detail fully visible until the destination has been laid out.
                            if (returnRow != null && shouldCenterGalleryReturn(vm.galleryReturnPhotoID, detailEntryVisibleIDs)) {
                                listState.centerGalleryReturnRow(returnRow)
                            }
                            androidx.compose.runtime.withFrameNanos { }
                            androidx.compose.runtime.withFrameNanos { }
                            if (ValueAnimator.areAnimatorsEnabled()) {
                                val id = activePhotoID
                                val photo = vm.photos.firstOrNull { it.uri.toString() == id }
                                val interrupted = photoFlight?.takeIf { it.photoID == id }
                                val source = interrupted?.bounds ?: detailPhotoBounds[id]
                                val anchor = interrupted?.let { it.detailAnchor ?: it.destination }
                                    ?: detailPhotoBounds[id]
                                val target = listPhotoBounds[id]
                                val bitmap = photo?.let { photoMotionBitmap(context, it, detail = true) }
                                val flight = if (id != null && id == vm.galleryReturnPhotoID &&
                                    source != null && target != null && anchor != null && bitmap != null) {
                                    PhotoFlight(id, bitmap, source, unifiedContent = true, detailAnchor = anchor)
                                        .also { it.destination = target }
                                } else null
                                photoFlight = flight
                                detailOpening = false
                                detailClosing = true
                                if (flight != null) {
                                    flight.progress.animateTo(1f, tween(DETAIL_MOTION_MILLIS, easing = DetailMotionEase))
                                } else delay(DETAIL_MOTION_MILLIS.toLong())
                            }
                            vm.finishDetailReturn()
                            photoFlight = null
                            detailClosing = false
                        } else if (vm.selectedIndex == null && returnRow != null &&
                            shouldCenterGalleryReturn(vm.galleryReturnPhotoID, detailEntryVisibleIDs)) {
                            listState.centerGalleryReturnRow(returnRow)
                        }
                    }
                    GalleryScreen(
                        gallery = current,
                        photos = vm.photos,
                        overview = vm.overviews[currentKey],
                        loading = vm.loadingPhotos,
                        sorting = vm.sortingPhotos,
                        listState = listState,
                        onOpenSwitcher = ::requestHome,
                        onRename = { renameTarget = it },
                        onToggleSort = vm::togglePhotoSort,
                        onSelectPhoto = { index ->
                            // Capture photo identities, including partly visible rows, before opening detail.
                            val layout = listState.layoutInfo
                            detailEntryVisibleIDs = layout.visibleItemsInfo
                                .filter { it.offset < layout.viewportEndOffset &&
                                    it.offset + it.size > layout.viewportStartOffset }
                                .flatMap { item ->
                                    val start = (item.index - 1) * 2
                                    if (start < 0) emptyList() else (start..start + 1).mapNotNull {
                                        vm.photos.getOrNull(it)?.uri?.toString()
                                    }
                                }.toSet()
                            if (!vm.sortingPhotos) {
                                val photo = vm.photos.getOrNull(index)
                                val id = photo?.uri?.toString()
                                val source = listPhotoBounds[id]
                                val bitmap = photo?.let { photoMotionBitmap(context, it, detail = false) }
                                detailClosing = false
                                activePhotoID = id
                                // Discard old off-screen pager geometry before a new visit.
                                detailPhotoBounds.clear()
                                photoFlight = if (ValueAnimator.areAnimatorsEnabled() && id != null &&
                                    source != null && bitmap != null) PhotoFlight(id, bitmap, source, unifiedContent = true) else null
                                detailOpening = photoFlight != null
                                transitionScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                                    vm.openDetail(index)
                                }
                            }
                        },
                        onDeletePhoto = vm::deletePhoto,
                        onWarmThumbnails = vm::warmGalleryThumbnails,
                        onPauseThumbnails = vm::pauseGalleryThumbnails,
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
            val detailAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (detailClosing) 0f else 1f,
                animationSpec = tween(DETAIL_MOTION_MILLIS, easing = DetailMotionEase), label = "detail-background",
            )
            Box(Modifier.fillMaxSize().navigationMotionGuard(detailOpening || detailClosing || vm.detailReturnPending).graphicsLayer {
                val flight = photoFlight
                alpha = if (detailClosing && flight?.unifiedContent == true) 1f else detailAlpha
            }) {
            DetailScreen(
                photos = vm.photos,
                startIndex = detailIndex,
                currentAlbumKey = currentKey,
                availableSystemAlbums = vm.systemAlbums,
                completedMove = vm.completedMove,
                moveError = vm.moveError,
                onDelete = vm::queueDetailDeletion,
                onClose = vm::closeDetail,
                onPhotoViewed = { photo -> activePhotoID = photo.uri.toString(); vm.recordDetailPhoto(photo) },
                onWarmImages = vm::warmDetailImages,
                onRequestSystemAlbums = vm::loadSystemAlbums,
                onMovePhoto = vm::movePhoto,
                onAcknowledgeMove = vm::acknowledgeCompletedMove,
                onClearMoveError = vm::clearMoveError,
            )
            }
        }

        photoFlight?.takeIf { !it.unifiedContent || (it.detailAnchor == null && it.destination == it.source) }
            ?.let { PhotoFlightOverlay(it) }

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

    }

    val galleryLoading = currentKey != null && (vm.loadingPhotos || readyGalleryKey != currentKey)
    var showGalleryLoading by remember(currentKey, galleryLoading) { mutableStateOf(false) }
    LaunchedEffect(currentKey, galleryLoading) {
        if (galleryLoading) {
            delay(500)
            showGalleryLoading = true
        }
    }
    if (galleryLoading && showGalleryLoading) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = vm::cancelGalleryLoading,
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        ) {
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                color = Surface1,
                modifier = Modifier.width(272.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(28.dp), color = Gold, strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "项目数量较多，加载中",
                        color = Bone, fontSize = 14.sp, lineHeight = 21.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = vm::cancelGalleryLoading) {
                        Text("取消", color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    BackHandler(enabled = vm.selectedIndex != null || vm.showSwitcher || vm.currentGallery != null) {
        when {
            galleryLoading -> vm.cancelGalleryLoading()
            vm.selectedIndex != null -> vm.closeDetail()
            vm.showSwitcher -> vm.closeSwitcher()
            vm.currentGallery != null -> requestHome()
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

/** Resolve the row's actual height before dismissing detail; preserve normal list bounds. */
private suspend fun androidx.compose.foundation.lazy.LazyListState.centerGalleryReturnRow(row: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == row }) {
        scrollToItem(row)
        androidx.compose.runtime.withFrameNanos { }
    }
    val layout = layoutInfo
    val item = layout.visibleItemsInfo.firstOrNull { it.index == row } ?: return
    scrollBy(galleryReturnCenterDelta(
        item.offset, item.size, layout.viewportStartOffset, layout.viewportEndOffset,
    ))
}
