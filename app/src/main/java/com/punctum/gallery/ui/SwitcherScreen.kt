package com.punctum.gallery.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.punctum.gallery.model.Gallery
import com.punctum.gallery.model.GalleryOverview
import com.punctum.gallery.model.InvitationCardStyle
import com.punctum.gallery.ui.theme.Bone
import com.punctum.gallery.ui.theme.Gold
import com.punctum.gallery.ui.theme.Hairline
import com.punctum.gallery.ui.theme.Ink
import com.punctum.gallery.ui.theme.Muted
import com.punctum.gallery.ui.theme.Surface1
import com.punctum.gallery.ui.theme.galleryTitleFont
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
internal fun SwitcherScreen(
    overviews: List<GalleryOverview>,
    canClose: Boolean,
    title: String = "Your Punctums",
    subtitle: String = "每一份画廊邀请卡，都是你来时的路",
    invitationStyle: InvitationCardStyle,
    postcardListState: LazyListState,
    ticketListState: LazyListState,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onToggleInvitationStyle: () -> Unit,
    onRename: (Gallery) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDelete: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val activeListState = if (invitationStyle == InvitationCardStyle.POSTCARD) postcardListState else ticketListState
    val scope = rememberCoroutineScope()
    var showSortDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { scope.launch { activeListState.animateScrollToItem(0) } })
                }
                .padding(start = 24.dp, end = 12.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("SELECT EXHIBITION")
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleInvitationStyle) {
                Icon(Icons.Outlined.Style, contentDescription = "切换邀请卡风格", tint = Muted)
            }
            IconButton(onClick = { showSortDialog = true }) {
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
        ) { index, ov ->
            TicketInvitationCard(
                overview = ov,
                paper = if (index % 2 == 0) TicketWarmPaper else TicketLightPaper,
                onClick = { onSelect(ov.gallery.uri.toString()) },
            )
        }
        item(key = "navbar") {
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
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
    val mutedOnPaper = Color(0xFF6B604F)
    val cardHeight = width * 1.62f

    Column(
        modifier = Modifier
            .width(width)
            .height(cardHeight)
            .background(paper)
            .border(1.dp, Color.Black.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 22.dp),
    ) {
        CoverCollage(
            uris = overview.coverUris,
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
            Text(
                postcardDateLine(overview),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(22.dp))
        Box(modifier = Modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                overview.gallery.displayName,
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
        Box(modifier = Modifier.fillMaxWidth().height(46.dp)) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.Black.copy(alpha = 0.22f)),
            )
            Text(
                "Tap to enter exhibition",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp, letterSpacing = 2.sp),
                color = mutedOnPaper,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

@Composable
private fun TicketInvitationCard(
    overview: GalleryOverview,
    paper: Color,
    onClick: () -> Unit,
) {
    val ink = Color(0xFF050505)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp)
            .background(paper)
            .clickable(onClick = onClick),
    ) {
        val ticketWidth = maxWidth
        val rightWidth = if (ticketWidth < 340.dp) 72.dp else 78.dp
        val imageWidth = ticketWidth * 0.39f
        val titleIsChinese = overview.gallery.displayName.any { it.code in 0x4E00..0x9FFF }
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
            Text(
                overview.gallery.displayName,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = galleryTitleFont(overview.gallery.displayName),
                    fontSize = if (titleIsChinese) 25.sp else 20.sp,
                    lineHeight = if (titleIsChinese) 28.sp else 23.sp,
                ),
                color = ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "关于 ${overview.count} 幅作品的故事",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 9.sp, lineHeight = 11.sp),
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                compactTimeSpan(overview.timeSpan).ifBlank { "Time unknown" },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp),
                color = ink,
                maxLines = 1,
            )
        }

        TicketImageStrip(
            uris = overview.coverUris,
            modifier = Modifier
                .width(imageWidth)
                .fillMaxHeight()
                .padding(vertical = 10.dp),
        )

        Box(
            modifier = Modifier
                .width(rightWidth)
                .fillMaxHeight()
                .background(paper),
        ) {
            DashedCutLine(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 8.dp, y = 10.dp)
                    .height(96.dp)
                    .width(2.dp),
            )
            TicketNotch(Modifier.align(Alignment.TopStart).offset(x = (-1).dp))
            TicketNotch(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-1).dp)
                    .graphicsLayer(rotationZ = 180f),
            )
            Barcode(
                seed = ticketNumber(overview),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .width(27.dp)
                    .height(96.dp),
            )
            TicketSerialText(
                ticketNumber(overview),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
            )
        }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = {
            Text("调整邀请卡", style = MaterialTheme.typography.titleLarge, color = Bone)
        },
        text = {
            Column {
                overviews.forEachIndexed { index, overview ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
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
                        IconButton(
                            enabled = index > 0,
                            onClick = { onMove(index, index - 1) },
                        ) {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移", tint = Muted)
                        }
                        IconButton(
                            enabled = index < overviews.lastIndex,
                            onClick = { onMove(index, index + 1) },
                        ) {
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移", tint = Muted)
                        }
                        IconButton(onClick = { onDelete(index) }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除画廊", tint = Muted)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onAdd) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("添加画廊", color = Gold)
                }
            }
        },
        containerColor = Ink,
    )
}

