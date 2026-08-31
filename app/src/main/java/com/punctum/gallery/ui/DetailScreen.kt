package com.punctum.gallery.ui

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.PixelCopy
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.punctum.gallery.CompletedPhotoMove
import com.punctum.gallery.data.PhotoRepository
import com.punctum.gallery.data.MotionPhotoService
import com.punctum.gallery.data.PhotoStill
import com.punctum.gallery.model.Photo
import com.punctum.gallery.model.SystemAlbum
import com.punctum.gallery.ui.theme.Bone
import com.punctum.gallery.ui.theme.DetailSerif
import com.punctum.gallery.ui.theme.Gold
import com.punctum.gallery.ui.theme.Ink
import com.punctum.gallery.ui.theme.Muted
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import coil.request.ImageRequest
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.coroutines.resume

@Composable
internal fun DetailScreen(
    photos: List<Photo>,
    startIndex: Int,
    currentAlbumKey: String?,
    completedMove: CompletedPhotoMove?,
    moveError: String?,
    onDelete: (Photo) -> Unit,
    onClose: () -> Unit,
    onWarmImages: (Int) -> Unit,
    onMovePhoto: (Photo, SystemAlbum) -> Unit,
    onAcknowledgeMove: () -> Unit,
    onClearMoveError: () -> Unit,
) {
    if (photos.isEmpty()) return
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val hostView = LocalView.current
    val scope = rememberCoroutineScope()
    var controlsVisible by remember { mutableStateOf(false) }
    var showMovePicker by remember { mutableStateOf(false) }
    var centerToast by remember { mutableStateOf<String?>(null) }
    var savingPhoto by remember { mutableStateOf(false) }
    var deleteToastVisible by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val deleteThreshold = with(density) { 180.dp.toPx() }
    var deleteProgress by remember { mutableStateOf(0f) }
    val displayedDetailUris = remember { mutableSetOf<String>() }
    val progress = deleteProgress
    val armed = progress >= 0.72f
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, photos.size - 1),
    ) { photos.size }
    val currentPhoto = photos.getOrNull(pagerState.currentPage)
    val nextDeletePhoto = photos.getOrNull((pagerState.currentPage + 1).coerceAtMost(photos.lastIndex))
    var activeDeletePhoto by remember { mutableStateOf<Photo?>(null) }
    var settlingPhoto by remember { mutableStateOf<Photo?>(null) }
    var livePlaybackActive by remember { mutableStateOf(false) }
    var moveSlide by remember { mutableStateOf(0f) }
    var movingFrom by remember { mutableStateOf<Photo?>(null) }
    var movingTo by remember { mutableStateOf<Photo?>(null) }
    val livePlayback by rememberUpdatedState(livePlaybackActive)
    val showCenterMessage: (String) -> Unit = { message ->
        centerToast = message
        scope.launch {
            delay(2400)
            if (centerToast == message) centerToast = null
        }
    }

    LaunchedEffect(pagerState, photos) {
        onWarmImages(pagerState.currentPage)
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onWarmImages(page)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        livePlaybackActive = false
    }

    LaunchedEffect(completedMove) {
        val move = completedMove ?: return@LaunchedEffect
        showMovePicker = false
        movingFrom = move.photo
        movingTo = move.nextPhoto
        try {
            if (move.nextPhoto != null) {
                val anim = Animatable(0f)
                anim.animateTo(1f, tween(durationMillis = 300, easing = FastOutSlowInEasing)) {
                    moveSlide = value
                }
            }
            val closingAfterMove = move.nextPhoto == null
            val destinationName = move.destination.displayName
            onAcknowledgeMove()
            if (!closingAfterMove) {
                showCenterMessage("该项目已移动到 $destinationName")
            }
        } finally {
            moveSlide = 0f
            movingFrom = null
            movingTo = null
        }
    }

    LaunchedEffect(moveError) {
        val message = moveError ?: return@LaunchedEffect
        showCenterMessage(message)
        onClearMoveError()
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("punctum_tutorial", Context.MODE_PRIVATE)
        if (prefs.getInt("quick_page_toast_count", 0) < 2) {
            delay(500)
            centerToast = "单击屏幕左/右边缘，支持快速切换前/后图片"
            prefs.edit().putInt("quick_page_toast_count", prefs.getInt("quick_page_toast_count", 0) + 1).apply()
            delay(4000)
            centerToast = null
        }
        if (prefs.getInt("swipe_delete_toast_count", 0) < 2) {
            delay(1000)
            centerToast = "上滑页面，支持快速删除图片"
            prefs.edit().putInt("swipe_delete_toast_count", prefs.getInt("swipe_delete_toast_count", 0) + 1).apply()
            delay(4000)
            centerToast = null
        }
    }

    LaunchedEffect(armed) {
        if (armed) {
            val prefs = context.getSharedPreferences("punctum_tutorial", Context.MODE_PRIVATE)
            val count = prefs.getInt("delete_red_toast_count", 0)
            deleteToastVisible = count < 2
            if (count < 2) {
                prefs.edit().putInt("delete_red_toast_count", count + 1).apply()
            }
        } else {
            deleteToastVisible = false
        }
    }

    DisposableEffect(activity, controlsVisible) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (controlsVisible) {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller?.hide(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .pointerInput(photos, pagerState.currentPage) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var total = Offset.Zero
                    var deletingGesture = false
                    deleteProgress = 0f
                    activeDeletePhoto = null

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (livePlayback) {
                            if (!change.pressed) break
                            continue
                        }
                        if (!change.pressed) {
                            if (deletingGesture) {
                                val currentPage = pagerState.currentPage
                                val targetPhoto = photos.getOrNull(currentPage)
                                val currentProgress = deleteProgress
                                scope.launch {
                                    val anim = Animatable(currentProgress)
                                    if (currentProgress >= 0.72f) {
                                        activeDeletePhoto = targetPhoto
                                        nextDeletePhoto?.let {
                                            displayedDetailUris.add(it.uri.toString())
                                            settlingPhoto = it
                                        }
                                        anim.animateTo(1.55f, tween(durationMillis = 260)) {
                                            deleteProgress = value
                                        }
                                        targetPhoto?.let { photo ->
                                            onDelete(photo)
                                            hostView.performDeleteHaptic()
                                        }
                                        deleteProgress = 0f
                                        activeDeletePhoto = null
                                        delay(220)
                                        settlingPhoto = null
                                    } else {
                                        anim.animateTo(0f, tween(durationMillis = 235)) {
                                            deleteProgress = value
                                        }
                                        activeDeletePhoto = null
                                    }
                                }
                            }
                            break
                        }

                        val delta = change.positionChange()
                        total += delta
                        val upwardDistance = (-total.y).coerceAtLeast(0f)

                        if (!deletingGesture) {
                            val verticalIntent = abs(total.y) > 8f && abs(total.y) > abs(total.x) * 1.2f
                            deletingGesture = total.y < 0f && verticalIntent
                        }

                        if (deletingGesture) {
                            if (activeDeletePhoto == null) {
                                activeDeletePhoto = photos.getOrNull(pagerState.currentPage)
                            }
                            deleteProgress = (upwardDistance / deleteThreshold).coerceIn(0f, 1.55f)
                            change.consume()
                        }
                    }
                }
            },
    ) {
        if (progress > 0f && nextDeletePhoto != null) {
            val reveal = ((progress - 0.22f) / 1.33f).coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize()) {
                ImmersivePhoto(
                    photo = nextDeletePhoto,
                    displayNumber = (pagerState.currentPage + 2).coerceAtMost(photos.size),
                    onTapLeft = {},
                    onTapRight = {},
                    onToggleControls = {},
                    animateImage = false,
                    onImageVisible = { displayedDetailUris.add(nextDeletePhoto.uri.toString()) },
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.48f * (1f - reveal))),
                )
            }
        }

        val moveFrom = movingFrom ?: completedMove?.photo
        val moveTo = movingTo ?: completedMove?.nextPhoto
        val showMoveOverlay = completedMove != null || moveSlide > 0f

        if (showMoveOverlay && moveFrom != null && moveTo != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = screenWidthPx * (1f - moveSlide) }
                    .background(Ink),
            ) {
                ImmersivePhoto(
                    photo = moveTo,
                    displayNumber = (pagerState.currentPage + 2).coerceAtMost(photos.size),
                    onTapLeft = {},
                    onTapRight = {},
                    onToggleControls = {},
                    animateImage = false,
                    onImageVisible = { displayedDetailUris.add(moveTo.uri.toString()) },
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = -screenWidthPx * moveSlide }
                    .background(Ink),
            ) {
                ImmersivePhoto(
                    photo = moveFrom,
                    displayNumber = pagerState.currentPage + 1,
                    onTapLeft = {},
                    onTapRight = {},
                    onToggleControls = {},
                    animateImage = false,
                    onImageVisible = { displayedDetailUris.add(moveFrom.uri.toString()) },
                )
            }
        } else if (progress == 0f) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !livePlaybackActive,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Ink),
                ) {
                    ImmersivePhoto(
                        photo = photos[page],
                        displayNumber = page + 1,
                        onTapLeft = {
                            if (pagerState.currentPage > 0) {
                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    pagerState.scrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        },
                        onTapRight = {
                            if (pagerState.currentPage < photos.lastIndex) {
                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    pagerState.scrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        onToggleControls = { controlsVisible = !controlsVisible },
                        animateImage = photos[page].uri.toString() !in displayedDetailUris,
                        onImageVisible = { displayedDetailUris.add(photos[page].uri.toString()) },
                        onLivePlaybackChanged = { livePlaybackActive = it },
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .deletePageTransform(progress, density)
                    .background(Ink),
            ) {
                (activeDeletePhoto ?: currentPhoto)?.let { photo ->
                    ImmersivePhoto(
                        photo = photo,
                        displayNumber = pagerState.currentPage + 1,
                        onTapLeft = {},
                        onTapRight = {},
                        onToggleControls = {},
                        animateImage = false,
                        onImageVisible = { displayedDetailUris.add(photo.uri.toString()) },
                    )
                }
            }
        }

        settlingPhoto?.let { photo ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Ink),
            ) {
                ImmersivePhoto(
                    photo = photo,
                    displayNumber = (pagerState.currentPage + 1).coerceAtMost(photos.size),
                    onTapLeft = {},
                    onTapRight = {},
                    onToggleControls = {},
                    animateImage = false,
                    onImageVisible = { displayedDetailUris.add(photo.uri.toString()) },
                )
            }
        }

        AnimatedVisibility(
            visible = progress > 0f,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 58.dp),
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (armed) Color(0xFFE24646) else Color(0xFF2C2C2C).copy(alpha = 0.78f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "上滑删除",
                        tint = if (armed) Color.White else Bone.copy(alpha = 0.72f),
                        modifier = Modifier.size(27.dp),
                    )
                }
                AnimatedVisibility(
                    visible = armed && deleteToastVisible,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 76.dp),
                    enter = fadeIn(tween(140)),
                    exit = fadeOut(tween(120)),
                ) {
                    Text(
                        "将照片删除到系统相册回收站",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Bone.copy(alpha = 0.78f),
                        modifier = Modifier
                            .background(Color(0xFF191919).copy(alpha = 0.78f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = centerToast != null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160)),
        ) {
            Text(
                centerToast.orEmpty(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Bone.copy(alpha = 0.86f),
                modifier = Modifier
                    .background(Color(0xFF191919).copy(alpha = 0.82f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PressFeedbackIconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Bone)
                }
                Spacer(Modifier.weight(1f))
                currentPhoto?.let { photo ->
                    PressFeedbackIconButton(
                        onClick = {
                            if (!openPhotoInLightroom(context, photo)) {
                                showCenterMessage("请下载 Lightroom 后使用编辑")
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "使用 Lightroom 编辑", tint = Bone)
                    }
                    PressFeedbackIconButton(
                        enabled = !savingPhoto,
                        onClick = {
                            scope.launch {
                                savingPhoto = true
                                centerToast = null
                                controlsVisible = false
                                delay(220)
                                val saved = activity?.let {
                                    saveDetailPageToPunctumAlbum(context, it)
                                } == true
                                savingPhoto = false
                                showCenterMessage(
                                    if (saved) "已保存当前页面到系统相册 Punctum"
                                    else "保存失败，请稍后重试",
                                )
                            }
                        },
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = "保存到 Punctum 图集",
                            tint = if (savingPhoto) Muted else Bone,
                        )
                    }
                    PressFeedbackIconButton(
                        onClick = { showMovePicker = true },
                        modifier = Modifier.semantics { contentDescription = "移动到其他图集" },
                    ) {
                        MoveToAlbumIcon(
                            tint = Bone,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                }
            }
        }
    }

    if (showMovePicker) {
        currentPhoto?.let { photo ->
            MoveAlbumPickerDialog(
                currentAlbumKey = currentAlbumKey,
                onPick = { album ->
                    showMovePicker = false
                    onMovePhoto(photo, album)
                },
                onDismiss = { showMovePicker = false },
            )
        }
    }
}

private fun Modifier.deletePageTransform(progress: Float, density: Density): Modifier {
    val base = progress.coerceIn(0f, 1f)
    val extra = ((progress - 1f).coerceIn(0f, 0.55f)) / 0.55f
    val scale = 1f - base * 0.28f - extra * 0.26f
    val yDp = -base * 132f - extra * 58f
    val radiusDp = base * 24f + extra * 10f
    val shadowDp = base * 44f + extra * 18f
    val rotation = -base * 1.2f - extra * 1.4f
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        translationY = with(density) { yDp.dp.toPx() }
        rotationZ = rotation
        shape = RoundedCornerShape(radiusDp.dp)
        clip = true
        shadowElevation = with(density) { (10f + shadowDp).dp.toPx() }
    }
}

@Composable
private fun ImmersivePhoto(
    photo: Photo,
    displayNumber: Int,
    onTapLeft: () -> Unit,
    onTapRight: () -> Unit,
    onToggleControls: () -> Unit,
    animateImage: Boolean,
    onImageVisible: () -> Unit,
    onLivePlaybackChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp
    val density = LocalDensity.current
    val hostView = LocalView.current
    var placeName by remember(photo.uri) { mutableStateOf<String?>(null) }
    var motionPlaybackMode by remember(photo.uri) {
        mutableStateOf(MotionPlaybackMode.NONE)
    }
    val motionPlayback = remember(photo.uri) { MotionPhotoPlayback() }
    val notifyLivePlayback by rememberUpdatedState(onLivePlaybackChanged)
    fun setMotionPlayback(mode: MotionPlaybackMode) {
        if (mode == MotionPlaybackMode.NONE) motionPlayback.halt()
        motionPlaybackMode = mode
        notifyLivePlayback(mode != MotionPlaybackMode.NONE)
    }
    val aspect = photo.aspectRatio.coerceAtLeast(0.1f)
    val imageHeight = screenWidth / aspect
    val imageTop = if (aspect < 1f) {
        0.dp
    } else {
        (screenHeight * 0.5f - 40.dp - imageHeight * 0.5f).coerceAtLeast(0.dp)
    }
    val imageTopPx = with(density) { imageTop.toPx() }
    val imageBottomPx = with(density) { (imageTop + imageHeight).toPx() }
    val badgeHotWidthPx = with(density) { LIVE_BADGE_HOT_WIDTH.toPx() }
    val badgeHotHeightPx = with(density) { LIVE_BADGE_HOT_HEIGHT.toPx() }

    LaunchedEffect(photo.uri) {
        placeName = photo.latLong?.let { PhotoRepository.reverseGeocode(context, it[0], it[1]) }
    }

    var motionVideoFile by remember(photo.uri) { mutableStateOf<File?>(null) }
    var videoHasFrame by remember(photo.uri) { mutableStateOf(false) }
    LaunchedEffect(photo.uri, photo.motionPhotoVideoLength, motionPlaybackMode) {
        if (motionPlaybackMode == MotionPlaybackMode.NONE || !photo.isMotionPhoto) return@LaunchedEffect
        if (motionVideoFile == null) {
            motionVideoFile = MotionPhotoService.videoFile(context, photo)
        }
    }
    val playbackActive =
        motionPlaybackMode != MotionPlaybackMode.NONE && motionVideoFile != null
    val fadeIn = playbackActive && videoHasFrame
    val animatedBlend by animateFloatAsState(
        targetValue = if (fadeIn) 1f else 0f,
        animationSpec = if (fadeIn) {
            tween(durationMillis = LIVE_PHOTO_CROSSFADE_MILLIS)
        } else {
            snap()
        },
        label = "motion-photo-crossfade",
    )
    val videoBlend = if (fadeIn) animatedBlend else 0f

    DisposableEffect(photo.uri) {
        onDispose {
            motionPlayback.halt()
            notifyLivePlayback(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(
                photo.uri,
                photo.isMotionPhoto,
                imageTopPx,
                imageBottomPx,
            ) {
                coroutineScope {
                    var gestureGeneration = 0
                    awaitEachGesture {
                        val thisGesture = ++gestureGeneration
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val scrollOffset = scrollState.value.toFloat()
                        val displayedImageBottom = imageBottomPx - scrollOffset
                        val startedInImage =
                            down.position.x in 0f..size.width.toFloat() &&
                                down.position.y in
                                (imageTopPx - scrollOffset)..(imageBottomPx - scrollOffset)
                        val startedOnLiveBadge =
                            photo.isMotionPhoto &&
                                startedInImage &&
                                down.position.x >= size.width - badgeHotWidthPx &&
                                down.position.y >= displayedImageBottom - badgeHotHeightPx
                        val ignoreGesture =
                            motionPlaybackMode == MotionPlaybackMode.PLAY_ONCE
                        var pressed = true
                        var moved = false
                        var playbackStarted = false
                        var total = Offset.Zero
                        var tapPosition = down.position
                        val longPressJob = if (
                            photo.isMotionPhoto &&
                            startedInImage &&
                            !startedOnLiveBadge &&
                            !ignoreGesture
                        ) {
                            launch {
                                delay(LIVE_PHOTO_HOLD_MILLIS)
                                if (thisGesture != gestureGeneration || !pressed || moved) {
                                    return@launch
                                }
                                playbackStarted = true
                                setMotionPlayback(MotionPlaybackMode.HOLD)
                                hostView.performHapticFeedback(
                                    HapticFeedbackConstants.LONG_PRESS,
                                )
                            }
                        } else {
                            null
                        }

                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change =
                                    event.changes.firstOrNull { it.id == down.id } ?: break
                                tapPosition = change.position
                                total += change.positionChange()
                                if (total.getDistance() > viewConfiguration.touchSlop) {
                                    moved = true
                                    if (!playbackStarted) longPressJob?.cancel()
                                }
                                if (playbackStarted) change.consume()

                                if (!change.pressed) {
                                    pressed = false
                                    longPressJob?.cancel()
                                    if (playbackStarted) {
                                        setMotionPlayback(MotionPlaybackMode.NONE)
                                    } else if (!moved && !ignoreGesture) {
                                        if (startedOnLiveBadge) {
                                            setMotionPlayback(MotionPlaybackMode.PLAY_ONCE)
                                        } else {
                                            when {
                                                tapPosition.x < size.width * 0.25f -> onTapLeft()
                                                tapPosition.x > size.width * 0.75f -> onTapRight()
                                                else -> onToggleControls()
                                            }
                                        }
                                    }
                                    break
                                }
                            }
                        } finally {
                            pressed = false
                            gestureGeneration += 1
                            longPressJob?.cancel()
                            if (playbackStarted && motionPlaybackMode == MotionPlaybackMode.HOLD) {
                                setMotionPlayback(MotionPlaybackMode.NONE)
                            }
                        }
                    }
                }
            },
    ) {
        motionVideoFile?.let { videoFile ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .offset {
                        IntOffset(0, (imageTopPx - scrollState.value).roundToInt())
                    }
                    .clipToBounds(),
            ) {
                MotionPhotoVideo(
                    videoFile = videoFile,
                    playing = playbackActive,
                    loop = motionPlaybackMode == MotionPlaybackMode.HOLD,
                    playback = motionPlayback,
                    onPlaybackComplete = {
                        if (motionPlaybackMode == MotionPlaybackMode.PLAY_ONCE) {
                            setMotionPlayback(MotionPlaybackMode.NONE)
                        }
                    },
                    onFirstFrameRendered = { videoHasFrame = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            DetailPhotoFrame(
                photo = photo,
                screenHeight = screenHeight,
                animateImage = animateImage,
                onImageVisible = onImageVisible,
                motionPlaybackMode = motionPlaybackMode,
                videoBlend = videoBlend,
                onBadgeClick = {
                    setMotionPlayback(MotionPlaybackMode.PLAY_ONCE)
                },
            )

            Spacer(Modifier.height(34.dp))

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp)) {
                Text(
                    "No.$displayNumber",
                    style = DetailTitleStyle,
                    color = Bone,
                )
                Spacer(Modifier.height(18.dp))

                val location = placeName ?: photo.coordinateText
                if (!location.isNullOrBlank()) {
                    Text(location, style = DetailTextStyle, color = Bone)
                }
                photo.dateTaken?.let {
                    Text(it, style = DetailTextStyle, color = Bone)
                }

                Spacer(Modifier.height(19.dp))

                DetailLine("Camera", photo.device)
                DetailLine("Exposure Time", photo.shutter)
                DetailLine("Focal Length", photo.focalLength)
                DetailLine("Aperture", displayAperture(photo.aperture))
                DetailLine("ISO", photo.iso)
                DetailLine("Resolution", photo.resolutionText)
                DetailLine("File Size", photo.fileSizeText)

                Spacer(Modifier.navigationBarsPadding().height(86.dp))
            }
        }
    }
}

private fun displayAperture(raw: String?): String? =
    raw?.trim()
        ?.replace(Regex("^f/", RegexOption.IGNORE_CASE), "F")
        ?.replace(Regex("^f", RegexOption.IGNORE_CASE), "F")
        ?.ifBlank { null }

@Composable
private fun DetailLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Text(
        "$label: $value",
        style = DetailTextStyle,
        color = Bone,
    )
}

