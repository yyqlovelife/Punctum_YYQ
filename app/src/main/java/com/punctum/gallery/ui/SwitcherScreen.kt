package com.punctum.gallery.ui

import android.net.Uri
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.request.ImageRequest
import java.io.File
import com.punctum.gallery.R
import com.punctum.gallery.model.Gallery
import com.punctum.gallery.model.GalleryOverview
import com.punctum.gallery.model.InvitationCardStyle
import com.punctum.gallery.ui.theme.Bone
import com.punctum.gallery.ui.theme.DetailSerif
import com.punctum.gallery.ui.theme.Gold
import com.punctum.gallery.ui.theme.Ink
import com.punctum.gallery.ui.theme.Muted
import com.punctum.gallery.ui.theme.Surface1
import com.punctum.gallery.ui.theme.NotoSerifSc
import com.punctum.gallery.ui.theme.galleryTitleFont
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.random.Random

@Composable
internal fun SwitcherScreen(
    overviews: List<GalleryOverview>,
    canClose: Boolean,
    title: String = "Your Punctums",
    subtitle: String = "每一份画廊邀请卡，都是你来时的路",
    invitationStyle: InvitationCardStyle,
    postcardListState: LazyListState,
    ticketListState: LazyListState,
    reversalFilmGridState: LazyGridState,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onToggleInvitationStyle: () -> Unit,
    onRename: (Gallery) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDelete: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showSortDialog by remember { mutableStateOf(false) }
    val reversalFilmVariants = remember { mutableMapOf<String, Int>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(invitationStyle) {
                    detectTapGestures(onDoubleTap = {
                        scope.launch {
                            when (invitationStyle) {
                                InvitationCardStyle.POSTCARD -> postcardListState.animateScrollToItem(0)
                                InvitationCardStyle.TICKET -> ticketListState.animateScrollToItem(0)
                                InvitationCardStyle.REVERSAL_FILM -> reversalFilmGridState.animateScrollToItem(0)
                            }
                        }
                    })
                }
                .padding(start = 24.dp, end = 12.dp, top = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .offset(y = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                SectionLabel("- PUNCTUM · STUDIUM -")
            }
            Spacer(Modifier.weight(1f))
            PressFeedbackIconButton(onClick = onToggleInvitationStyle) {
                Icon(Icons.Outlined.Style, contentDescription = "切换邀请卡风格", tint = Muted)
            }
            PressFeedbackIconButton(onClick = { showSortDialog = true }) {
                Icon(Icons.Outlined.Sort, contentDescription = "调整排序", tint = Muted)
            }
            if (canClose) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = Muted)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            val titleFontSize = when {
                maxWidth < 330.dp -> 34.dp
                maxWidth < 370.dp -> 37.dp
                maxWidth < 420.dp -> 40.dp
                else -> 44.dp
            }
            Text(
                title,
                style = MaterialTheme.typography.displayMedium.copy(fontSize = titleFontSize.value.sp),
                color = Bone,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(26.dp))

        when (invitationStyle) {
            InvitationCardStyle.POSTCARD -> PostcardList(
                overviews = overviews,
                listState = postcardListState,
                onSelect = onSelect,
            )

            InvitationCardStyle.TICKET -> TicketList(
                overviews = overviews,
                listState = ticketListState,
                onSelect = onSelect,
            )

            InvitationCardStyle.REVERSAL_FILM -> ReversalFilmGrid(
                overviews = overviews,
                gridState = reversalFilmGridState,
                cardVariants = reversalFilmVariants,
                onSelect = onSelect,
            )
        }
    }

    if (showSortDialog) {
        SortDialog(
            overviews = overviews,
            onMove = onMove,
            onDelete = onDelete,
            onAdd = {
                showSortDialog = false
                onAdd()
            },
            onDismiss = { showSortDialog = false },
        )
    }
}

@Composable
private fun PostcardList(
    overviews: List<GalleryOverview>,
    listState: LazyListState,
    onSelect: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cardWidth = maxWidth * 0.78f
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 70.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(
                items = overviews,
                key = { _, item -> item.gallery.uri.toString() },
            ) { _, ov ->
                PostcardInvitationCard(
                    overview = ov,
                    width = cardWidth,
                    onClick = { onSelect(ov.gallery.uri.toString()) },
                )
            }
            item(key = "navbar") {
                Spacer(Modifier.navigationBarsPadding().height(8.dp))
            }
        }
    }
}

@Composable
private fun TicketList(
    overviews: List<GalleryOverview>,
    listState: LazyListState,
    onSelect: (String) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(
            items = overviews,
            key = { _, item -> item.gallery.uri.toString() },
        ) { _, ov ->
            TicketInvitationCard(
                overview = ov,
                onClick = { onSelect(ov.gallery.uri.toString()) },
            )
        }
        item(key = "navbar") {
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }
}

