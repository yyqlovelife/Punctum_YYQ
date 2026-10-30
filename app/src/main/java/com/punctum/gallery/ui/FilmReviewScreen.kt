package com.punctum.gallery.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import android.view.HapticFeedbackConstants
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import org.json.JSONArray
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.res.ResourcesCompat
import android.graphics.Paint
import android.graphics.Rect
import android.widget.Toast
import coil.Coil
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.SuccessResult
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.request.ImageRequest
import com.punctum.gallery.R
import com.punctum.gallery.data.PhotoRepository
import com.punctum.gallery.data.PhotoStill
import com.punctum.gallery.model.*
import com.punctum.gallery.ui.theme.Bone
import com.punctum.gallery.ui.theme.Gold
import com.punctum.gallery.ui.theme.DetailSerif
import com.punctum.gallery.ui.theme.NotoSerifSc
import com.punctum.gallery.ui.theme.galleryTitleFont
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt

private const val FILM_PROJECTION_REVEAL_MILLIS = 1200

private val TablePaper = Color.White
private val TableInk = Color(0xFF373A35)

/** A single gallery surface. Selection never opens the legacy detail route. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilmReviewScreen(
    gallery: Gallery,
    photos: List<Photo>,
    overview: GalleryOverview?,
    loading: Boolean,
    sorting: Boolean,
    queuedIDs: Set<String>,
    deleting: Boolean,
    onExit: () -> Unit,
    onRename: (Gallery) -> Unit,
    onToggleSort: () -> Unit,
    onDelete: (Photo) -> Unit,
    onRequestListDelete: (Photo) -> Unit,
    onWarmImages: (Int) -> Unit,
    onWarmThumbnails: (Int, Int) -> Unit,
    onPauseThumbnails: () -> Unit,
    onContentReady: () -> Unit,
    isEntranceComplete: () -> Boolean,
) {
    val context = LocalContext.current
    val tutorialPrefs = remember(context) { context.getSharedPreferences("punctum_tutorial", Context.MODE_PRIVATE) }
    var showUsageHint by remember { mutableStateOf(false) }
    var hasSeenUsageHint by remember { mutableStateOf(tutorialPrefs.getBoolean("film_review_gesture_hint_seen", false)) }
    val view = LocalView.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
    }
    DisposableEffect(activity, view) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousLightNavigation = controller?.isAppearanceLightNavigationBars
        val previousBehavior = controller?.systemBarsBehavior
        val statusBarsWereVisible = ViewCompat.getRootWindowInsets(view)
            ?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val previousCutoutMode = if (Build.VERSION.SDK_INT >= 28) window?.attributes?.layoutInDisplayCutoutMode else null
        val previousContrast = if (Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced else null
        controller?.isAppearanceLightNavigationBars = true
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.statusBars())
        if (Build.VERSION.SDK_INT >= 28) window?.attributes = window?.attributes?.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced = false
        onDispose {
            if (statusBarsWereVisible) controller?.show(WindowInsetsCompat.Type.statusBars())
            else controller?.hide(WindowInsetsCompat.Type.statusBars())
            if (previousBehavior != null) controller?.systemBarsBehavior = previousBehavior
            if (previousLightNavigation != null) controller?.isAppearanceLightNavigationBars = previousLightNavigation
            if (Build.VERSION.SDK_INT >= 28 && previousCutoutMode != null) window?.attributes = window?.attributes?.apply {
                layoutInDisplayCutoutMode = previousCutoutMode
            }
            if (Build.VERSION.SDK_INT >= 29 && previousContrast != null) window?.isNavigationBarContrastEnforced = previousContrast
        }
    }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var selectedID by rememberSaveable(gallery.uri.toString()) { mutableStateOf<String?>(null) }
    var infoVisible by rememberSaveable(gallery.uri.toString()) { mutableStateOf(false) }
    var menuExpanded by remember(gallery.uri) { mutableStateOf(false) }
    val visible = remember(photos, queuedIDs) { photos.filterNot { it.uri.toString() in queuedIDs } }
    val selectedIndex = visible.indexOfFirst { it.uri.toString() == selectedID }
    val selected = visible.getOrNull(selectedIndex)
    LaunchedEffect(selected != null) {
        if (selected != null && !hasSeenUsageHint) {
            hasSeenUsageHint = true
            tutorialPrefs.edit().putBoolean("film_review_gesture_hint_seen", true).apply()
            showUsageHint = true
            delay(5500)
            showUsageHint = false
        }
    }
    val pages = ((visible.size + FILM_PAGE_SIZE - 1) / FILM_PAGE_SIZE).coerceAtLeast(1)
    val pager = rememberPagerState { pages }
    val reveal = remember { Animatable(1f) }
    var revealJob by remember { mutableStateOf<Job?>(null) }
    var awaitingProjection by remember { mutableStateOf<String?>(null) }
    var displayedID by remember { mutableStateOf<String?>(null) }
    var previousSort by remember { mutableStateOf(gallery.sortOrder) }
    val latestVisible by rememberUpdatedState(visible)
    var readyIDs by remember { mutableStateOf(emptySet<String>()) }
    val firstPageIDs = visible.take(FILM_PAGE_SIZE).map { it.uri.toString() }.toSet()
    val ready by rememberUpdatedState(onContentReady)
    val entranceComplete by rememberUpdatedState(isEntranceComplete)
    val lampTimeline = remember(context) {
        val data = context.resources.openRawResource(R.raw.film_lamp_reference)
            .bufferedReader().use { JSONArray(it.readText()) }
        FilmLampTimeline(List(data.length()) { index ->
            val row = data.getJSONObject(index)
            fun values(name: String): List<Float> = row.getJSONArray(name).let { a ->
                List(3) { a.getDouble(it).toFloat() }
            }
            FilmLampReference(values("table"), values("gain"), values("bias"), values("border"))
        })
    }
    val film120Art = remember(context) {
        Film120Art(BitmapFactory.decodeResource(context.resources, R.drawable.film120_frame),
            BitmapFactory.decodeResource(context.resources, R.drawable.film120_digits))
    }
    var lampOn by remember(gallery.uri) { mutableStateOf(false) }
    val lampTime = remember(gallery.uri) { Animatable(0f) }
    val lamp = remember(lampTimeline, lampTime) { derivedStateOf { lampTimeline.frameAt(lampTime.value) } }
    LaunchedEffect(gallery.uri, lampOn) {
        if (!lampOn) {
            lampTime.snapTo(0f)
        } else if (!ValueAnimator.areAnimatorsEnabled()) {
            lampTime.snapTo(FilmLampTimeline.DURATION_MILLIS.toFloat())
        } else {
            // A deliberate physical switch starts at ignition, with no automatic dark countdown.
            lampTime.snapTo(FilmLampTimeline.MANUAL_ON_START_MILLIS.toFloat())
            lampTime.animateTo(FilmLampTimeline.DURATION_MILLIS.toFloat(),
                tween(FilmLampTimeline.MANUAL_ON_DURATION_MILLIS, easing = LinearEasing))
        }
    }

    LaunchedEffect(loading, firstPageIDs, readyIDs) {
        if (!loading && (firstPageIDs.isEmpty() || readyIDs.containsAll(firstPageIDs))) ready()
    }
    LaunchedEffect(gallery.sortOrder) {
        if (previousSort != gallery.sortOrder) {
            previousSort = gallery.sortOrder
            pager.scrollToPage(0)
        }
    }
    LaunchedEffect(pages) {
        if (pager.currentPage >= pages) pager.scrollToPage(pages - 1)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress to pager.currentPage }.collect { (scrolling, page) ->
            onPauseThumbnails()
            if (!scrolling && latestVisible.isNotEmpty()) {
                val first = (page * FILM_PAGE_SIZE).coerceAtMost(latestVisible.lastIndex)
                // The existing cache warmer uses the original gallery's indices.
                val start = photos.indexOfFirst { it.uri == latestVisible[first].uri }.coerceAtLeast(0)
                onWarmThumbnails(start, min(start + FILM_PAGE_SIZE - 1, photos.lastIndex))
            }
        }
    }
    DisposableEffect(Unit) { onDispose { onPauseThumbnails() } }
    LaunchedEffect(selectedID, selected?.uri, gallery.sortOrder) {
        // Identity survives sort/metadata refresh; a genuinely removed asset clears the screen.
        if (selectedID != null && selected == null) selectedID = null
    }

    fun select(photo: Photo, project: Boolean) {
        val replay = displayedID == photo.uri.toString()
        val animate = project && ValueAnimator.areAnimatorsEnabled()
        revealJob?.cancel()
        selectedID = photo.uri.toString()
        awaitingProjection = if (animate && !replay) selectedID else null
        revealJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Keep the current projection intact while the next image is decoding.
            reveal.snapTo(if (animate && replay) 0f else 1f)
            if (animate && replay) reveal.animateTo(1f, tween(FILM_PROJECTION_REVEAL_MILLIS, easing = DetailMotionEase))
        }
    }

    val changePhoto: (Int) -> Unit = { step ->
        visible.getOrNull(selectedIndex + step)?.let { photo ->
            select(photo, false)
            scope.launch { pager.scrollToPage((selectedIndex + step) / FILM_PAGE_SIZE) }
        }
    }
    fun queuePhotoDeletion(photo: Photo) {
        if (deleting || sorting || visible.none { it.uri == photo.uri }) return
        if (selectedID == photo.uri.toString()) {
            val replacement = filmReplacement(visible.map { it.uri.toString() }, photo.uri.toString())
            revealJob?.cancel()
            awaitingProjection = null
            selectedID = replacement
            scope.launch { reveal.snapTo(1f) }
        }
        onDelete(photo)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val queueDelete: () -> Unit = {
        selected?.takeIf { it.uri.toString() == displayedID }?.let(::queuePhotoDeletion)
    }

    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF090A09))) {
            val statusInset = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
            // Transfer the removed 44dp footer to the projection without shrinking the six slide cells.
            val previousChrome = statusInset + 44.dp
            val projectionHeight = previousChrome + (maxHeight - previousChrome) * .49f + 44.dp
            Column(Modifier.fillMaxSize()) {
                ProjectionScreen(
                    targetPhoto = selected,
                    photos = photos,
                    infoVisible = infoVisible,
                    minimumMargin = 48.dp,
                    showUsageHint = showUsageHint,
                    reveal = reveal.asState(),
                    modifier = Modifier.fillMaxWidth().height(projectionHeight).drawWithContent {
                        drawContent()
                        val wash = lamp.value.wash * .08f
                        if (wash > 0f) drawRect(Brush.verticalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = wash)),
                            startY = size.height - 40.dp.toPx(), endY = size.height))
                    },
                    gesturesEnabled = !loading && !sorting && !deleting,
                    onGesture = { gesture ->
                        when (gesture) {
                            FilmGesture.PREVIOUS -> changePhoto(-1)
                            FilmGesture.NEXT -> changePhoto(1)
                            FilmGesture.DELETE -> queueDelete()
                            FilmGesture.NONE -> Unit
                        }
                    },
                    onImageReady = { id ->
                        displayedID = id
                        val originalIndex = photos.indexOfFirst { it.uri.toString() == id }
                        if (originalIndex >= 0) onWarmImages(originalIndex)
                        if (awaitingProjection == id) {
                            awaitingProjection = null
                            revealJob?.cancel()
                            revealJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                reveal.snapTo(0f)
                                reveal.animateTo(1f, tween(FILM_PROJECTION_REVEAL_MILLIS, easing = DetailMotionEase))
                            }
                        }
                    },
                    onImageError = { id ->
                        if (selectedID == id) {
                            awaitingProjection = null
                            selectedID = displayedID?.takeIf { previous -> visible.any { it.uri.toString() == previous } }
                            Toast.makeText(context, "照片加载失败，请重试", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                Column(
                    Modifier.fillMaxWidth().weight(1f)
                        .drawBehind {
                            val rgb = lamp.value.table
                            drawRect(Color(rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f))
                        }
                        // Paint the light table behind system navigation; inset its controls only.
                        .navigationBarsPadding(),
                ) {
                    val titleFont = galleryTitleFont(gallery.displayName)
                    val titleResource = if (titleFont == NotoSerifSc) R.font.noto_serif_sc_semibold else R.font.newsreader_semibold
                    val titleInk = rememberFilmTextInk(gallery.displayName, titleResource, 18.sp)
                    val detailText = "${filmCompactTimeSpan(overview?.timeSpan.orEmpty())}  ·  ${photos.size} 张"
                    val detailInk = rememberFilmTextInk(detailText, R.font.noto_serif_sc_regular, 10.sp)
                    val titleHeight = with(LocalDensity.current) { titleInk.height.toDp() }
                    val detailHeight = with(LocalDensity.current) { detailInk.height.toDp() }
                    val headerHeight = maxOf(72.dp, 28.dp + titleHeight + detailHeight)
                    Box(Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 12.dp)) {
                        Column(Modifier.align(Alignment.Center).fillMaxWidth().filmLampSurface(lamp).padding(horizontal = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            FilmInkText(gallery.displayName, titleInk, titleFont, 18.sp, FontWeight.SemiBold, TableInk,
                                Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            Spacer(Modifier.height(6.dp))
                            FilmInkText(detailText, detailInk, NotoSerifSc, 10.sp, FontWeight.Normal, TableInk.copy(alpha = .65f),
                                Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                        IconButton(onClick = onExit, enabled = !deleting,
                            modifier = Modifier.align(Alignment.CenterStart).size(44.dp).filmLampSurface(lamp).offset(y = -(6.dp + detailHeight) / 2)) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回首页", tint = TableInk, modifier = Modifier.size(20.dp))
                        }
                        FilmLampSwitch(
                            checked = lampOn,
                            enabled = !loading && !deleting && entranceComplete(),
                            onCheckedChange = {
                                lampOn = it
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                            modifier = Modifier.align(Alignment.CenterEnd).offset(y = -(6.dp + detailHeight) / 2),
                        )
                    }
                    if (loading || deleting || sorting) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = TableInk)
                    Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = 20.dp)) {
                        if (visible.isEmpty()) {
                            Text(if (queuedIDs.isEmpty()) "这个图集还没有照片" else "已选好待删除项目，返回首页时确认",
                                modifier = Modifier.align(Alignment.Center).padding(20.dp), color = TableInk, fontSize = 12.sp)
                        } else HorizontalPager(
                            state = pager,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = !sorting && !deleting,
                            flingBehavior = PagerDefaults.flingBehavior(state = pager),
                            beyondViewportPageCount = 1,
                        ) { page ->
                            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                repeat(2) { row ->
                                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        repeat(3) { col ->
                                            val photo = visible.getOrNull(page * FILM_PAGE_SIZE + row * 3 + col)
                                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                                if (photo != null) FilmSlide(
                                                    photo = photo,
                                                    lamp = lamp,
                                                    art = film120Art,
                                                    number = photos.indexOfFirst { it.uri == photo.uri } + 1,
                                                    selected = photo.uri.toString() == selectedID,
                                                    enabled = !pager.isScrollInProgress && !sorting && !deleting,
                                                    onSelect = { if (!pager.isScrollInProgress) select(photo, true) },
                                                    onDelete = { if (!pager.isScrollInProgress) onRequestListDelete(photo) },
                                                    onReady = { readyIDs = readyIDs + photo.uri.toString() },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
                        Box(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 20.dp)) {
                            IconButton(onClick = { menuExpanded = true }, enabled = !deleting, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Outlined.MoreHoriz, "图集更多操作", tint = Bone.copy(alpha = .72f), modifier = Modifier.size(21.dp))
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false },
                                containerColor = TablePaper, tonalElevation = 0.dp) {
                                DropdownMenuItem(text = { Text("重命名图集", color = TableInk, fontFamily = NotoSerifSc, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, null, tint = TableInk, modifier = Modifier.size(18.dp)) },
                                    onClick = { menuExpanded = false; onRename(gallery) })
                                DropdownMenuItem(text = { Text("排序 · ${gallery.sortOrder.label}", color = TableInk, fontFamily = NotoSerifSc, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Outlined.Sort, null, tint = TableInk, modifier = Modifier.size(18.dp)) },
                                    enabled = !loading && !sorting,
                                    onClick = onToggleSort)
                                DropdownMenuItem(text = { Text(if (infoVisible) "关闭影像参数" else "显示影像参数", color = TableInk, fontFamily = NotoSerifSc, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Outlined.Info, null, tint = TableInk, modifier = Modifier.size(18.dp)) },
                                    onClick = { infoVisible = !infoVisible; menuExpanded = false })
                            }
                        }
        }
    }
}

/** Font ink, rather than its line box, determines the two equal title gaps. */
private data class FilmTextInk(val top: Int, val height: Int)