@Composable
private fun DetailPhotoFrame(
    photo: Photo,
    screenHeight: Dp,
    animateImage: Boolean,
    onImageVisible: () -> Unit,
    motionPlaybackMode: MotionPlaybackMode,
    videoBlend: Float,
    onBadgeClick: () -> Unit,
) {
    val context = LocalContext.current
    val imageModel = remember(photo.uri, photo.stillImageByteCount) {
        ImageRequest.Builder(context)
            .data(PhotoStill.forDetail(photo))
            .memoryCacheKey("detail:${photo.uri}")
            .diskCacheKey("detail:${photo.uri}")
            .size(1800)
            .crossfade(false)
            .build()
    }
    val aspect = photo.aspectRatio.coerceAtLeast(0.1f)
    if (aspect < 1f) {
        DetailImageContent(
            photo = photo,
            model = imageModel,
            animateOnLoad = animateImage,
            onSuccess = onImageVisible,
            motionPlaybackMode = motionPlaybackMode,
            videoBlend = videoBlend,
            onBadgeClick = onBadgeClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect),
        )
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val imageHeight = maxWidth / aspect
            val centerY = screenHeight * 0.5f - 40.dp
            val topPadding = (centerY - imageHeight * 0.5f).coerceAtLeast(0.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topPadding + imageHeight),
            ) {
                DetailImageContent(
                    photo = photo,
                    model = imageModel,
                    animateOnLoad = animateImage,
                    onSuccess = onImageVisible,
                    motionPlaybackMode = motionPlaybackMode,
                    videoBlend = videoBlend,
                    onBadgeClick = onBadgeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = topPadding)
                        .aspectRatio(aspect),
                )
            }
        }
    }
}