@Composable
private fun ReversalFilmGrid(
    overviews: List<GalleryOverview>,
    gridState: LazyGridState,
    cardVariants: MutableMap<String, Int>,
    onSelect: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val baselinePadding = 16.dp
        val baselineGap = 10.dp
        val baselineCardWidth = (maxWidth - baselinePadding * 2 - baselineGap * 2) / 3
        val scale = maxWidth / (baselineCardWidth * 2 + baselineGap + baselinePadding * 2)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = baselinePadding * scale,
                end = baselinePadding * scale,
                bottom = 28.dp * scale,
            ),
            horizontalArrangement = Arrangement.spacedBy(baselineGap * scale),
            verticalArrangement = Arrangement.spacedBy(11.dp * scale),
        ) {
            gridItemsIndexed(
                items = overviews,
                key = { _, item -> item.gallery.uri.toString() },
            ) { _, overview ->
                val galleryKey = overview.gallery.uri.toString()
                ReversalFilmCard(
                    overview = overview,
                    visualScale = scale,
                    variant = cardVariants.getOrPut(galleryKey) { Random.nextInt(3) },
                    onClick = { onSelect(galleryKey) },
                )
            }
            item(key = "navbar", span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.navigationBarsPadding().height(8.dp * scale))
            }
        }
    }
}

@Composable
private fun GalleryTitleText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    alignment: Alignment = Alignment.CenterStart,
    textAlign: TextAlign? = null,
    chineseStrokeWidth: Float = 2f,
) {
    val isChinese = text.any { it.code in 0x4E00..0x9FFF }
    val resolvedStyle = if (isChinese) {
        style.copy(fontWeight = FontWeight.SemiBold, fontSynthesis = FontSynthesis.None)
    } else {
        style.copy(fontSynthesis = FontSynthesis.None)
    }
    Box(modifier = modifier, contentAlignment = alignment) {
        Text(
            text = text,
            style = resolvedStyle,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign,
        )
    }
}

@Composable
private fun ReversalFilmCard(
    overview: GalleryOverview,
    visualScale: Float,
    variant: Int,
    onClick: () -> Unit,
) {
    if (KodakInspiredReversalFilmEnabled) {
        KodakInspiredReversalFilmCard(
            overview = overview,
            visualScale = visualScale,
            variant = variant,
            onClick = onClick,
        )
    } else {
        LegacyReversalFilmCard(
            overview = overview,
            visualScale = visualScale,
            onClick = onClick,
        )
    }
}

@Composable
private fun KodakInspiredReversalFilmCard(
    overview: GalleryOverview,
    visualScale: Float,
    variant: Int,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val cachedCover = remember(overview.ticketCoverPath) {
        overview.ticketCoverPath?.let(::File)?.takeIf(File::exists)
    }
    val latestUri = overview.coverUris.firstOrNull()
    val imageModel = remember(cachedCover, latestUri) {
        when {
            cachedCover != null -> ImageRequest.Builder(context)
                .data(cachedCover)
                .memoryCacheKey("reversal-film-file:${cachedCover.absolutePath}")
                .diskCacheKey("reversal-film-file:${cachedCover.absolutePath}")
                .crossfade(false)
                .build()
            latestUri != null -> inviteCoverRequest(context, latestUri)
            else -> null
        }
    }
    val titleIsChinese = remember(overview.gallery.displayName) {
        overview.gallery.displayName.any { it.code in 0x4E00..0x9FFF }
    }
    val paperColor = when (variant) {
        0 -> Color(0xFFE9EAE4)
        1 -> Color(0xFFEAE3D5)
        else -> Color(0xFFE8EBE5)
    }
    val accentColor = when (variant) {
        0 -> Color(0xFFF04424)
        1 -> Color(0xFFD91E45)
        else -> Color(0xFFEF432D)
    }
    val cardShape = RoundedCornerShape(4.dp * visualScale)
    val imageShape = RoundedCornerShape(4.dp * visualScale)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .punctumPressable(
                pressedScale = 0.985f,
                pressedOffsetY = 4.dp,
                pressedAlpha = 0.96f,
                onClick = onClick,
            )
            .shadow(5.dp * visualScale, cardShape, clip = false)
            .clip(cardShape)
            .background(paperColor),
    ) {
        Image(
            painter = painterResource(R.drawable.reversal_film_paper_texture),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.42f),
        )
        KodakInspiredDecoration(
            variant = variant,
            modifier = Modifier.fillMaxSize(),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp * visualScale),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = overview.gallery.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = (
                            if (titleIsChinese) 8.2f * visualScale else 9f * visualScale
                            ).sp,
                        lineHeight = (9f * visualScale).sp,
                        letterSpacing = (-0.25f).sp,
                        fontWeight = FontWeight.Black,
                        fontSynthesis = FontSynthesis.None,
                    ),
                    color = accentColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 7.dp * visualScale),
                )
                Text(
                    text = "PUNCTUM SLIDE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = (3.8f * visualScale).sp,
                        lineHeight = (4.2f * visualScale).sp,
                        letterSpacing = (0.25f * visualScale).sp,
                        fontWeight = FontWeight.Black,
                    ),
                    color = accentColor,
                    maxLines = 1,
                )
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 13.dp * visualScale)
                    .fillMaxWidth()
                    .aspectRatio(1.48f)
                    .clip(imageShape)
                    .background(Color(0xFFF6F4EE)),
            ) {
                if (imageModel != null) {
                    PunctumImage(
                        model = imageModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(5.dp * visualScale)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.26f), Color.Transparent),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(3.dp * visualScale)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Black.copy(alpha = 0.20f), Color.Transparent),
                            ),
                        ),
                )
            }

            KodakInspiredFooter(
                variant = variant,
                visualScale = visualScale,
                accentColor = accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun KodakInspiredDecoration(variant: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        when (variant) {
            0 -> {
                val yellow = Color(0xFFF5B900).copy(alpha = 0.88f)
                val panelTop = size.height * 0.70f
                drawRect(
                    color = yellow,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, panelTop),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height - panelTop),
                )
                val barFractions = listOf(0.42f, 0.47f, 0.52f, 0.57f, 0.625f, 0.67f)
                val barThicknesses = listOf(0.012f, 0.015f, 0.019f, 0.024f, 0.032f, 0.040f)
                barFractions.forEachIndexed { index, fraction ->
                    val thickness = size.height * barThicknesses[index]
                    val y = size.height * fraction
                    drawRect(
                        color = yellow,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, y),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.155f, thickness),
                    )
                    drawRect(
                        color = yellow,
                        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.845f, y),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.155f, thickness),
                    )
                }
            }

            1 -> {
                val orange = Color(0xFFF0A000).copy(alpha = 0.92f)
                val lineThicknesses = listOf(0.009f, 0.012f, 0.016f, 0.022f)
                repeat(4) { index ->
                    val leftY = size.height * (0.79f + index * 0.035f)
                    val rightY = size.height * (0.70f + index * 0.035f)
                    val path = Path().apply {
                        moveTo(0f, leftY)
                        lineTo(size.width * 0.46f, leftY)
                        cubicTo(
                            size.width * 0.55f,
                            leftY,
                            size.width * 0.56f,
                            rightY,
                            size.width * 0.64f,
                            rightY,
                        )
                        lineTo(size.width, rightY)
                    }
                    drawPath(
                        path = path,
                        color = orange,
                        style = Stroke(
                            width = size.height * lineThicknesses[index],
                            cap = StrokeCap.Round,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun KodakInspiredFooter(
    variant: Int,
    visualScale: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when (variant) {
            0 -> {
                VariantOneProcessingBadge(
                    visualScale = visualScale,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 3.dp * visualScale, bottom = 4.dp * visualScale),
                )
            }

            1 -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 5.dp * visualScale, bottom = 4.dp * visualScale),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        "PUNCTUM",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = (7.2f * visualScale).sp,
                            lineHeight = (7.4f * visualScale).sp,
                            letterSpacing = (-0.42f).sp,
                            fontWeight = FontWeight(1000),
                            fontStyle = FontStyle.Italic,
                        ),
                        color = Color(0xFF282522),
                        maxLines = 1,
                    )
                    Text(
                        "PROCESSING SERVICES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = (3.4f * visualScale).sp,
                            lineHeight = (3.7f * visualScale).sp,
                            letterSpacing = (-0.04f * visualScale).sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                        ),
                        color = Color(0xFF282522),
                        maxLines = 1,
                    )
                }
            }

            else -> {
                ThirdVariantPunctumMark(
                    visualScale = visualScale,
                    accentColor = accentColor,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp * visualScale, bottom = 4.dp * visualScale),
                )
                Text(
                    "PROCESSED BY PUNCTUM",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = (4.25f * visualScale).sp,
                        lineHeight = (4.7f * visualScale).sp,
                        letterSpacing = (-0.08f * visualScale).sp,
                        fontWeight = FontWeight(1000),
                    ),
                    color = accentColor,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 5.dp * visualScale, bottom = 8.dp * visualScale),
                )
            }
        }
    }
}

