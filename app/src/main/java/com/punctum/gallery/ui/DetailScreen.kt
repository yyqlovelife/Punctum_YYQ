package com.punctum.gallery.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.punctum.gallery.data.PhotoRepository
import com.punctum.gallery.model.Photo
import com.punctum.gallery.ui.theme.Bone
import com.punctum.gallery.ui.theme.Georgia
import com.punctum.gallery.ui.theme.Gold
import com.punctum.gallery.ui.theme.Ink
import com.punctum.gallery.ui.theme.Muted
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import coil.request.ImageRequest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun DetailScreen(
    photos: List<Photo>,
    startIndex: Int,
    onDelete: (Photo) -> Unit,
    onClose: () -> Unit,
    onWarmImages: (Int) -> Unit,
) {
    if (photos.isEmpty()) return
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    var controlsVisible by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Photo?>(null) }
    var centerToast by remember { mutableStateOf<String?>(null) }
    var deleteToastVisible by remember { mutableStateOf(false) }
    val density = LocalDensity.current
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

    LaunchedEffect(pagerState, photos) {
        onWarmImages(pagerState.currentPage)
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onWarmImages(page)
        }
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
                                        targetPhoto?.let(onDelete)
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

        if (progress == 0f) {
            HorizontalPager(
                state = pagerState,
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
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
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
                style = MaterialTheme.typography.labelSmall,
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
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "返回", tint = Bone)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${pagerState.currentPage + 1} / ${photos.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
                Spacer(Modifier.width(4.dp))
                currentPhoto?.let { photo ->
                    IconButton(onClick = { sharePhoto(context, photo) }) {
                        Icon(Icons.Outlined.IosShare, contentDescription = "分享", tint = Bone)
                    }
                    IconButton(onClick = { pendingDelete = photo }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除", tint = Muted)
                    }
                }
            }
        }
    }

    pendingDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(photo)
                    },
                ) {
                    Text("确定", color = Gold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = Muted)
                }
            },
            title = {
                Text("删除照片", style = MaterialTheme.typography.titleLarge, color = Bone)
            },
            text = {
                Text(
                    "确定删除这张照片吗？照片可在系统相册回收站查看。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Bone,
                )
            },
            containerColor = Ink,
        )
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
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    var placeName by remember(photo.uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(photo.uri) {
        placeName = photo.latLong?.let { PhotoRepository.reverseGeocode(context, it[0], it[1]) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        when {
                            offset.x < size.width * 0.25f -> onTapLeft()
                            offset.x > size.width * 0.75f -> onTapRight()
                            else -> onToggleControls()
                        }
                    },
                )
            }
            .verticalScroll(scrollState),
    ) {
        DetailPhotoFrame(
            photo = photo,
            screenHeight = screenHeight,
            animateImage = animateImage,
            onImageVisible = onImageVisible,
        )

        Spacer(Modifier.height(30.dp))

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp)) {
            Text(
                "No.$displayNumber",
                style = DetailTitleStyle,
                color = Bone,
            )
            Spacer(Modifier.height(18.dp))

            photo.dateTaken?.let {
                OneLineDetailText(it)
                Spacer(Modifier.height(1.dp))
            }
            val location = placeName ?: photo.coordinateText
            if (!location.isNullOrBlank()) {
                Text(location, style = DetailTextStyle, color = Bone)
            }

            Spacer(Modifier.height(24.dp))

            DetailLine("Camera", photo.device)
            DetailLine("Shutter", photo.shutter)
            DetailLine("Focal", photo.focalLength)
            DetailLine("Aperture", displayAperture(photo.aperture))
            DetailLine("ISO", photo.iso)
            DetailLine("Resolution", photo.resolutionText)
            DetailLine("File Size", photo.fileSizeText)

            Spacer(Modifier.navigationBarsPadding().height(86.dp))
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
private fun OneLineDetailText(text: String) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val fontSize = when {
            maxWidth < 300.dp -> 12.sp
            maxWidth < 340.dp -> 13.sp
            else -> 14.sp
        }
        Text(
            text,
            style = DetailTextStyle.copy(fontSize = fontSize, lineHeight = fontSize * 1.25f),
            color = Bone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailPhotoFrame(
    photo: Photo,
    screenHeight: Dp,
    animateImage: Boolean,
    onImageVisible: () -> Unit,
) {
    val context = LocalContext.current
    val imageModel = remember(photo.uri) {
        ImageRequest.Builder(context)
            .data(photo.uri)
            .memoryCacheKey("detail:${photo.uri}")
            .diskCacheKey("detail:${photo.uri}")
            .size(1800)
            .crossfade(false)
            .build()
    }
    val aspect = photo.aspectRatio.coerceAtLeast(0.1f)
    if (aspect < 1f) {
        PunctumImage(
            model = imageModel,
            contentDescription = photo.name,
            contentScale = ContentScale.Fit,
            animateOnLoad = animateImage,
            onSuccess = onImageVisible,
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
                PunctumImage(
                    model = imageModel,
                    contentDescription = photo.name,
                    contentScale = ContentScale.Fit,
                    animateOnLoad = animateImage,
                    onSuccess = onImageVisible,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = topPadding)
                        .aspectRatio(aspect),
                )
            }
        }
    }
}

private fun sharePhoto(context: android.content.Context, photo: Photo) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, photo.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share photo"))
}

private val DetailTitleStyle = TextStyle(
    fontFamily = Georgia,
    fontWeight = FontWeight.SemiBold,
    fontSize = 21.sp,
    lineHeight = 25.sp,
    letterSpacing = 0.1.sp,
)

private val DetailTextStyle = TextStyle(
    fontFamily = Georgia,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.05.sp,
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