@Composable
private fun DetailImageContent(
    photo: Photo,
    model: Any,
    animateOnLoad: Boolean,
    onSuccess: () -> Unit,
    motionPlaybackMode: MotionPlaybackMode,
    videoBlend: Float,
    onBadgeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        PunctumImage(
            model = model,
            contentDescription = photo.name,
            contentScale = ContentScale.Fit,
            animateOnLoad = animateOnLoad,
            onSuccess = onSuccess,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - videoBlend },
        )
        if (photo.isMotionPhoto && motionPlaybackMode == MotionPlaybackMode.NONE) {
            LivePhotoBadge(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clickable(onClick = onBadgeClick)
                    .padding(4.dp),
            )
        }
    }
}

@Composable
private fun MotionPhotoVideo(
    videoFile: File,
    playing: Boolean,
    loop: Boolean,
    playback: MotionPhotoPlayback,
    onPlaybackComplete: () -> Unit,
    onFirstFrameRendered: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnPlaybackComplete by rememberUpdatedState(onPlaybackComplete)
    val currentOnFirstFrameRendered by rememberUpdatedState(onFirstFrameRendered)
    val textureView = remember(videoFile.path) {
        TextureView(context).apply {
            isOpaque = true
            isClickable = false
            isFocusable = false
        }
    }

    LaunchedEffect(playing, loop) {
        if (playing) playback.play(loop) else playback.halt()
    }

    DisposableEffect(textureView, videoFile.path) {
        val player = MediaPlayer()
        var surface: Surface? = null
        var dataSourceSet = false
        var firstFrameNotified = false
        var videoWidth = 0
        var videoHeight = 0

        fun notifyFirstFrame() {
            if (firstFrameNotified) return
            firstFrameNotified = true
            currentOnFirstFrameRendered()
        }

        fun applyTransform(lockBufferSize: Boolean = false) {
            applyMotionVideoTransform(
                textureView = textureView,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                lockBufferSize = lockBufferSize,
            )
        }

        player.setOnPreparedListener {
            videoWidth = it.videoWidth
            videoHeight = it.videoHeight
            applyTransform(lockBufferSize = true)
            playback.onPrepared(it)
        }
        player.setOnVideoSizeChangedListener { _, width, height ->
            if (width <= 0 || height <= 0) return@setOnVideoSizeChangedListener
            if (width == videoWidth && height == videoHeight) return@setOnVideoSizeChangedListener
            videoWidth = width
            videoHeight = height
            applyTransform(lockBufferSize = true)
        }
        player.setOnInfoListener { _, what, _ ->
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                notifyFirstFrame()
            }
            false
        }
        player.setOnCompletionListener {
            if (!playback.looping) currentOnPlaybackComplete()
        }
        player.setOnErrorListener { _, _, _ ->
            currentOnPlaybackComplete()
            true
        }
        playback.attach(player)

        fun attachSurface(surfaceTexture: SurfaceTexture) {
            surface?.release()
            surface = Surface(surfaceTexture)
            player.setSurface(surface)
            applyTransform()
            if (!dataSourceSet) {
                dataSourceSet = true
                runCatching {
                    player.setDataSource(videoFile.absolutePath)
                    player.prepareAsync()
                }.onFailure {
                    currentOnPlaybackComplete()
                }
            } else {
                playback.restartIfNeeded()
            }
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surfaceTexture: SurfaceTexture,
                width: Int,
                height: Int,
            ) {
                attachSurface(surfaceTexture)
            }

            override fun onSurfaceTextureSizeChanged(
                surfaceTexture: SurfaceTexture,
                width: Int,
                height: Int,
            ) {
                applyTransform()
            }

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                runCatching { player.setSurface(null) }
                surface?.release()
                surface = null
                return true
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                notifyFirstFrame()
            }
        }
        textureView.surfaceTexture?.takeIf { textureView.isAvailable }?.let(::attachSurface)

        onDispose {
            textureView.surfaceTextureListener = null
            playback.detach(player)
            surface?.release()
            surface = null
            runCatching { player.reset() }
            runCatching { player.release() }
        }
    }
    AndroidView(
        factory = { textureView },
        modifier = modifier,
    )
}