@Composable
private fun VariantOneProcessingBadge(
    visualScale: Float,
    modifier: Modifier = Modifier,
) {
    val ink = Color(0xFF27221E)
    val yellow = Color(0xFFF5B900)
    Row(
        modifier = modifier.size(
            width = 56.dp * visualScale,
            height = 17.dp * visualScale,
        )
            .background(ink, RoundedCornerShape(2.5.dp * visualScale))
            .padding(start = 2.dp * visualScale, end = 2.8.dp * visualScale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PunctumApertureMark(
            lineColor = yellow,
            dotColor = Color.White,
            modifier = Modifier.size(13.dp * visualScale),
        )
        Spacer(Modifier.width(2.2.dp * visualScale))
        Canvas(
            modifier = Modifier.size(
                width = 4.4.dp * visualScale,
                height = 10.dp * visualScale,
            ),
        ) {
            repeat(3) { index ->
                val y = size.height * (0.22f + index * 0.28f)
                drawLine(
                    color = yellow,
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                    strokeWidth = size.height * (0.09f + index * 0.018f),
                    cap = StrokeCap.Round,
                )
            }
        }
        Spacer(Modifier.width(2.dp * visualScale))
        Column(
            modifier = Modifier
                .weight(1f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "PROCESSED BY",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = (2.55f * visualScale).sp,
                    lineHeight = (2.9f * visualScale).sp,
                    letterSpacing = (-0.05f).sp,
                    fontWeight = FontWeight.Black,
                ),
                color = Color.White,
                maxLines = 1,
            )
            Text(
                "PUNCTUM",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = (5.05f * visualScale).sp,
                    lineHeight = (5.25f * visualScale).sp,
                    letterSpacing = (-0.20f).sp,
                    fontWeight = FontWeight(1000),
                ),
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ThirdVariantPunctumMark(
    visualScale: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val yellow = Color(0xFFF3B600)
    Row(
        modifier = modifier
            .size(width = 34.5.dp * visualScale, height = 15.dp * visualScale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(15.dp * visualScale)
                .background(yellow, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            PunctumApertureMark(
                lineColor = accentColor,
                dotColor = accentColor,
                modifier = Modifier.size(10.5.dp * visualScale),
            )
        }
        Spacer(Modifier.width(2.2.dp * visualScale))
        Box(
            modifier = Modifier
                .width(0.7.dp * visualScale)
                .height(9.dp * visualScale)
                .background(accentColor.copy(alpha = 0.88f)),
        )
        Spacer(Modifier.width(2.dp * visualScale))
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                "PUNCTUM",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = (2.35f * visualScale).sp,
                    lineHeight = (2.7f * visualScale).sp,
                    letterSpacing = (-0.18f).sp,
                    fontWeight = FontWeight(1000),
                ),
                color = accentColor,
                maxLines = 1,
            )
            Text(
                "ARCHIVE 01",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = (1.55f * visualScale).sp,
                    lineHeight = (1.9f * visualScale).sp,
                    letterSpacing = (0.08f * visualScale).sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = accentColor.copy(alpha = 0.82f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PunctumApertureMark(
    lineColor: Color,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.105f
        val center = androidx.compose.ui.geometry.Offset(
            x = size.width * 0.56f,
            y = size.height * 0.36f,
        )
        val radius = size.minDimension * 0.245f

        drawLine(
            color = lineColor,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height * 0.16f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height * 0.86f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = lineColor,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height * 0.17f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.54f, size.height * 0.17f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = lineColor,
            radius = radius,
            center = center,
            style = Stroke(width = stroke),
        )
        drawCircle(
            color = dotColor,
            radius = size.minDimension * 0.065f,
            center = center,
        )
        drawLine(
            color = lineColor,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.69f, size.height * 0.70f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.70f),
            strokeWidth = stroke * 0.68f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun LegacyReversalFilmCard(
    overview: GalleryOverview,
    visualScale: Float,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val cachedCover = remember(overview.ticketCoverPath) {
        overview.ticketCoverPath?.let(::File)?.takeIf(File::exists)
    }
    val latestUri = overview.coverUris.firstOrNull()
    val imageModel = remember(cachedCover, latestUri) {
        when {
            cachedCover != null -> ImageRequest.Builder(context)
                .data(cachedCover)
                .memoryCacheKey("reversal-film-file:${cachedCover.absolutePath}")
                .diskCacheKey("reversal-film-file:${cachedCover.absolutePath}")
                .crossfade(false)
                .build()
            latestUri != null -> inviteCoverRequest(context, latestUri)
            else -> null
        }
    }
    val titleIsChinese = remember(overview.gallery.displayName) {
        overview.gallery.displayName.any { it.code in 0x4E00..0x9FFF }
    }
    val cardShape = RoundedCornerShape(2.dp * visualScale)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .punctumPressable(
                pressedScale = 0.985f,
                pressedOffsetY = 4.dp,
                pressedAlpha = 0.96f,
                onClick = onClick,
            )
            .shadow(5.dp * visualScale, cardShape, clip = false)
            .clip(cardShape)
            .background(ReversalFilmPaper),
    ) {
        Image(
            painter = painterResource(R.drawable.reversal_film_paper_texture),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                GalleryTitleText(
                    text = overview.gallery.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = galleryTitleFont(overview.gallery.displayName),
                        fontSize = (
                            if (titleIsChinese) {
                                11.5f * visualScale - 2f
                            } else {
                                13f * visualScale - 2f
                            }
                            ).sp,
                        lineHeight = (13f * visualScale - 2f).sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = ReversalFilmInk,
                    alignment = Alignment.Center,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 5.dp * visualScale)
                        .offset(y = if (titleIsChinese) (-2).dp * visualScale else 3.dp * visualScale),
                    chineseStrokeWidth = 2f * visualScale,
                )
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 14.dp * visualScale)
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .clip(cardShape)
                    .background(Color(0xFF151412)),
            ) {
                if (imageModel != null) {
                    PunctumImage(
                        model = imageModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFF36342F)))
                }
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(8.dp * visualScale)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.70f),
                                    Color.Black.copy(alpha = 0.32f),
                                    Color.Black.copy(alpha = 0.10f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(6.dp * visualScale)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.62f),
                                    Color.Black.copy(alpha = 0.28f),
                                    Color.Black.copy(alpha = 0.09f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(6.dp * visualScale)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.07f),
                                    Color.Black.copy(alpha = 0.30f),
                                ),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(6.dp * visualScale)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.06f),
                                    Color.Black.copy(alpha = 0.26f),
                                ),
                            ),
                        ),
                )
            }

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        reversalFilmDateLine(overview),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = (6.5f * visualScale - 1f).sp,
                            lineHeight = (6.5f * visualScale - 1f).sp,
                            letterSpacing = 0.sp,
                        ),
                        color = ReversalFilmMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                    Spacer(Modifier.height(0.5.dp * visualScale))
                    Text(
                        "SLIDE · DIAPOSITIVE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = (5f * visualScale - 1f).sp,
                            lineHeight = (5f * visualScale - 1f).sp,
                            letterSpacing = (0.45f * visualScale).sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = ReversalFilmMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PostcardInvitationCard(
    overview: GalleryOverview,
    width: Dp,
    onClick: () -> Unit,
) {
    val paper = Color(0xFFE8DECC)
    val textColor = Color(0xFF24211D)
    val cardHeight = width * 1.70f

    Box(
        modifier = Modifier
            .width(width)
            .height(cardHeight)
            .punctumPressable(
                pressedScale = 0.985f,
                pressedOffsetY = 4.dp,
                pressedAlpha = 0.96f,
                onClick = onClick,
            )
            .background(paper)
            .border(1.dp, Color.Black.copy(alpha = 0.12f)),
    ) {
        PostcardPunctumsWatermark(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(width + 128.dp),
        )
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 28.dp),
            ) {
                CoverCollage(
                    uris = overview.coverUris,
                    coverPath = overview.postcardCoverPath,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
                Spacer(Modifier.height(22.dp))
                Column(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    Text(
                        postcardStoryLine(overview),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        postcardDateLine(overview),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = DetailSerif,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            letterSpacing = 0.sp,
                        ),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(22.dp))
                Box(modifier = Modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.CenterStart) {
                    GalleryTitleText(
                        text = overview.gallery.displayName,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = galleryTitleFont(overview.gallery.displayName),
                            fontSize = 32.sp,
                            lineHeight = 36.sp,
                        ),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Image(
                    painter = painterResource(R.drawable.postcard_footer_paper_texture),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 22.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "MOMENT · PUNCTUM · STUDIUM",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            lineHeight = 9.sp,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color.Black.copy(alpha = 0.52f),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "TAP TO ENTER EXHIBITION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            lineHeight = 9.sp,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color.Black.copy(alpha = 0.34f),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

@Composable
private fun PostcardPunctumsWatermark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val text = "PUNCTUMS"
        val edgeInset = 4.dp.toPx()
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFF6F6251).copy(alpha = 0.065f).toArgb()
            textSize = 68.sp.toPx()
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            isFakeBoldText = true
            textScaleX = 1.18f
        }
        val targetLength = size.height - edgeInset * 2
        var characterWidths = text.map { paint.measureText(it.toString()) }
        val naturalLength = characterWidths.sum()
        if (naturalLength > targetLength) {
            paint.textScaleX *= targetLength / naturalLength
            characterWidths = text.map { paint.measureText(it.toString()) }
        }
        val letterGap = if (text.length > 1) {
            ((targetLength - characterWidths.sum()) / (text.length - 1)).coerceAtLeast(0f)
        } else {
            0f
        }
        drawContext.canvas.nativeCanvas.run {
            save()
            translate(size.width - 8.dp.toPx(), size.height - edgeInset)
            rotate(-90f)
            var cursor = 0f
            text.forEachIndexed { index, character ->
                drawText(character.toString(), cursor, 0f, paint)
                cursor += characterWidths[index] + letterGap
            }
            restore()
        }
    }
}

@Composable
private fun TicketInvitationCard(
    overview: GalleryOverview,
    onClick: () -> Unit,
) {
    val ink = Color(0xFF050505)
    val ticketStubColor = overview.ticketDominantColorArgb
        ?.let { Color(it) }
        ?: TicketStubFallback
    val serialNumber = ticketNumber(overview)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(TicketCardHeight)
            .punctumPressable(
                pressedScale = 0.985f,
                pressedOffsetY = 4.dp,
                pressedAlpha = 0.96f,
                onClick = onClick,
            )
            .background(TicketPaper),
    ) {
        val ticketWidth = maxWidth
        val rightWidth = if (ticketWidth < 340.dp) 72.dp else 78.dp
        val cutLineOffset = TicketNotchSize / 2 - 1.dp
        val notchDepth = TicketNotchSize / 2
        val imageWidth = ticketWidth * 0.39f
        val titleIsChinese = overview.gallery.displayName.any { it.code in 0x4E00..0x9FFF }
        Image(
            painter = painterResource(R.drawable.ticket_paper_texture),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(ticketWidth - rightWidth + cutLineOffset)
                .fillMaxHeight(),
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Column(
            modifier = Modifier
                .width(ticketWidth - imageWidth - rightWidth)
                .fillMaxHeight()
                .padding(start = 12.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
        ) {
            GalleryTitleText(
                text = overview.gallery.displayName,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = galleryTitleFont(overview.gallery.displayName),
                    fontSize = if (titleIsChinese) 22.sp else 20.sp,
                    lineHeight = if (titleIsChinese) 25.sp else 23.sp,
                    fontWeight = MaterialTheme.typography.headlineMedium.fontWeight,
                ),
                color = ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            TicketInfoBand(
                story = "关于 ${overview.count} 幅作品的故事",
                time = compactTimeSpan(overview.timeSpan).ifBlank { "Time unknown" },
                color = ink,
            )
        }

        Box(
            modifier = Modifier
                .width(imageWidth)
                .fillMaxHeight(),
        ) {
            TicketImageStrip(
                uris = overview.coverUris,
                coverPath = overview.ticketCoverPath,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-2).dp)
                    .width(imageWidth - 6.dp)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp),
            )
        }

        Box(
            modifier = Modifier
                .width(rightWidth)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(rightWidth - cutLineOffset)
                    .fillMaxHeight()
                    .background(ticketStubColor),
            )
            DashedCutLine(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = cutLineOffset, y = notchDepth)
                    .height(TicketCardHeight - notchDepth * 2)
                    .width(2.dp),
            )
            TicketNotch(Modifier.align(Alignment.TopStart).offset(x = (-1).dp))
            TicketNotch(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-1).dp)
                    .graphicsLayer(rotationZ = 180f),
            )
            TicketStubLabels(
                marker = ticketMarker(overview),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 9.dp)
                    .width(14.dp)
                    .height(96.dp),
            )
            Barcode(
                seed = serialNumber,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 27.dp)
                    .width(28.dp)
                    .height(96.dp),
            )
            TicketSerialText(
                serialNumber,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
            )
        }
        }
    }
}

