package com.punctum.gallery.ui

import org.junit.Assert.*
import org.junit.Test
import com.punctum.gallery.model.*

class SwipeDeleteMotionTest {
    @Test fun directionLockRejectsJitterDownwardAndDiagonalDeletion() {
        assertEquals(FilmProjectionAxis.UNDECIDED, filmProjectionAxis(7f, -7f, 10f))
        assertEquals(FilmProjectionAxis.UNDECIDED, filmProjectionAxis(90f, -90f, 10f))
        assertEquals(FilmProjectionAxis.HORIZONTAL, filmProjectionAxis(-90f, -20f, 10f))
        assertEquals(FilmProjectionAxis.HORIZONTAL, filmProjectionAxis(90f, 20f, 10f))
        assertEquals(FilmProjectionAxis.UPWARD, filmProjectionAxis(10f, -90f, 10f))
        assertEquals(FilmProjectionAxis.IGNORED, filmProjectionAxis(10f, 90f, 10f))
    }
    @Test fun filmAndDetailShareTheSameArmingDistanceAndOverdragResistance() {
        assertEquals(0f, mapDeleteDragProgress(-1f), 0f)
        assertEquals(DELETE_ARM_PROGRESS, mapDeleteDragProgress(.72f), .0001f)
        assertEquals(100.8f, 140f * DELETE_ARM_PROGRESS, .001f)
        assertEquals(1f, mapDeleteDragProgress(1f), 0f)
        assertEquals(1.07f, mapDeleteDragProgress(1.5f), .001f)
        assertEquals(DELETE_DRAG_MAX_PROGRESS, mapDeleteDragProgress(100f), 0f)
    }
    @Test fun replacementRevealAndTargetPulseCompleteWithoutAJump() {
        val start = deleteBackgroundReveal(.8f, 0f, .8f)
        assertEquals(start, deleteBackgroundReveal(.8f, .000001f, .8f), .0001f)
        var last = start
        for (i in 1..100) {
            val next = deleteBackgroundReveal(.8f, i / 100f, .8f)
            assertTrue(next >= last)
            last = next
        }
        assertEquals(1f, last, 0f)
        assertEquals(1f, deleteButtonPulse(0f), 0f)
        assertEquals(1f, deleteButtonPulse(1f), .0001f)
        assertTrue(deleteButtonPulse(.9f) > 1f)
    }
}
