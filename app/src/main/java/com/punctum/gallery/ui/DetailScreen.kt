package com.punctum.gallery.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil.compose.AsyncImage
import com.punctum.gallery.data.PhotoRepository
import com.punctum.gallery.model.Photo
import com.punctum.gallery.ui.theme.Bone
import com.punctum.gallery.ui.theme.Georgia
import com.punctum.gallery.ui.theme.Gold
import com.punctum.gallery.ui.theme.Ink
import com.punctum.gallery.ui.theme.Muted
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.abs

@Composable
internal fun DetailScreen(
    photos: List<Photo>,
    startIndex: Int,
    onDelete: (Photo) -> Unit,
    onClose: () -> Unit,
) {
    if (photos.isEmpty()) return
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    var controlsVisible by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Photo?>(null) }
    var swipeDeleteDistance by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val deleteThreshold = with(density) { 64.dp.toPx() }
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, photos.size - 1),
    ) { photos.size }
    val currentPhoto = photos.getOrNull(pagerState.currentPage)

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
                    swipeDeleteDistance = 0f

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (deletingGesture && swipeDeleteDistance >= deleteThreshold) {
                                photos.getOrNull(pagerState.currentPage)?.let(onDelete)
                            }
                            swipeDeleteDistance = 0f
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
                            swipeDeleteDistance = upwardDistance
                            change.consume()
                        }
                    }
                }
            },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
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
            )
        }

        AnimatedVisibility(
            visible = swipeDeleteDistance > 0f,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 58.dp),
        ) {
            val armed = swipeDeleteDistance >= deleteThreshold
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "上滑删除",
                tint = if (armed) Color(0xFFE04B4B) else Muted,
                modifier = Modifier.size(30.dp),
            )
        }

        AnimatedVisibility(visible = controlsVisible) {
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

@Composable
private fun ImmersivePhoto(
    photo: Photo,
    displayNumber: Int,
    onTapLeft: () -> Unit,
    onTapRight: () -> Unit,
    onToggleControls: () -> Unit,
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
            DetailLine("ISO", photo.iso)
            DetailLine("Aperture", photo.aperture)

            Spacer(Modifier.navigationBarsPadding().height(86.dp))
        }
    }
}

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
private fun DetailPhotoFrame(photo: Photo, screenHeight: Dp) {
    val aspect = photo.aspectRatio.coerceAtLeast(0.1f)
    if (aspect < 1f) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.name,
            contentScale = ContentScale.Fit,
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
                AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.name,
                    contentScale = ContentScale.Fit,
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