@Composable
private fun TicketInfoBand(story: String, time: String, color: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(0.92f).height(0.7.dp).background(color.copy(alpha = 0.48f)))
        Spacer(Modifier.height(1.dp))
        Text(
            story,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = NotoSerifSc,
                fontSize = 7.sp,
                lineHeight = 8.sp,
                letterSpacing = 0.sp,
                fontWeight = FontWeight.Normal,
                fontSynthesis = FontSynthesis.None,
            ),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            time,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 7.sp,
                lineHeight = 7.sp,
                letterSpacing = 0.sp,
                fontWeight = FontWeight.Normal,
            ),
            color = color,
            maxLines = 1,
        )
        Spacer(Modifier.height(1.dp))
        Box(Modifier.fillMaxWidth(0.92f).height(0.7.dp).background(color.copy(alpha = 0.48f)))
    }
}

@Composable
private fun TicketStubLabels(marker: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(14.dp)
                .height(20.dp),
        ) {
            Text(
                marker,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 7.sp,
                    lineHeight = 8.sp,
                    letterSpacing = 0.4.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(20.dp)
                    .graphicsLayer(rotationZ = -90f),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 14.dp)
                .width(14.dp)
                .height(48.dp),
        ) {
            Text(
                "CAPTURE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 6.5.sp,
                    lineHeight = 7.sp,
                    letterSpacing = 0.2.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(48.dp)
                    .graphicsLayer(rotationZ = -90f),
            )
        }
    }
}

