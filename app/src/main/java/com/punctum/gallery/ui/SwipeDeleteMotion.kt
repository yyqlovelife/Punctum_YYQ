package com.punctum.gallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.punctum.gallery.ui.theme.Bone

internal const val DELETE_ARM_PROGRESS = 0.72f
internal const val DELETE_DRAG_RESISTANCE = 0.14f
internal const val DELETE_DRAG_MAX_PROGRESS = 1.12f
internal const val DELETE_COMMIT_DURATION_MS = 200
internal const val DELETE_SETTLE_DURATION_MS = 50L

internal fun Modifier.deletePageTransform(
    dragProgress: Float,
    commitProgress: Float,
    committedDragProgress: Float,
    targetTranslationYPx: Float,
    density: Density,
): Modifier {
    return deletePageTransform({ dragProgress }, { commitProgress }, { committedDragProgress }, targetTranslationYPx, density)
}

internal fun Modifier.deletePageTransform(
    drag: () -> Float, commit: () -> Float, committed: () -> Float,
    targetTranslationYPx: Float, density: Density,
): Modifier = graphicsLayer {
    val dragProgress = drag()
    val commitProgress = commit()
    val committedDragProgress = committed()
    val sourceDrag = if (commitProgress > 0f) committedDragProgress else dragProgress
    val dragBase = sourceDrag.coerceIn(0f, 1f)
    val dragExtra = ((sourceDrag - 1f) / (DELETE_DRAG_MAX_PROGRESS - 1f)).coerceIn(0f, 1f)
    val startScale = 1f - dragBase * 0.08f - dragExtra * 0.01f
    val startYDp = -dragBase * 132f - dragExtra * 18f
    val startRadiusDp = dragBase * 18f + dragExtra * 2f
    val startShadowDp = 10f + dragBase * 24f

    val continuousProgress = commitProgress.coerceIn(0f, 1f)
    val motionProgress = easeOutQuadratic(continuousProgress)
    val shapeProgress = smoothStep(continuousProgress)
    val scale = mix(startScale, 0.035f, shapeProgress)
    val startYPx = with(density) { startYDp.dp.toPx() }
    val yPx = mix(startYPx, targetTranslationYPx, motionProgress)
    val radiusDp = mix(startRadiusDp, 180f, shapeProgress)
    val shadowDp = mix(startShadowDp, 0f, shapeProgress)
    val fadeProgress = ((continuousProgress - 0.88f) / 0.12f).coerceIn(0f, 1f)
    val cardAlpha = 1f - LinearOutSlowInEasing.transform(fadeProgress)
        scaleX = scale
        scaleY = scale
        translationY = yPx
        alpha = cardAlpha
        shape = RoundedCornerShape(radiusDp.dp)
        clip = true
        shadowElevation = with(density) { shadowDp.dp.toPx() }

}

internal fun mapDeleteDragProgress(rawProgress: Float): Float {
    val positiveProgress = rawProgress.coerceAtLeast(0f)
    if (positiveProgress <= 1f) return positiveProgress
    val resistedProgress = 1f + (positiveProgress - 1f) * DELETE_DRAG_RESISTANCE
    return resistedProgress.coerceAtMost(DELETE_DRAG_MAX_PROGRESS)
}

internal fun deletePageCornerRadius(
    dragProgress: Float,
    commitProgress: Float,
    committedDragProgress: Float,
): Float {
    val sourceDrag = if (commitProgress > 0f) committedDragProgress else dragProgress
    val dragBase = sourceDrag.coerceIn(0f, 1f)
    val dragExtra = ((sourceDrag - 1f) / (DELETE_DRAG_MAX_PROGRESS - 1f)).coerceIn(0f, 1f)
    val startRadius = dragBase * 18f + dragExtra * 2f
    return mix(startRadius, 180f, smoothStep(commitProgress.coerceIn(0f, 1f)))
}

internal fun deleteBackgroundReveal(
    dragProgress: Float,
    commitProgress: Float,
    committedDragProgress: Float,
): Float {
    val sourceDrag = if (commitProgress > 0f) committedDragProgress else dragProgress
    val dragReveal = (sourceDrag / DELETE_DRAG_MAX_PROGRESS).coerceIn(0f, 1f) * 0.30f
    if (commitProgress <= 0f) return dragReveal
    return mix(dragReveal, 1f, easeOutQuadratic(commitProgress.coerceIn(0f, 1f)))
}

internal fun deleteButtonPulse(commitProgress: Float): Float {
    if (commitProgress <= 0.72f) return 1f
    val targetProgress = ((commitProgress - 0.72f) / 0.28f).coerceIn(0f, 1f)
    return if (targetProgress <= 0.68f) {
        mix(1f, 1.08f, targetProgress / 0.68f)
    } else {
        mix(1.08f, 1f, (targetProgress - 0.68f) / 0.32f)
    }
}

internal fun easeOutQuadratic(progress: Float): Float {
    val remaining = 1f - progress.coerceIn(0f, 1f)
    return 1f - remaining * remaining
}

internal fun smoothStep(progress: Float): Float {
    val clamped = progress.coerceIn(0f, 1f)
    return clamped * clamped * (3f - 2f * clamped)
}

internal fun mix(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress.coerceIn(0f, 1f)

@Composable
internal fun SwipeDeleteIndicator(
    visible: Boolean, armed: Boolean, commitProgress: Float, toastVisible: Boolean,
    modifier: Modifier = Modifier, onTargetCenterY: (Float) -> Unit = {},
) {
    SwipeDeleteIndicator(visible, armed, { commitProgress }, toastVisible, modifier, onTargetCenterY)
}

@Composable
internal fun SwipeDeleteIndicator(
    visible: Boolean, armed: Boolean, commitProgress: () -> Float, toastVisible: Boolean,
    modifier: Modifier = Modifier, onTargetCenterY: (Float) -> Unit = {},
) {
    AnimatedVisibility(visible, modifier, enter = fadeIn(tween(120)), exit = fadeOut(tween(120))) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(48.dp)
                    .onGloballyPositioned { coordinates ->
                        onTargetCenterY(coordinates.localToRoot(Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)).y)
                    }
                    .graphicsLayer {
                        val pulse = deleteButtonPulse(commitProgress())
                        scaleX = pulse
                        scaleY = pulse
                    }
                    .background(if (armed) Color(0xFFE24646) else Color(0xFF2C2C2C).copy(alpha = .78f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Delete, "上滑删除", tint = if (armed) Color.White else Bone.copy(alpha = .72f), modifier = Modifier.size(27.dp))
            }
            AnimatedVisibility(
                armed && toastVisible,
                Modifier.align(Alignment.TopCenter).padding(top = 76.dp),
                enter = fadeIn(tween(140)), exit = fadeOut(tween(120)),
            ) {
                Text("松手标记删除，退出大图页时统一确认",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Bone.copy(alpha = .78f),
                    modifier = Modifier.background(Color(0xFF191919).copy(alpha = .78f), CircleShape).padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
    }
}