private fun applyMotionVideoTransform(
    textureView: TextureView,
    videoWidth: Int,
    videoHeight: Int,
    lockBufferSize: Boolean,
) {
    val viewWidth = textureView.width
    val viewHeight = textureView.height
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return
    val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
    val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
    val matrix = Matrix()
    if (abs(viewAspect - videoAspect) >= 0.02f) {
        if (lockBufferSize) {
            runCatching {
                textureView.surfaceTexture?.setDefaultBufferSize(videoWidth, videoHeight)
            }
        }
        val scale = max(
            viewWidth / videoWidth.toFloat(),
            viewHeight / videoHeight.toFloat(),
        )
        matrix.setScale(scale, scale)
        matrix.postTranslate(
            (viewWidth - videoWidth * scale) / 2f,
            (viewHeight - videoHeight * scale) / 2f,
        )
    }
    textureView.setTransform(matrix)
}

private class MotionPhotoPlayback {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val applyState = Runnable { applyDesiredState() }
    private var player: MediaPlayer? = null
    @Volatile
    var enabled: Boolean = false
        private set
    @Volatile
    var looping: Boolean = false
        private set
    private var prepared: Boolean = false

    fun attach(player: MediaPlayer) {
        this.player = player
        player.isLooping = looping
        scheduleApply()
    }