@Composable
private fun rememberFilmTextInk(text: String, resource: Int, fontSize: TextUnit): FilmTextInk {
    val context = LocalContext.current
    val font = remember(context, resource) { ResourcesCompat.getFont(context, resource) }
    val pixels = with(LocalDensity.current) { fontSize.toPx() }
    return remember(text, font, pixels) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textSize = pixels }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        FilmTextInk(bounds.top, bounds.height().coerceAtLeast(1))
    }
}

@Composable
private fun FilmInkText(text: String, ink: FilmTextInk, font: FontFamily, fontSize: TextUnit,
    weight: FontWeight, color: Color, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Start) {
    Layout(content = {
        Text(text, color = color, fontFamily = font, fontWeight = weight, fontSize = fontSize,
            lineHeight = fontSize * 1.4f, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = textAlign)
    }, modifier = modifier) { measurables, constraints ->
        val label = measurables.single().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val baseline = label[FirstBaseline].takeUnless { it == AlignmentLine.Unspecified } ?: 0
        layout(label.width, ink.height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            label.placeRelative(0, -ink.top - baseline)
        }
    }
}

/** Static folds and cached weave paths read as a matte screen even before a slide is selected. */
private fun Modifier.filmCurtain(empty: Boolean): Modifier = drawWithCache {
    val center = Offset(size.width / 2f, size.height / 2f)
    val base = Brush.verticalGradient(listOf(Color(0xFF252526), Color(0xFF1B1B1D), Color(0xFF141416)))
    val folds = Brush.horizontalGradient(listOf(
        Color.Black.copy(alpha = .35f), Color.White.copy(alpha = .025f), Color.Black.copy(alpha = .14f),
        Color.White.copy(alpha = .035f), Color.Black.copy(alpha = .17f), Color.White.copy(alpha = .02f), Color.Black.copy(alpha = .38f)))
    val weave = Path()
    var y = 0f
    val pitch = 2.2.dp.toPx()
    while (y < size.height) { weave.moveTo(0f, y); weave.lineTo(size.width, y + .45.dp.toPx()); y += pitch }
    var x = 0f
    while (x < size.width) { weave.moveTo(x, 0f); weave.lineTo(x + .3.dp.toPx(), size.height); x += pitch * 1.4f }
    val glow = Brush.radialGradient(listOf(Color(0xFF737277).copy(alpha = if (empty) .28f else .12f), Color.Transparent),
        center = center, radius = size.width * .62f)
    val vignette = Brush.radialGradient(0f to Color.Transparent, .42f to Color.Transparent,
        .80f to Color.Black.copy(alpha = if (empty) .42f else .28f), 1f to Color.Black.copy(alpha = .78f),
        center = center, radius = max(size.width * .76f, size.height * .65f))
    onDrawBehind {
        drawRect(base)
        drawRect(folds)
        drawPath(weave, Color(0xFF9B9B9D).copy(alpha = .035f), style = androidx.compose.ui.graphics.drawscope.Stroke(.35.dp.toPx()))
        drawRect(glow)
        drawRect(vignette)
    }
}