@Composable
private fun CoverCollage(uris: List<Uri>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.background(Color(0xFF15110E)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CollageImage(uri = uris.getOrNull(0), modifier = Modifier.fillMaxWidth().weight(1f))
            CollageImage(uri = uris.getOrNull(1), modifier = Modifier.fillMaxWidth().weight(1.45f))
        }
        CollageImage(
            uri = uris.getOrNull(2),
            modifier = Modifier.weight(1.18f).fillMaxHeight(),
        )
    }
}

@Composable
private fun TicketImageStrip(uris: List<Uri>, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Surface1)) {
        val uri = uris.firstOrNull()
        if (uri != null) {
            val context = LocalContext.current
            val imageModel = remember(uri) { inviteCoverRequest(context, uri) }
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CollageImage(uri: Uri?, modifier: Modifier) {
    Box(modifier = modifier.background(Surface1)) {
        if (uri != null) {
            val context = LocalContext.current
            val imageModel = remember(uri) { inviteCoverRequest(context, uri) }
            AsyncImage(
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
        modifier = modifier.width(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        text.filter { it.isDigit() }.take(12).forEach { char ->
            Text(
                char.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    lineHeight = 8.sp,
                    letterSpacing = 0.sp,
                ),
                color = Color.Black,
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
        var y = 0f
        var index = 0
        while (y < size.height) {
            val digit = digits[index % digits.length].digitToInt()
            val bar = (1.2f + (digit % 4) * 0.65f).dp.toPx()
            val gap = if ((digit + index) % 5 == 0) 1.8.dp.toPx() else 0.9.dp.toPx()
            drawLine(
                color = Color.Black,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = bar,
                cap = StrokeCap.Square,
            )
            y += bar + gap
            index += 1
        }
    }
}

@Composable
private fun TicketNotch(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2f, size.height * 0.72f)
            close()
        }
        drawPath(path = path, color = Ink)
    }
}

private fun postcardStoryLine(overview: GalleryOverview): String = "关于 ${overview.count} 幅作品的故事"

private fun postcardDateLine(overview: GalleryOverview): String =
    compactTimeSpan(overview.timeSpan).ifBlank { "时间未知" }

private fun compactTimeSpan(span: String): String {
    return span
        .replace("年", ".")
        .replace("月", "")
        .replace(" ", "")
        .replace(Regex("""\.(\d)(\D|$)""")) { ".0${it.groupValues[1]}${it.groupValues[2]}" }
        .replace(Regex("""-(\d)(\D|$)""")) { "-0${it.groupValues[1]}${it.groupValues[2]}" }
}

private fun ticketNumber(overview: GalleryOverview): String {
    val hash = overview.gallery.uri.toString().hashCode().absoluteValue
    val left = (hash % 10_000_000).toString().padStart(7, '0')
    return "$left - 0101110"
}

private val TicketWarmPaper = Color(0xFFE5D2B3)
private val TicketLightPaper = Color(0xFFEEE3CF)