@Composable
private fun SortDialog(
    overviews: List<GalleryOverview>,
    onMove: (Int, Int) -> Unit,
    onDelete: (Int) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    var orderedOverviews by remember { mutableStateOf(overviews) }
    var draggedGalleryKey by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var dragPointerY by remember { mutableStateOf<Float?>(null) }
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val rowHeight = 58.dp
    val listHeight = 290.dp
    val scrollbarThumbHeight = 36.dp
    val rowHeightPx = with(density) { rowHeight.toPx() }
    val listHeightPx = with(density) { listHeight.toPx() }
    val autoScrollEdgePx = with(density) { 48.dp.toPx() }
    val maxAutoScrollStepPx = with(density) { 4.dp.toPx() }
    val sortListState = rememberLazyListState()
    val showsScrollbar = orderedOverviews.size > 5
    val scrollRangePx =
        (orderedOverviews.size * rowHeightPx - listHeightPx).coerceAtLeast(1f)
    val scrollProgress =
        (sortListState.firstVisibleItemScrollOffset / scrollRangePx).coerceIn(0f, 1f)
    val scrollbarOffsetY = with(density) {
        ((listHeightPx - scrollbarThumbHeight.toPx()) * scrollProgress).toDp()
    }

    LaunchedEffect(overviews, draggedGalleryKey) {
        // Overview data refreshes every few seconds. Keep the drag snapshot intact until the
        // finger lifts, then absorb the latest metadata and persisted gallery order.
        if (draggedGalleryKey == null) orderedOverviews = overviews
    }

    fun applyDragReordering(galleryKey: String) {
        var currentIndex = orderedOverviews.indexOfFirst {
            it.gallery.uri.toString() == galleryKey
        }
        while (
            dragOffsetY > rowHeightPx / 2f &&
            currentIndex in 0 until orderedOverviews.lastIndex
        ) {
            val reordered = orderedOverviews.toMutableList()
            reordered.add(
                currentIndex + 1,
                reordered.removeAt(currentIndex),
            )
            orderedOverviews = reordered
            dragOffsetY -= rowHeightPx
            currentIndex += 1
            hapticFeedback.performHapticFeedback(
                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
            )
        }
        while (
            dragOffsetY < -rowHeightPx / 2f &&
            currentIndex > 0
        ) {
            val reordered = orderedOverviews.toMutableList()
            reordered.add(
                currentIndex - 1,
                reordered.removeAt(currentIndex),
            )
            orderedOverviews = reordered
            dragOffsetY += rowHeightPx
            currentIndex -= 1
            hapticFeedback.performHapticFeedback(
                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
            )
        }
    }

    LaunchedEffect(draggedGalleryKey) {
        val galleryKey = draggedGalleryKey ?: return@LaunchedEffect
        while (draggedGalleryKey == galleryKey) {
            withFrameNanos { }
            val pointerY = dragPointerY ?: continue
            val autoScrollStep = when {
                pointerY < autoScrollEdgePx -> {
                    val strength =
                        ((autoScrollEdgePx - pointerY) / autoScrollEdgePx).coerceIn(0.25f, 1f)
                    -maxAutoScrollStepPx * strength
                }
                pointerY > listHeightPx - autoScrollEdgePx -> {
                    val strength =
                        ((pointerY - (listHeightPx - autoScrollEdgePx)) / autoScrollEdgePx)
                            .coerceIn(0.25f, 1f)
                    maxAutoScrollStepPx * strength
                }
                else -> 0f
            }
            if (autoScrollStep.absoluteValue < 0.5f) continue

            val consumedScroll = sortListState.scrollBy(autoScrollStep)
            if (consumedScroll.absoluteValue >= 0.5f) {
                // Counter the list movement so the dragged row stays under the finger, then
                // swap as newly revealed galleries pass the halfway threshold.
                dragOffsetY += consumedScroll
                applyDragReordering(galleryKey)
            }
        }
    }

    fun finishDrag(commit: Boolean) {
        val draggedKey = draggedGalleryKey
        val endIndex = orderedOverviews.indexOfFirst {
            it.gallery.uri.toString() == draggedKey
        }
        if (commit && dragStartIndex >= 0 && endIndex >= 0 && dragStartIndex != endIndex) {
            onMove(dragStartIndex, endIndex)
        } else if (!commit) {
            orderedOverviews = overviews
        }
        draggedGalleryKey = null
        dragStartIndex = -1
        dragOffsetY = 0f
        dragPointerY = null
    }

    PunctumOverlayDialog(
        title = "调整图集画廊",
        onDismissRequest = onDismiss,
        actions = {},
        content = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(listHeight),
                ) {
                    LazyColumn(
                        state = sortListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds(),
                    ) {
                        item(key = "gallery-sort-rows") {
                            Column {
                                orderedOverviews.forEachIndexed { index, overview ->
                                val galleryKey = overview.gallery.uri.toString()
                                key(galleryKey) {
                                    val isDragging = draggedGalleryKey == galleryKey
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(rowHeight)
                                            .zIndex(if (isDragging) 1f else 0f)
                                            .graphicsLayer {
                                                translationY = if (isDragging) dragOffsetY else 0f
                                                scaleX = if (isDragging) 1.015f else 1f
                                                scaleY = if (isDragging) 1.015f else 1f
                                            }
                                            .background(
                                                color = if (isDragging) {
                                                    Bone.copy(alpha = 0.08f)
                                                } else {
                                                    Color.Transparent
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                            )
                                            .pointerInput(galleryKey) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { pointerOffset ->
                                                        val currentIndex =
                                                            orderedOverviews.indexOfFirst {
                                                                it.gallery.uri.toString() == galleryKey
                                                            }
                                                        draggedGalleryKey = galleryKey
                                                        dragStartIndex = currentIndex
                                                        dragOffsetY = 0f
                                                        dragPointerY =
                                                            currentIndex * rowHeightPx -
                                                                sortListState.firstVisibleItemScrollOffset +
                                                                pointerOffset.y
                                                        hapticFeedback.performHapticFeedback(
                                                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                                                        )
                                                    },
                                                    onDragCancel = { finishDrag(commit = false) },
                                                    onDragEnd = { finishDrag(commit = true) },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragPointerY = dragPointerY?.plus(dragAmount.y)
                                                        dragOffsetY += dragAmount.y
                                                        applyDragReordering(galleryKey)
                                                    },
                                                )
                                            }
                                            .padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            overview.gallery.displayName,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = galleryTitleFont(overview.gallery.displayName),
                                            ),
                                            color = Bone,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Box(
                                            modifier = Modifier.size(48.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Outlined.DragHandle,
                                                contentDescription = "长按拖动排序",
                                                tint = Muted,
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                        IconButton(onClick = { onDelete(index) }) {
                                            Icon(
                                                Icons.Outlined.DeleteOutline,
                                                contentDescription = "删除画廊",
                                                tint = Muted,
                                            )
                                        }
                                    }
                                }
                                }
                            }
                        }
                    }

                    if (showsScrollbar) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(y = scrollbarOffsetY)
                                .padding(end = 2.dp)
                                .width(3.dp)
                                .height(scrollbarThumbHeight)
                                .clip(RoundedCornerShape(50))
                                .background(Muted.copy(alpha = 0.72f)),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onAdd) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("添加图集画廊", color = Gold)
                }
            }
        },
    )
}

