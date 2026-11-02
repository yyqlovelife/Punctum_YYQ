package com.punctum.gallery.ui

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.request.ImageRequest
import coil.size.Precision
import com.punctum.gallery.R
import com.punctum.gallery.model.Film120Geometry
import com.punctum.gallery.model.GalleryOverview
import com.punctum.gallery.model.filmHomeDate
import com.punctum.gallery.ui.theme.*

// Preserve the old home and its scroll state behind this single rollback switch.
internal const val FilmStripHomeEnabled = true
private const val HOME_LOG_TAG = "PunctumHome"

/** All dimensions are measured in the supplied 672px-wide reference image. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilmStripHomeHeader(
    title: String, subtitle: String, canClose: Boolean,
    onStyle: () -> Unit, onSort: () -> Unit, onClose: () -> Unit, onTop: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val s = maxWidth.value / 672f
        val status = with(LocalDensity.current) { WindowInsets.statusBarsIgnoringVisibility.getTop(this).toDp() }
        fun y(reference: Float) = (reference * s).dp - status
        Box(Modifier.fillMaxWidth().height((442f * s).dp - status)) {
            Text("- PUNCTUM · STUDIUM -", color = Muted,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = (24 * s).sp,
                    lineHeight = (32 * s).sp, letterSpacing = (1.4f * s).sp),
                modifier = Modifier.offset((42 * s).dp, y(149f)).clickable(onClick = onTop))
            Row(Modifier.align(Alignment.TopEnd).offset(y = y(119f)).padding(end = (24 * s).dp)) {
                PressFeedbackIconButton(onClick = onStyle, activateImmediatelyOnRelease = true) {
                    Icon(Icons.Outlined.Style, "切换邀请卡风格", tint = Muted, modifier = Modifier.size((40 * s).dp))
                }
                PressFeedbackIconButton(onClick = onSort) {
                    Icon(Icons.Outlined.Sort, "调整排序", tint = Muted, modifier = Modifier.size((40 * s).dp))
                }
                if (canClose) PressFeedbackIconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, "关闭", tint = Muted)
                }
            }
            Text(title, color = Bone, maxLines = 1, overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.displayMedium.copy(fontSize = (64 * s).sp,
                    lineHeight = (84 * s).sp, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                modifier = Modifier.offset((42 * s).dp, y(236f)).fillMaxWidth().padding(end = (42 * s).dp))
            Text(subtitle, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = (30 * s).sp,
                    lineHeight = (42 * s).sp, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                modifier = Modifier.offset((42 * s).dp, y(318f)).padding(end = (42 * s).dp))
        }
    }
}

@Composable
internal fun FilmStripHomeList(overviews: List<GalleryOverview>, state: LazyGridState, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val labelPaper = painterResource(R.drawable.film_label_paper_texture)
    // One shared decode per screen, independent of album count. Photographs use Coil's bounded caches.
    val frame = remember { BitmapFactory.decodeResource(context.resources, R.drawable.film120_frame) }
    val digits = remember { BitmapFactory.decodeResource(context.resources, R.drawable.film120_digits) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val s = maxWidth.value / 672f
        // Fixed Nobel PJW110 calibration (1080x2412, density 3): 165px then 166px.
        // No screen-height adaptation; the slightly thicker labels finish at the bottom edge.
        val firstAlbumGap = 55.dp
        val nextAlbumGap = (166f / 3f).dp
        LazyVerticalGrid(columns = GridCells.Fixed(1), state = state, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(overviews, key = { _, ov -> ov.gallery.uri.toString() }) { index, ov ->
                Column {
                    Column(Modifier.fillMaxWidth().homeMotionAnchor(ov.gallery.uri.toString())
                        .punctumPressable(
                            pressedScale = 0.985f,
                            pressedOffsetY = 4.dp,
                            pressedAlpha = 0.96f,
                            activateImmediatelyOnRelease = true,
                            onClick = { onSelect(ov.gallery.uri.toString()) },
                        )) {
                        Box(Modifier.fillMaxWidth().height((220 * s).dp).clipToBounds()) {
                            val tileWidth = 220f * Film120Geometry.WIDTH / Film120Geometry.HEIGHT
                            repeat(4) { slot ->
                                val uri = ov.coverUris.getOrNull(slot)
                                val revision = ov.coverRevisions.getOrNull(slot).orEmpty()
                                val key = "film-home120-v1:$uri:$revision"
                                Box(Modifier.offset(x = ((-58f + slot * tileWidth) * s).dp)
                                    .size((tileWidth * s).dp, (220 * s).dp).background(Color(0xFF171717))) {
                                    if (uri != null) {
                                        val request = remember(uri, revision) {
                                            ImageRequest.Builder(context).data(uri).size(720)
                                                .precision(Precision.EXACT)
                                                .memoryCacheKey(key).diskCacheKey(key).crossfade(false).build()
                                        }
                                        PunctumImage(request, null, ContentScale.Crop,
                                            Modifier.offset((Film120Geometry.PHOTO_LEFT / Film120Geometry.WIDTH * tileWidth * s).dp,
                                                (Film120Geometry.PHOTO_TOP / Film120Geometry.HEIGHT * 220 * s).dp)
                                                .size((Film120Geometry.PHOTO_SIDE / Film120Geometry.WIDTH * tileWidth * s).dp),
                                            animateOnLoad = false,
                                            onImageSize = { w, h -> if (Log.isLoggable(HOME_LOG_TAG, Log.DEBUG))
                                                Log.d(HOME_LOG_TAG, "cover slot=$slot decoded=${w}x$h revision=$revision") })
                                    }
                                    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
                                    val dest = remember { RectF() }
                                    val src = remember { Rect() }
                                    Canvas(Modifier.fillMaxSize()) {
                                        val scale = size.width / Film120Geometry.WIDTH
                                        drawIntoCanvas { c ->
                                            dest.set(0f, 0f, size.width, size.height)
                                            c.nativeCanvas.drawBitmap(frame, null, dest, paint)
                                            fun number(value: String, left: Float, top: Float) {
                                                value.forEachIndexed { i, ch ->
                                                    val n = ch.digitToInt()
                                                    val gw = Film120Geometry.DIGIT_WIDTH
                                                    src.set((n * gw).toInt(), 0, ((n + 1) * gw).toInt(), Film120Geometry.DIGIT_HEIGHT.toInt())
                                                    val x = (left + i * gw) * scale
                                                    dest.set(x, top * scale, x + gw * scale, (top + Film120Geometry.DIGIT_HEIGHT) * scale)
                                                    c.nativeCanvas.drawBitmap(digits, src, dest, paint)
                                                }
                                            }
                                            if (uri != null) {
                                                number(Film120Geometry.emulsionCode(uri.toString()), Film120Geometry.RANDOM_LEFT, 0f)
                                                number((slot + 1).toString(), Film120Geometry.NUMBER_LEFT, Film120Geometry.NUMBER_TOP)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth().height((76 * s).dp).clipToBounds().background(Color.White)) {
                            Image(labelPaper, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize())
                            Row(Modifier.fillMaxSize().padding(horizontal = (20 * s).dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(filmHomeText(ov.gallery.displayName), color = Color.Black,
                                    fontFamily = AveriaSerifLibre, fontSize = (38 * s).sp, lineHeight = (46 * s).sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium.copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                                    modifier = Modifier.weight(1f))
                                Spacer(Modifier.width((14 * s).dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(filmHomeText(filmHomeDate(ov.timeSpan)), color = Color(0xFF606060),
                                        fontFamily = AveriaSerifLibre, fontSize = (16 * s).sp, lineHeight = (20 * s).sp,
                                        letterSpacing = (0.6f * s).sp, maxLines = 1)
                                    Text(filmHomeText(if (ov.loading) "正在读取图集" else "Stories Behind ${ov.count} Photographs"),
                                        color = Color(0xFF606060), fontFamily = AveriaSerifLibre,
                                        fontSize = (16 * s).sp, lineHeight = (22 * s).sp,
                                        letterSpacing = 0.sp, maxLines = 1, textAlign = TextAlign.End)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(if (index == 0) firstAlbumGap else nextAlbumGap))
                }
            }
            item(key = "navbar") { Spacer(Modifier.navigationBarsPadding().height(8.dp)) }
        }
    }
}

private fun filmHomeText(text: String) = buildAnnotatedString {
    text.forEach { ch ->
        withStyle(SpanStyle(fontFamily = if (ch.code in 0x2E80..0x9FFF) NotoSerifSc else AveriaSerifLibre)) { append(ch) }
    }
}
