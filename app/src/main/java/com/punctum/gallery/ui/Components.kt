package com.punctum.gallery.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.punctum.gallery.ui.theme.Bone
import com.punctum.gallery.ui.theme.Gold
import com.punctum.gallery.ui.theme.Hairline
import com.punctum.gallery.ui.theme.Muted

@Composable
internal fun SectionLabel(text: String, color: Color = Muted) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
internal fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Hairline))
}

@Composable
internal fun FrameButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(1.dp, Gold.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Gold)
    }
}

@Composable
internal fun ExifField(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
            modifier = Modifier.width(132.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = Bone,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun PunctumImage(
    model: Any?,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    animateOnLoad: Boolean = true,
    onSuccess: () -> Unit = {},
    onError: () -> Unit = {},
) {
    var loaded by remember(model, animateOnLoad) { mutableStateOf(!animateOnLoad) }
    val alpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "image-alpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (loaded) 1f else 1.018f,
        animationSpec = tween(durationMillis = 240),
        label = "image-scale",
    )
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        onSuccess = {
            loaded = true
            onSuccess()
        },
        onError = { onError() },
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            scaleX = scale
            scaleY = scale
        },
    )
}

@Composable
internal fun MoveToAlbumIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 23.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = (this.size.minDimension * 0.085f).coerceAtLeast(1.6f)
        val back = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = this@Canvas.size.width * 0.08f,
                    top = this@Canvas.size.height * 0.08f,
                    right = this@Canvas.size.width * 0.54f,
                    bottom = this@Canvas.size.height * 0.66f,
                    radiusX = this@Canvas.size.width * 0.06f,
                    radiusY = this@Canvas.size.width * 0.06f,
                ),
            )
        }
        val front = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = this@Canvas.size.width * 0.22f,
                    top = this@Canvas.size.height * 0.28f,
                    right = this@Canvas.size.width * 0.68f,
                    bottom = this@Canvas.size.height * 0.86f,
                    radiusX = this@Canvas.size.width * 0.06f,
                    radiusY = this@Canvas.size.width * 0.06f,
                ),
            )
        }
        drawPath(back, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(front, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(
            color = tint,
            start = Offset(this.size.width * 0.58f, this.size.height * 0.42f),
            end = Offset(this.size.width * 0.92f, this.size.height * 0.42f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        val head = Path().apply {
            moveTo(this@Canvas.size.width * 0.78f, this@Canvas.size.height * 0.26f)
            lineTo(this@Canvas.size.width * 0.94f, this@Canvas.size.height * 0.42f)
            lineTo(this@Canvas.size.width * 0.78f, this@Canvas.size.height * 0.58f)
        }
        drawPath(head, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
