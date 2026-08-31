package com.punctum.gallery.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.punctum.gallery.ui.theme.Bone
import com.punctum.gallery.ui.theme.Gold
import com.punctum.gallery.ui.theme.Hairline
import com.punctum.gallery.ui.theme.Muted
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun SectionLabel(text: String, color: Color = Muted) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
internal fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Hairline))
}

@Composable
internal fun Modifier.punctumPressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.88f,
    pressedOffsetY: Dp = 2.dp,
    pressedAlpha: Float = 0.78f,
    onClick: () -> Unit,
): Modifier {
    val progress = remember { Animatable(0f) }
    val currentOnClick by rememberUpdatedState(onClick)

    LaunchedEffect(enabled) {
        if (!enabled) progress.snapTo(0f)
    }

    return this
        .graphicsLayer {
            val amount = progress.value
            val scale = 1f + (pressedScale - 1f) * amount
            scaleX = scale
            scaleY = scale
            translationY = pressedOffsetY.toPx() * amount
            alpha = 1f + (pressedAlpha - 1f) * amount
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    coroutineScope {
                        val pressJob = launch {
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = 110,
                                    easing = LinearOutSlowInEasing,
                                ),
                            )
                        }
                        val released = tryAwaitRelease()
                        if (released) {
                            pressJob.join()
                            progress.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.72f,
                                    stiffness = 900f,
                                ),
                            )
                            currentOnClick()
                        } else {
                            pressJob.cancel()
                            progress.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.76f,
                                    stiffness = 900f,
                                ),
                            )
                        }
                    }
                },
            )
        }
        .semantics {
            role = Role.Button
            if (enabled) {
                onClick {
                    currentOnClick()
                    true
                }
            } else {
                disabled()
            }
        }
}

@Composable
internal fun PressFeedbackIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .punctumPressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
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