    fun detach(player: MediaPlayer) {
        if (this.player !== player) return
        mainHandler.removeCallbacks(applyState)
        mute(player)
        runCatching { if (player.isPlaying) player.pause() }
        this.player = null
        prepared = false
    }

    fun play(loop: Boolean) {
        enabled = true
        looping = loop
        scheduleApply()
    }

    fun halt() {
        enabled = false
        mute(player)
        scheduleApply()
    }

    fun onPrepared(player: MediaPlayer) {
        if (this.player !== player) return
        prepared = true
        scheduleApply()
    }

    fun restartIfNeeded() {
        scheduleApply()
    }

    private fun scheduleApply() {
        mainHandler.removeCallbacks(applyState)
        mainHandler.post(applyState)
    }

    private fun applyDesiredState() {
        val current = player ?: return
        if (!prepared) return
        current.isLooping = looping
        if (enabled) {
            runCatching { current.setVolume(1f, 1f) }
            runCatching {
                if (!current.isPlaying) current.start()
            }
        } else {
            mute(current)
            runCatching { if (current.isPlaying) current.pause() }
        }
    }

    private fun mute(player: MediaPlayer?) {
        player ?: return
        runCatching { player.setVolume(0f, 0f) }
    }
}

@Composable
private fun LivePhotoBadge(modifier: Modifier = Modifier) {
    val ink = Color.White.copy(alpha = 0.82f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.38f), CircleShape)
            .padding(5.dp),
    ) {
        Canvas(Modifier.size(16.dp)) {
            val stroke = 1.1.dp.toPx()
            val dashedStroke = 1.dp.toPx()
            val centerDot = size.minDimension * 0.11f
            val solidRing = size.minDimension * 0.30f
            val dashedRing = size.minDimension * 0.44f
            val dash = 1.7.dp.toPx()
            val gap = 1.05.dp.toPx()
            drawCircle(color = ink, radius = centerDot)
            drawCircle(
                color = ink,
                radius = solidRing,
                style = Stroke(width = stroke),
            )
            drawCircle(
                color = ink,
                radius = dashedRing,
                style = Stroke(
                    width = dashedStroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap)),
                ),
            )
        }
    }
}