private data class LoadedFilmProjection(val photo: Photo, val bitmap: ImageBitmap)

@Composable
private fun ProjectionScreen(
    targetPhoto: Photo?, photos: List<Photo>, infoVisible: Boolean, reveal: State<Float>,
    minimumMargin: androidx.compose.ui.unit.Dp,
    showUsageHint: Boolean,
    modifier: Modifier, gesturesEnabled: Boolean,
    onGesture: (FilmGesture) -> Unit, onImageReady: (String) -> Unit,
    onImageError: (String) -> Unit,
) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf<LoadedFilmProjection?>(null) }
    val imageReady by rememberUpdatedState(onImageReady)
    val imageError by rememberUpdatedState(onImageError)
    LaunchedEffect(targetPhoto?.uri, targetPhoto?.stillImageByteCount) {
        if (targetPhoto == null) {
            loaded = null
        } else {
            // Share the original detail cache and its exact decode size with the neighbor warmer.
            val result = Coil.imageLoader(context).execute(
                ImageRequest.Builder(context)
                    .data(PhotoStill.forDetail(targetPhoto))
                    .memoryCacheKey("detail:${targetPhoto.uri}")
                    .diskCacheKey("detail:${targetPhoto.uri}")
                    .size(1800).crossfade(false).build(),
            )
            if (result is SuccessResult) {
                loaded = LoadedFilmProjection(targetPhoto, result.drawable.toBitmap().asImageBitmap())
                imageReady(targetPhoto.uri.toString())
            } else imageError(targetPhoto.uri.toString())
        }
    }
    // Image, border, geometry and metadata are replaced together, after decoding succeeds.
    val photo = loaded?.let { if (targetPhoto?.uri == it.photo.uri) targetPhoto else it.photo }
    val number = photo?.let { current -> photos.indexOfFirst { it.uri == current.uri } + 1 }
    val density = LocalDensity.current
    val threshold = with(density) { 52.dp.toPx() }
    var drag by remember { mutableStateOf(Offset.Zero) }
    val gesture by rememberUpdatedState(onGesture)
    val releaseEnabled by rememberUpdatedState(gesturesEnabled)
    var place by remember(photo?.uri) { mutableStateOf<String?>(null) }
    LaunchedEffect(photo?.uri, infoVisible) {
        if (infoVisible && photo != null) place = photo.latLong?.let { PhotoRepository.reverseGeocode(context, it[0], it[1]) }
    }
    BoxWithConstraints(
        modifier.clipToBounds()
            .filmCurtain(empty = photo == null)
            .semantics { if (photo == null) contentDescription = "等待胶片投影的幕布" }
            .pointerInput(photo != null, gesturesEnabled) {
                if (photo == null || !gesturesEnabled) return@pointerInput
                detectTapGestures(onTap = { position ->
                    if (releaseEnabled) gesture(filmEdgeTap(position.x, size.width.toFloat()))
                })
            }
            .pointerInput(photo != null, gesturesEnabled) {
                if (photo == null || !gesturesEnabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { drag = Offset.Zero },
                    onDrag = { change, amount ->
                        if (releaseEnabled) { drag += amount; change.consume() }
                    },
                    onDragCancel = { drag = Offset.Zero },
                    onDragEnd = {
                        if (releaseEnabled) gesture(filmGesture(drag.x, drag.y, threshold))
                        drag = Offset.Zero
                    },
                )
            },
    ) {
        // Portraits use the full immersive height; landscape sizes remain width-limited.
        val resting = filmProjectionFrame(photo?.aspectRatio ?: 1f, maxWidth.value, maxHeight.value, false, minimumMargin.value)
        val raised = filmProjectionFrame(photo?.aspectRatio ?: 1f, maxWidth.value, maxHeight.value, true, minimumMargin.value)
        val fitted = resting.size
        val informationLift = animateFloatAsState(
            if (infoVisible) 1f else 0f,
            tween(if (ValueAnimator.areAnimatorsEnabled()) 220 else 0, easing = DetailMotionEase), label = "projection-info-lift",
        )
        val raisedScale = if (fitted.height > 0f) raised.size.height / fitted.height else 1f
        val raisedShift = raised.top + raised.size.height / 2f - resting.top - fitted.height / 2f
        val imageTop = resting.top.dp
        if (photo != null) Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                // Read animation state in the layer phase, never in the screen's composition.
                val lift = informationLift.value
                compositingStrategy = CompositingStrategy.ModulateAlpha
                alpha = reveal.value
                scaleX = 1f + (raisedScale - 1f) * lift
                scaleY = scaleX
                translationY = raisedShift.dp.toPx() * lift
            }.blur(16.dp),
        ) {
            // This recorded light layer is static during projection and detail transitions.
            val center = Offset(size.width / 2f, (resting.top + fitted.height / 2f).dp.toPx())
            val imageWidth = fitted.width.dp.toPx()
            val imageHeight = fitted.height.dp.toPx()
            val bottom = center.y + imageHeight / 2f
            drawImage(loaded!!.bitmap,
                dstOffset = IntOffset((center.x - imageWidth * .515f).roundToInt(), (center.y - imageHeight * .515f).roundToInt()),
                dstSize = IntSize((imageWidth * 1.03f).roundToInt().coerceAtLeast(1), (imageHeight * 1.03f).roundToInt().coerceAtLeast(1)),
                alpha = .18f)
            drawRect(Brush.radialGradient(listOf(Bone.copy(alpha = .045f), Color.Transparent),
                center = center, radius = (max(imageWidth, imageHeight) * .8f).coerceAtLeast(1f)))
            if (bottom < size.height) {
                val beam = Path().apply {
                    moveTo(center.x - imageWidth * .48f, bottom)
                    lineTo(center.x + imageWidth * .48f, bottom)
                    lineTo(center.x + 12.dp.toPx(), size.height + 24.dp.toPx())
                    lineTo(center.x - 12.dp.toPx(), size.height + 24.dp.toPx())
                    close()
                }
                drawPath(beam, Brush.verticalGradient(listOf(Bone.copy(alpha = .012f), Bone.copy(alpha = .035f)),
                    startY = bottom, endY = size.height))
            }
        }
        Box(Modifier.fillMaxWidth().height(fitted.height.dp).offset(y = imageTop), contentAlignment = Alignment.Center) {
            if (photo != null) key(photo.uri) {
                Box(
                    Modifier.size(fitted.width.dp, fitted.height.dp)
                        .graphicsLayer {
                            val phase = reveal.value
                            val lift = informationLift.value
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                            alpha = phase
                            scaleX = (.94f + .06f * phase) * (1f + (raisedScale - 1f) * lift)
                            scaleY = scaleX
                            translationY = raisedShift.dp.toPx() * lift + (1f - phase) * size.height * .06f + min(0f, drag.y) * .2f
                            translationX = drag.x.coerceIn(-90.dp.toPx(), 90.dp.toPx()) * .28f
                        },
                ) {
                    Image(
                        bitmap = loaded!!.bitmap,
                        contentDescription = "投影 ${photo.name}", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().drawWithCache {
                            val horizontal = Brush.horizontalGradient(0f to Color.Black.copy(alpha = .16f), .09f to Color.Transparent,
                                .91f to Color.Transparent, 1f to Color.Black.copy(alpha = .16f))
                            val vertical = Brush.verticalGradient(0f to Color.Black.copy(alpha = .12f), .10f to Color.Transparent,
                                .90f to Color.Transparent, 1f to Color.Black.copy(alpha = .12f))
                            onDrawWithContent {
                                val inset = size.height * (1f - reveal.value) * .44f
                                clipRect(top = inset, bottom = size.height - inset) {
                                    this@onDrawWithContent.drawContent()
                                    drawRect(horizontal)
                                    drawRect(vertical)
                                }
                            }
                        },
                    )
                }
            }
        }
        if (photo == null) Text(
            "选择一张胶片，重映一段时光",
            color = Bone.copy(alpha = .22f), fontFamily = NotoSerifSc, fontSize = 12.sp,
            letterSpacing = .7.sp, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
        )
        if (photo != null) {
            val infoTop = if (infoVisible) {
                (raised.top + raised.size.height + maxHeight.value - FILM_INFO_HEIGHT) / 2f
            } else maxHeight.value - FILM_INFO_HEIGHT
            Column(
                Modifier.align(Alignment.TopCenter).offset(y = infoTop.dp).fillMaxWidth()
                    .height(FILM_INFO_HEIGHT.dp).padding(horizontal = FILM_INFO_SIDE_MARGIN.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (infoVisible) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("No.${number ?: "—"}", color = Gold, fontFamily = DetailSerif, fontSize = 10.sp, lineHeight = 15.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(photo.device.orEmpty(), color = Bone.copy(alpha = .8f), fontFamily = galleryTitleFont(photo.device.orEmpty()), fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(listOfNotNull(photo.focalLength, photo.aperture, photo.shutter,
                        photo.iso?.let { if (it.trim().startsWith("ISO", ignoreCase = true)) it else "ISO $it" }).joinToString("   ·   "),
                        color = Bone.copy(alpha = .8f), fontFamily = DetailSerif, fontSize = 10.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(photo.resolutionText, photo.fileSizeText).joinToString("  ·  "),
                        color = Bone.copy(alpha = .5f), fontFamily = DetailSerif, fontSize = 9.sp, lineHeight = 14.sp)
                    Text(listOfNotNull(photo.dateTaken, place ?: photo.coordinateText).joinToString("  ·  "),
                        color = Bone.copy(alpha = .5f), fontSize = 9.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Spacer(Modifier.weight(1f))
                    if (drag.y < -threshold || showUsageHint) Text(if (drag.y < -threshold) "松手加入待删除" else "左右滑动快切  ·  上滑加入待删除",
                        color = if (drag.y < -threshold) Gold else Bone.copy(alpha = .28f), fontSize = 9.sp,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun FilmSlide(photo: Photo, lamp: State<FilmLampFrame>, art: Film120Art, number: Int, selected: Boolean, enabled: Boolean, onSelect: () -> Unit, onDelete: () -> Unit, onReady: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val deleteProgress = remember(photo.uri) { Animatable(0f) }
    var showDeleteProgress by remember(photo.uri) { mutableStateOf(false) }
    val currentSelect by rememberUpdatedState(onSelect)
    val currentDelete by rememberUpdatedState(onDelete)
    LaunchedEffect(enabled) {
        if (!enabled) { showDeleteProgress = false; deleteProgress.snapTo(0f) }
    }
    val tilt = remember(photo.uri) { ((photo.uri.hashCode().and(0x7fffffff) % 9) - 4) * .32f }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val dimensions = Film120Geometry.size(maxWidth.value, maxHeight.value)
        val frameWidth = dimensions.width.dp
        val frameHeight = dimensions.height.dp
        val scale = dimensions.width / Film120Geometry.WIDTH
        Box(
            Modifier.size(frameWidth, frameHeight).graphicsLayer { rotationZ = tilt }
                .shadow(if (selected) 4.dp else 2.dp, RoundedCornerShape(.5.dp))
                .pointerInput(photo.uri, enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(onPress = {
                        coroutineScope {
                            var pressed = true
                            var deleteStarted = false
                            val hold = launch {
                                delay(200)
                                deleteStarted = true
                                showDeleteProgress = true
                                deleteProgress.snapTo(0f)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                deleteProgress.animateTo(1f, tween(500, easing = LinearEasing))
                                if (pressed) currentDelete()
                            }
                            try {
                                val released = tryAwaitRelease()
                                pressed = false
                                hold.cancel()
                                if (released && !deleteStarted) currentSelect()
                            } finally {
                                pressed = false
                                hold.cancel()
                                showDeleteProgress = false
                            }
                        }
                    })
                }
                .semantics {
                    contentDescription = "胶片 $number ${photo.name}${if (selected) " 已投影" else ""}"
                    if (enabled) {
                        onClick("投影") { currentSelect(); true }
                        onLongClick("加入待删除") { currentDelete(); true }
                    }
                },
        ) {
            // The bitmap is source-cropped to a square before drawing, without a rotated scissor.
            Box(Modifier.offset(x = (Film120Geometry.PHOTO_LEFT * scale).dp,
                y = (Film120Geometry.PHOTO_TOP * scale).dp)
                .size((Film120Geometry.PHOTO_SIDE * scale).dp)) {
                FilmThumbnail(photo, lamp, Modifier.fillMaxSize(), onReady)
            }
            Film120Border(art, lamp, number, remember(photo.uri) { Film120Geometry.emulsionCode(photo.uri.toString()) },
                Modifier.matchParentSize())
            if (selected) Canvas(Modifier.matchParentSize()) {
                drawRect(Color(0xFFE78B4D).copy(alpha = .85f), style = Stroke(.65.dp.toPx()))
            }
            if (showDeleteProgress) {
                Box(Modifier.align(Alignment.TopEnd).padding(3.dp)) {
                    DeleteProgressBadge(deleteProgress.value, size = 26.dp)
                }
            }
        }
    }
}

/** Source-crop the bitmap so a rotated aperture never needs a jagged hardware scissor. */
@Composable
private fun FilmThumbnail(photo: Photo, lamp: State<FilmLampFrame>, modifier: Modifier, onReady: () -> Unit) {
    val context = LocalContext.current
    val ready by rememberUpdatedState(onReady)
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val heightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val request = remember(photo.uri, photo.thumbnailPath, widthPx, heightPx) {
            ImageRequest.Builder(context).data(PhotoStill.forList(photo))
                .size(widthPx, heightPx).scale(coil.size.Scale.FILL).crossfade(false).build()
        }
        val painter = rememberAsyncImagePainter(request)
        val state = painter.state
        LaunchedEffect(state) {
            if (state is AsyncImagePainter.State.Success || state is AsyncImagePainter.State.Error) ready()
        }
        val bitmap = remember(state) { (state as? AsyncImagePainter.State.Success)?.result?.drawable?.toBitmap() }
        val source = remember(bitmap) {
            bitmap?.let {
                val crop = Film120Geometry.crop(it.width, it.height)
                Rect(crop.left, crop.top, crop.left + crop.side, crop.top + crop.side)
            }
        }
        val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
        val lampFilter = remember { FilmLampFilter() }
        val destination = remember { android.graphics.RectF() }
        Canvas(Modifier.fillMaxSize()) {
            if (bitmap != null && source != null) {
                // Read shared light state only while drawing; preserve source cropping and antialiasing.
                val frame = lamp.value
                paint.alpha = 255
                paint.colorFilter = lampFilter.forFrame(frame, photograph = true)
                destination.set(0f, 0f, size.width, size.height)
                drawIntoCanvas {
                    it.nativeCanvas.drawBitmap(bitmap, source, destination, paint)
                    // Retain a little source detail above the clipping threshold, then restore full color.
                    if (frame.originalMix > 0f && frame.originalMix < 1f) {
                        paint.colorFilter = null
                        paint.alpha = (frame.originalMix * 255f).roundToInt()
                        it.nativeCanvas.drawBitmap(bitmap, source, destination, paint)
                    }
                }
            } else drawRect(Color(0xFF252C26))
        }
    }
}

/** Only the frame, labels and header receive the table exposure; photographs have their own curve. */
@Composable
private fun Modifier.filmLampSurface(lamp: State<FilmLampFrame>): Modifier {
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val filter = remember { FilmLampFilter() }
    return drawWithContent {
        paint.colorFilter = filter.forFrame(lamp.value, photograph = false)
        val canvas = drawContext.canvas.nativeCanvas
        val checkpoint = canvas.saveLayer(0f, 0f, size.width, size.height, paint)
        try { drawContent() } finally { canvas.restoreToCount(checkpoint) }
    }
}

/** Reuse filters for the same immutable reference frame across draw calls. */
private class FilmLampFilter {
    private var previous: FilmLampFrame? = null
    private var filter: ColorMatrixColorFilter? = null

    fun forFrame(frame: FilmLampFrame, photograph: Boolean): ColorMatrixColorFilter? {
        if (frame !== previous) {
            previous = frame
            if (photograph && frame.filmGain == 1f && frame.filmBias == 0f) {
                filter = null
            } else {
                val matrix = FloatArray(20)
                for (channel in 0..2) {
                    matrix[channel * 6] = if (photograph) frame.filmGain else frame.boardGain(channel)
                    matrix[channel * 5 + 4] = if (photograph) frame.filmBias else frame.boardBias(channel)
                }
                matrix[18] = 1f
                filter = ColorMatrixColorFilter(ColorMatrix(matrix))
            }
        }
        return filter
    }
}


@Composable
private fun FilmLampSwitch(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Canvas(modifier.size(48.dp).toggleable(value = checked, enabled = enabled, role = Role.Switch,
        interactionSource = interaction, indication = null, onValueChange = onCheckedChange).semantics {
            contentDescription = "看片台灯光开关"
            stateDescription = if (checked) "已开灯" else "已关灯"
        }) {
        val unit = size.width / 48f
        val left = 7f * unit
        val top = 15f * unit
        val width = 34f * unit
        val height = 18f * unit
        drawRoundRect(Color.Black.copy(alpha = .22f), Offset(left, top + unit), Size(width, height), CornerRadius(3f * unit))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF55584F), Color(0xFF252923)), startY = top, endY = top + height),
            Offset(left, top), Size(width, height), CornerRadius(3f * unit))
        drawRoundRect(Color(0xFF6C6E64), Offset(left, top), Size(width, height), CornerRadius(3f * unit), style = Stroke(.5f * unit))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF252923), Color(0xFF383D34)), startY = top, endY = top + height),
            Offset(left + 2f * unit, top + 2f * unit), Size(width - 4f * unit, height - 4f * unit), CornerRadius(2f * unit))
        val thumbWidth = 14f * unit
        val thumbLeft = if (checked) left + width - thumbWidth - 2f * unit else left + 2f * unit
        val thumbTop = top + 2f * unit
        drawRoundRect(Color.Black.copy(alpha = .3f), Offset(thumbLeft, thumbTop + unit), Size(thumbWidth, 14f * unit), CornerRadius(2f * unit))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFEEEADE), Color(0xFFC8C6B9)), startY = thumbTop, endY = thumbTop + 14f * unit),
            Offset(thumbLeft, thumbTop), Size(thumbWidth, 14f * unit), CornerRadius(2f * unit))
        drawRoundRect(Color(0xFFA6A598), Offset(thumbLeft, thumbTop), Size(thumbWidth, 14f * unit), CornerRadius(2f * unit), style = Stroke(.5f * unit))
        for (line in listOf(5.5f, 8.5f)) {
            val x = thumbLeft + line * unit
            drawLine(Color(0xFF8B8C7E), Offset(x, thumbTop + 4f * unit), Offset(x, thumbTop + 10f * unit), .65f * unit)
            drawLine(Color(0xFFF5F1E5), Offset(x + .7f * unit, thumbTop + 4f * unit), Offset(x + .7f * unit, thumbTop + 10f * unit), .45f * unit)
        }
    }
}


