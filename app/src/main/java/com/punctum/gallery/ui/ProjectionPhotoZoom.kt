package com.punctum.gallery.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.punctum.gallery.model.PhotoZoomState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Same retained 1–5x pose and reset spring as the ordinary photo detail page. */
@Stable
internal class ProjectionPhotoZoom(private val scope: CoroutineScope) {
    var pose by mutableStateOf(PhotoZoomState())
        private set
    var gestureInProgress by mutableStateOf(false)
        private set
    private var returning by mutableStateOf(false)
    private var returnJob: Job? = null
    private var lastTapTime: Long? = null
    private var lastTapPosition = Offset.Zero
    val isActive by derivedStateOf { gestureInProgress || returning || pose.isZoomed }

    fun clear() {
        returnJob?.cancel()
        returnJob = null
        pose = PhotoZoomState()
        gestureInProgress = false
        returning = false
        lastTapTime = null
    }

    fun claim() {
        returnJob?.cancel()
        returning = false
        gestureInProgress = true
    }

    fun transform(factor: Float, focal: Offset, pan: Offset, frame: Rect) {
        pose = pose.transform(factor, focal.x, focal.y, pan.x, pan.y, frame.width, frame.height)
    }

    fun finish() { gestureInProgress = false }
    fun forgetTap() { lastTapTime = null }

    fun tap(time: Long, position: Offset, minTime: Long, timeout: Long, slop: Float) {
        val interval = lastTapTime?.let { time - it }
        if (interval != null && interval in minTime..timeout &&
            (position - lastTapPosition).getDistance() <= slop
        ) {
            reset()
        } else {
            lastTapTime = time
            lastTapPosition = position
        }
    }

    private fun reset() {
        returnJob?.cancel()
        lastTapTime = null
        returning = true
        returnJob = scope.launch {
            val start = pose
            if (ValueAnimator.areAnimatorsEnabled()) {
                Animatable(1f).animateTo(0f, spring(dampingRatio = .88f, stiffness = 700f)) {
                    val progress = value.coerceIn(0f, 1f)
                    pose = PhotoZoomState(1f + (start.scale - 1f) * progress,
                        start.offsetX * progress, start.offsetY * progress)
                }
            }
            pose = PhotoZoomState()
            returning = false
        }
    }
}

/** Initial-pass ownership prevents the pager/delete detector from consuming a pinch or zoomed pan. */
internal fun Modifier.projectionZoomInput(
    zoom: ProjectionPhotoZoom,
    identity: Any?,
    enabled: Boolean,
    doubleTapSlop: Float,
    frame: (Float, Float) -> Rect,
    canStart: () -> Boolean,
    onClaim: () -> Unit,
): Modifier = pointerInput(identity, enabled) {
    if (!enabled) return@pointerInput
    try {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            fun currentFrame() = frame(size.width.toFloat(), size.height.toFloat())
            fun inPhoto(position: Offset): Boolean {
                val bounds = currentFrame()
                val pose = zoom.pose
                val center = bounds.center + Offset(pose.offsetX, pose.offsetY)
                return position.x in 0f..size.width.toFloat() && position.y in 0f..size.height.toFloat() &&
                    position.x in (center.x - bounds.width * pose.scale / 2f)..(center.x + bounds.width * pose.scale / 2f) &&
                    position.y in (center.y - bounds.height * pose.scale / 2f)..(center.y + bounds.height * pose.scale / 2f)
            }
            var claimed = zoom.pose.isZoomed && canStart() && inPhoto(down.position)
            if (claimed) { zoom.claim(); onClaim(); down.consume() }
            var multiple = false
            var moved = false
            var previousCount = 1
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (!claimed && pressed.size >= 2 && canStart() && pressed.take(2).all { inPhoto(it.position) }) {
                    claimed = true
                    zoom.claim()
                    onClaim()
                }
                if (claimed) {
                    if (pressed.size >= 2) { multiple = true; zoom.forgetTap() }
                    // A joining/leaving finger must not move the focal point discontinuously.
                    if (pressed.isNotEmpty() && pressed.size == previousCount) {
                        val bounds = currentFrame()
                        zoom.transform(if (pressed.size >= 2) event.calculateZoom() else 1f,
                            event.calculateCentroid(useCurrent = false) - bounds.center, event.calculatePan(), bounds)
                    }
                    if (multiple || event.changes.any {
                            (it.position - down.position).getDistance() > viewConfiguration.touchSlop
                        }) moved = true
                    event.changes.forEach { it.consume() }
                }
                previousCount = pressed.size
                if (pressed.isEmpty()) {
                    if (claimed) {
                        val up = event.changes.firstOrNull { it.id == down.id }
                        if (!multiple && !moved && zoom.pose.isZoomed && up != null) {
                            zoom.tap(up.uptimeMillis, up.position, viewConfiguration.doubleTapMinTimeMillis,
                                viewConfiguration.doubleTapTimeoutMillis, doubleTapSlop)
                        } else zoom.forgetTap()
                        zoom.finish()
                    }
                    break
                }
            }
        }
    } finally { zoom.clear() }
}