private enum class MotionPlaybackMode {
    NONE,
    HOLD,
    PLAY_ONCE,
}

private fun View.performDeleteHaptic() {
    val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(feedback)
}

private const val LIGHTROOM_PACKAGE = "com.adobe.lrmobile"
private const val EXPORT_LONG_EDGE = 1920
private const val LIVE_PHOTO_HOLD_MILLIS = 150L
private const val LIVE_PHOTO_CROSSFADE_MILLIS = 200
private val LIVE_BADGE_HOT_WIDTH = 44.dp
private val LIVE_BADGE_HOT_HEIGHT = 44.dp

private fun openPhotoInLightroom(context: Context, photo: Photo): Boolean {
    val mimeType = context.contentResolver.getType(photo.uri) ?: "image/*"
    val intent = Intent(Intent.ACTION_EDIT).apply {
        setDataAndType(photo.uri, mimeType)
        setPackage(LIGHTROOM_PACKAGE)
        clipData = ClipData.newUri(context.contentResolver, photo.name, photo.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    if (intent.resolveActivity(context.packageManager) == null) return false
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

private suspend fun saveDetailPageToPunctumAlbum(context: Context, activity: Activity): Boolean {
    val pageBitmap = captureDetailPage(activity) ?: return false
    return withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var insertedUri: android.net.Uri? = null
        var renderedBitmap: Bitmap? = null
        try {
            val rendered = pageBitmap.scaleLongEdgeTo(EXPORT_LONG_EDGE)
            renderedBitmap = rendered
            val fileName = "Punctum_${System.currentTimeMillis()}.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DCIM}/Punctum",
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                insertedUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext false
                resolver.openOutputStream(insertedUri, "w")?.use { output ->
                    check(rendered.compress(Bitmap.CompressFormat.JPEG, 95, output))
                } ?: error("Unable to open export destination")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                check(resolver.update(insertedUri, values, null, null) > 0)
            } else {
                @Suppress("DEPRECATION")
                val albumDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    "Punctum",
                )
                check(albumDir.exists() || albumDir.mkdirs())
                val outputFile = File(albumDir, fileName)
                FileOutputStream(outputFile).use { output ->
                    check(rendered.compress(Bitmap.CompressFormat.JPEG, 95, output))
                }
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    @Suppress("DEPRECATION")
                    put(MediaStore.Images.Media.DATA, outputFile.absolutePath)
                }
                insertedUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Unable to index exported image")
            }
            true
        } catch (_: Exception) {
            insertedUri?.let { uri -> runCatching { resolver.delete(uri, null, null) } }
            false
        } finally {
            renderedBitmap?.takeIf { it !== pageBitmap }?.recycle()
            pageBitmap.recycle()
        }
    }
}