private data class Film120Art(val frame: Bitmap, val digits: Bitmap)

/** Reference border texture and original triangles, plus user-specified live film numbers. */
@Composable
private fun Film120Border(art: Film120Art, lamp: State<FilmLampFrame>, number: Int, code: String, modifier: Modifier) {
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val destination = remember { android.graphics.RectF() }
    val digitSource = remember { Rect() }
    val filter = remember { Film120BorderFilter() }
    Canvas(modifier) {
        val scale = size.width / Film120Geometry.WIDTH
        paint.colorFilter = filter.forFrame(lamp.value)
        destination.set(0f, 0f, size.width, size.height)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(art.frame, null, destination, paint)
            fun printNumber(text: String, left: Float, top: Float) {
                text.forEachIndexed { index, ch ->
                    val digit = ch.digitToInt()
                    val glyphWidth = Film120Geometry.DIGIT_WIDTH
                    digitSource.set((digit * glyphWidth).toInt(), 0, ((digit + 1) * glyphWidth).toInt(), Film120Geometry.DIGIT_HEIGHT.toInt())
                    val x = (left + index * glyphWidth) * scale
                    destination.set(x, top * scale, x + glyphWidth * scale, (top + Film120Geometry.DIGIT_HEIGHT) * scale)
                    canvas.nativeCanvas.drawBitmap(art.digits, digitSource, destination, paint)
                }
            }
            printNumber(code, Film120Geometry.RANDOM_LEFT, 0f)
            printNumber(number.toString(), Film120Geometry.NUMBER_LEFT, Film120Geometry.NUMBER_TOP)
        }
    }
}

/** The opaque black base transmits much less of the lamp than the photographic emulsion. */
private class Film120BorderFilter {
    private var previous: FilmLampFrame? = null
    private var filter: ColorMatrixColorFilter? = null
    fun forFrame(frame: FilmLampFrame): ColorMatrixColorFilter? {
        if (previous !== frame) {
            previous = frame
            filter = if (frame.borderGain == 1f && frame.borderLift.all { it == 0f }) null else {
                val matrix = FloatArray(20)
                for (channel in 0..2) {
                    matrix[channel * 6] = frame.borderGain
                    matrix[channel * 5 + 4] = frame.borderLift[channel]
                }
                matrix[18] = 1f
                ColorMatrixColorFilter(ColorMatrix(matrix))
            }
        }
        return filter
    }
}