@Composable
private fun CoverCollage(uris: List<Uri>, coverPath: String?, modifier: Modifier = Modifier) {
    val cachedCover = remember(coverPath) { coverPath?.let { File(it) }?.takeIf { it.exists() } }
    if (cachedCover != null) {
        Box(modifier = modifier.background(Color(0xFF15110E)).padding(12.dp)) {
            val context = LocalContext.current
            val imageModel = remember(cachedCover) {
                ImageRequest.Builder(context)
                    .data(cachedCover)
                    .memoryCacheKey("invite-postcard-file:${cachedCover.absolutePath}")
                    .diskCacheKey("invite-postcard-file:${cachedCover.absolutePath}")
                    .crossfade(false)
                    .build()
            }
            PunctumImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else if (uris.size < 4) {
        CollageImage(
            uri = uris.firstOrNull(),
            modifier = modifier.background(Color(0xFF15110E)).padding(12.dp).fillMaxSize(),
        )
    } else {
        Column(
            modifier = modifier.background(Color(0xFF15110E)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CollageImage(uri = uris.getOrNull(0), modifier = Modifier.weight(1f).fillMaxHeight())
                CollageImage(uri = uris.getOrNull(1), modifier = Modifier.weight(1f).fillMaxHeight())
            }
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CollageImage(uri = uris.getOrNull(2), modifier = Modifier.weight(1f).fillMaxHeight())
                CollageImage(uri = uris.getOrNull(3), modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun TicketImageStrip(uris: List<Uri>, coverPath: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .padding(8.dp),
    ) {
        val cachedCover = remember(coverPath) { coverPath?.let { File(it) }?.takeIf { it.exists() } }
        val uri = uris.firstOrNull()
        if (cachedCover != null || uri != null) {
            val context = LocalContext.current
            val imageModel = remember(cachedCover, uri) {
                if (cachedCover != null) {
                    ImageRequest.Builder(context)
                        .data(cachedCover)
                        .memoryCacheKey("invite-ticket-file:${cachedCover.absolutePath}")
                        .diskCacheKey("invite-ticket-file:${cachedCover.absolutePath}")
                        .crossfade(false)
                        .build()
                } else {
                    inviteCoverRequest(context, uri!!)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF050505))
                    .padding(1.dp),
            ) {
                PunctumImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.52f), Color.Transparent),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.14f)),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.10f)),
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun CollageImage(uri: Uri?, modifier: Modifier) {
    Box(modifier = modifier.background(Surface1)) {
        if (uri != null) {
            val context = LocalContext.current
            val imageModel = remember(uri) { inviteCoverRequest(context, uri) }
            PunctumImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun inviteCoverRequest(context: android.content.Context, uri: Uri): ImageRequest =
    ImageRequest.Builder(context)
        .data(uri)
        .memoryCacheKey("invite-cover:$uri")
        .diskCacheKey("invite-cover:$uri")
        .size(700)
        .crossfade(false)
        .build()

@Composable
private fun DashedCutLine(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val dash = 8.dp.toPx()
        val gap = 6.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.White.copy(alpha = 0.82f),
                start = androidx.compose.ui.geometry.Offset(size.width / 2f, y),
                end = androidx.compose.ui.geometry.Offset(size.width / 2f, (y + dash).coerceAtMost(size.height)),
                strokeWidth = 1.3.dp.toPx(),
                cap = StrokeCap.Square,
            )
            y += dash + gap
        }
    }
}

@Composable
private fun TicketSerialText(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(14.dp)
            .height(96.dp)
            .clipToBounds(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        text.filter { it.isDigit() }.take(12).forEach { char ->
            Text(
                char.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 6.5.sp,
                    lineHeight = 6.5.sp,
                    letterSpacing = 0.sp,
                ),
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.graphicsLayer(rotationZ = -90f),
            )
        }
    }
}

@Composable
private fun Barcode(seed: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val digits = seed.filter { it.isDigit() }.ifBlank { "0101110" }
        val module = 0.72.dp.toPx()
        val quietZone = 2.dp.toPx()
        val maxY = size.height - quietZone
        var y = quietZone
        var index = 0
        while (y < maxY) {
            val digit = digits[index % digits.length].digitToInt()
            val barModules = 1 + ((digit + index) % 4)
            val spaceModules = 1 + ((digit * 3 + index) % 3)
            val barHeight = (barModules * module).coerceAtMost(maxY - y)
            drawRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(0f, y),
                size = androidx.compose.ui.geometry.Size(size.width, barHeight),
            )
            y += barHeight + spaceModules * module
            index += 1
        }
    }
}