private suspend fun captureDetailPage(activity: Activity): Bitmap? =
    withContext(Dispatchers.Main) {
        val contentView = activity.findViewById<View>(android.R.id.content)
            ?: return@withContext null
        if (contentView.width <= 0 || contentView.height <= 0) return@withContext null

        val location = IntArray(2)
        contentView.getLocationInWindow(location)
        val sourceRect = Rect(
            location[0],
            location[1],
            location[0] + contentView.width,
            location[1] + contentView.height,
        )
        val bitmap = Bitmap.createBitmap(
            sourceRect.width(),
            sourceRect.height(),
            Bitmap.Config.ARGB_8888,
        )

        suspendCancellableCoroutine { continuation ->
            try {
                PixelCopy.request(
                    activity.window,
                    sourceRect,
                    bitmap,
                    { result ->
                        if (!continuation.isActive) {
                            bitmap.recycle()
                        } else if (result == PixelCopy.SUCCESS) {
                            continuation.resume(bitmap)
                        } else {
                            bitmap.recycle()
                            continuation.resume(null)
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
            } catch (_: Exception) {
                bitmap.recycle()
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

private fun Bitmap.scaleLongEdgeTo(targetLongEdge: Int): Bitmap {
    val sourceLongEdge = max(width, height)
    if (sourceLongEdge <= 0 || sourceLongEdge == targetLongEdge) return this
    val scale = targetLongEdge.toFloat() / sourceLongEdge.toFloat()
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}

private val DetailLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

@Suppress("DEPRECATION")
private val DetailPlatformStyle = PlatformTextStyle(includeFontPadding = false)

private val DetailTitleStyle = TextStyle(
    fontFamily = DetailSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.sp,
    platformStyle = DetailPlatformStyle,
    lineHeightStyle = DetailLineHeightStyle,
)

private val DetailTextStyle = TextStyle(
    fontFamily = DetailSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp,
    platformStyle = DetailPlatformStyle,
    lineHeightStyle = DetailLineHeightStyle,
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
