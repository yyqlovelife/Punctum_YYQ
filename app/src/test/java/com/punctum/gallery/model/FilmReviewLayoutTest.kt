package com.punctum.gallery.model

import org.junit.Assert.*
import org.junit.Test

class FilmReviewLayoutTest {
    @Test fun restingProjectionCentersAcrossTheWholeImmersiveSurface() {
        for (aspect in listOf(1f, 4f / 3, 3f / 4, 3f / 2, 2f / 3, 4f, .25f)) {
            val frame = filmProjectionFrame(aspect, 360f, 431f, false, 44f)
            assertEquals(frame.top, 431f - frame.top - frame.size.height, .001f)
            assertTrue(frame.top >= 44f)
            assertEquals(aspect, frame.size.width / frame.size.height, .001f)
        }
    }

    @Test fun portraitUsesMoreHeightThanLandscapeAndThePreviousEnvelope() {
        val portrait = filmProjectionFrame(2f / 3, 360f, 431f, false, 44f)
        val landscape = filmProjectionFrame(4f / 3, 360f, 431f, false, 44f)
        assertTrue(portrait.size.height > landscape.size.height)
        assertEquals(343f, portrait.size.height, .001f)
        assertEquals(234f, landscape.size.height, .001f)
        assertEquals(FILM_INFO_SIDE_MARGIN, (360f - landscape.size.width) / 2f, .001f)
    }

    @Test fun landscapeStaysInPlaceWhenInformationAlreadyFits() {
        val rest = filmProjectionFrame(4f / 3, 360f, 431f, false, 44f)
        val info = filmProjectionFrame(4f / 3, 360f, 431f, true, 44f)
        assertEquals(rest, info)
    }

    @Test fun informationLiftIsSmallAndNeverOverlapsItsText() {
        for (aspect in listOf(1f, 2f / 3, 3f / 4, 4f / 3)) {
            val rest = filmProjectionFrame(aspect, 360f, 431f, false, 44f)
            val info = filmProjectionFrame(aspect, 360f, 431f, true, 44f)
            val lift = rest.top + rest.size.height / 2f - info.top - info.size.height / 2f
            assertTrue(lift >= -.001f && lift <= 18.001f)
            assertTrue(info.top + info.size.height <= 431f - FILM_INFO_HEIGHT - FILM_INFO_GAP + .001f)
            assertTrue(info.top >= 44f)
            assertEquals(aspect, info.size.width / info.size.height, .001f)
        }
        val compact = filmProjectionFrame(2f / 3, 320f, 120f, true, 44f)
        assertTrue(compact.top >= 0f)
        assertTrue(compact.top + compact.size.height <= 32.001f)
    }

    @Test fun centeredCaptionKeepsOriginalDateRange() {
        assertEquals("2024.05 — 2026.10", filmCompactTimeSpan("2024年5月 - 2026年10月"))
        assertEquals("2026.01", filmCompactTimeSpan("2026年1月"))
        assertEquals("2026.01 — 2026.10", filmCompactTimeSpan("2026年1-10月"))
        assertEquals("", filmCompactTimeSpan(""))
        assertEquals("2026.01 — 2026.10", filmCompactTimeSpan("2026.01 — 2026.10"))
    }

    @Test fun fiveAnchorRatiosAndPanoramasStayInsideTheSameEnvelope() {
        for (ratio in listOf(1f, 4f / 3, 3f / 4, 3f / 2, 2f / 3, 4f, .25f)) {
            val size = fitFilmImage(ratio, 320f, 250f)
            assertTrue(size.width <= 320.001f)
            assertTrue(size.height <= 250.001f)
            assertEquals(ratio, size.width / size.height, .001f)
            assertTrue(size.width >= 319.999f || size.height >= 249.999f)
        }
    }

    @Test fun missingDimensionsStillGiveAVisibleSquare() {
        assertEquals(FilmImageSize(200f, 200f), fitFilmImage(Float.NaN, 300f, 200f))
        assertEquals(FilmImageSize(200f, 200f), fitFilmImage(0f, 300f, 200f))
    }

    @Test fun onlyOuterQuartersAreTapNavigationTargets() {
        assertEquals(FilmGesture.PREVIOUS, filmEdgeTap(0f, 400f))
        assertEquals(FilmGesture.PREVIOUS, filmEdgeTap(99f, 400f))
        assertEquals(FilmGesture.NONE, filmEdgeTap(100f, 400f))
        assertEquals(FilmGesture.NONE, filmEdgeTap(200f, 400f))
        assertEquals(FilmGesture.NONE, filmEdgeTap(300f, 400f))
        assertEquals(FilmGesture.NEXT, filmEdgeTap(301f, 400f))
        assertEquals(FilmGesture.NEXT, filmEdgeTap(400f, 400f))
        assertEquals(FilmGesture.NONE, filmEdgeTap(10f, 0f))
        assertEquals(FilmGesture.NONE, filmEdgeTap(-1f, 400f))
    }

    @Test fun horizontalAndDiagonalNavigationCannotTriggerDeletion() {
        assertEquals(FilmGesture.NEXT, filmGesture(-120f, -20f, 52f))
        assertEquals(FilmGesture.PREVIOUS, filmGesture(120f, 20f, 52f))
        assertEquals(FilmGesture.NONE, filmGesture(90f, -90f, 52f))
        assertEquals(FilmGesture.NONE, filmGesture(4f, -48f, 52f))
        assertEquals(FilmGesture.NONE, filmGesture(0f, 100f, 52f))
        assertEquals(FilmGesture.DELETE, filmGesture(15f, -100f, 52f))
    }

    @Test fun deletionKeepsTheNextSlideOrFallsBackAtTheEnd() {
        assertEquals("b", filmReplacement(listOf("a", "b", "c"), "a"))
        assertEquals("c", filmReplacement(listOf("a", "b", "c"), "b"))
        assertEquals("b", filmReplacement(listOf("a", "b", "c"), "c"))
        assertNull(filmReplacement(listOf("a"), "a"))
        assertNull(filmReplacement(listOf("a"), "missing"))
    }
}