@Composable
private fun TicketNotch(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(TicketNotchSize)) {
        drawCircle(
            color = Ink,
            radius = size.width / 2f,
            center = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
        )
    }
}

private fun postcardStoryLine(overview: GalleryOverview): String = "关于 ${overview.count} 幅作品的故事"

private fun postcardDateLine(overview: GalleryOverview): String =
    compactTimeSpan(overview.timeSpan).ifBlank { "时间未知" }

private fun reversalFilmDateLine(overview: GalleryOverview): String {
    val compact = compactTimeSpan(overview.timeSpan)
    if (compact.isBlank()) return "时间未知"
    val range = Regex("""^(\d{4})\.(\d{2})-(?:(\d{4})\.)?(\d{2})$""")
        .matchEntire(compact)
        ?: return compact
    val startYear = range.groupValues[1]
    val startMonth = range.groupValues[2]
    val endYear = range.groupValues[3].ifBlank { startYear }
    val endMonth = range.groupValues[4]
    return "$startYear.$startMonth — $endYear.$endMonth"
}

private fun compactTimeSpan(span: String): String {
    val compact = span
        .replace("年", ".")
        .replace("月", "")
        .replace(" ", "")
        .replace(Regex("""\.(\d)(\D|$)""")) { ".0${it.groupValues[1]}${it.groupValues[2]}" }
        .replace(Regex("""-(\d)(\D|$)""")) { "-0${it.groupValues[1]}${it.groupValues[2]}" }
    val sameYear = Regex("""^(\d{4})\.(\d{2})-(\d{2})$""").matchEntire(compact) ?: return compact
    val year = sameYear.groupValues[1]
    return "$year.${sameYear.groupValues[2]}-$year.${sameYear.groupValues[3]}"
}

private fun ticketNumber(overview: GalleryOverview): String {
    val hash = overview.gallery.uri.toString().hashCode().absoluteValue
    val left = (hash % 10_000_000).toString().padStart(7, '0')
    return "$left - 0101110"
}

private fun ticketMarker(overview: GalleryOverview): String {
    val hash = overview.gallery.uri.toString().hashCode().toLong().absoluteValue
    return ((hash % 90) + 10).toString()
}

private val TicketStubFallback = Color(0xFF57534E)
private val TicketPaper = Color(0xFFFFF5E6)
private val TicketCardHeight = 116.dp
private val TicketNotchSize = 10.dp
private const val KodakInspiredReversalFilmEnabled = false
private val ReversalFilmPaper = Color(0xFFD0C8BC)
private val ReversalFilmInk = Color(0xFF292722)
private val ReversalFilmMuted = Color(0xFF756E62)
