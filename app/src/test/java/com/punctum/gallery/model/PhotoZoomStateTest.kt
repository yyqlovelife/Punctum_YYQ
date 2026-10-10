package com.punctum.gallery.model

import org.junit.Assert.*
import org.junit.Test

class PhotoZoomStateTest {
    private fun PhotoZoomState.move(factor: Float = 1f, focalX: Float = 0f, focalY: Float = 0f,
                                   panX: Float = 0f, panY: Float = 0f) =
        transform(factor, focalX, focalY, panX, panY, 1000f, 600f)

    @Test fun anotherPinchContinuesFromRetainedScaleAndFocalPixel() {
        val first = PhotoZoomState().move(2f, 100f, 50f)
        val next = first.move(1.5f, 100f, 50f)
        assertEquals(3f, next.scale, 0.001f)
        assertEquals(-200f, next.offsetX, 0.001f)
        assertEquals(-100f, next.offsetY, 0.001f)
        assertTrue(next.isZoomed)
    }

    @Test fun retainedPoseAcceptsSingleFingerPan() {
        val pose = PhotoZoomState().move(2f).move(panX = 75f, panY = -40f)
        assertEquals(2f, pose.scale, 0f)
        assertEquals(75f, pose.offsetX, 0f)
        assertEquals(-40f, pose.offsetY, 0f)
    }

    @Test fun panCannotMovePhotoBeyondItsScaledEdges() {
        val pose = PhotoZoomState().move(2f).move(panX = 9000f, panY = -9000f)
        assertEquals(500f, pose.offsetX, 0f)
        assertEquals(-300f, pose.offsetY, 0f)
    }

    @Test fun scaleCapsAtFiveAndUsesActualScaleForAnchor() {
        val pose = PhotoZoomState().move(100f, 100f, 50f)
        assertEquals(5f, pose.scale, 0f)
        assertEquals(-400f, pose.offsetX, 0f)
        assertEquals(-200f, pose.offsetY, 0f)
    }

    @Test fun pinchingBackToOneClearsOffsetAndUnlocks() {
        val pose = PhotoZoomState().move(3f).move(panX = 100f, panY = 80f).move(0.1f)
        assertEquals(PhotoZoomState(), pose)
        assertFalse(pose.isZoomed)
    }

    @Test fun invalidInputDoesNotCorruptRetainedPose() {
        val pose = PhotoZoomState().move(2f)
        assertEquals(pose, pose.move(Float.NaN))
        assertEquals(pose, pose.move(panX = Float.POSITIVE_INFINITY))
        assertEquals(pose, pose.transform(2f, 0f, 0f, 0f, 0f, 0f, 600f))
    }
}
