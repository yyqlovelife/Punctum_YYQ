package com.punctum.gallery.model

import org.junit.Assert.*
import org.junit.Test

class Film120GeometryTest {
    @Test fun referenceMarginsAndPrintsStayOutsideTheSquareOpening() {
        val g = Film120Geometry
        assertTrue(g.PHOTO_LEFT > 0f && g.PHOTO_TOP > g.DIGIT_HEIGHT)
        assertTrue(g.PHOTO_LEFT + g.PHOTO_SIDE < g.WIDTH)
        assertTrue(g.PHOTO_TOP + g.PHOTO_SIDE <= g.NUMBER_TOP + 1f)
        assertTrue(g.NUMBER_TOP + g.DIGIT_HEIGHT <= g.HEIGHT + 1f)
        assertEquals(51f / 1420f, g.PHOTO_LEFT / g.PHOTO_SIDE, .000001f)
        assertEquals(64f / 1420f, g.PHOTO_TOP / g.PHOTO_SIDE, .000001f)
    }

    @Test fun fullFrameFitsDifferentCellsWithoutDistortingTheReference() {
        for ((width, height) in listOf(105f to 91f, 90f to 110f, 120f to 100f, 60f to 50f)) {
            val size = Film120Geometry.size(width, height)
            assertTrue(size.width <= width && size.height <= height)
            assertEquals(1519f / 1538f, size.width / size.height, .000001f)
        }
    }

    @Test fun portraitLandscapeAndOddPixelsAreCroppedAtTheCenter() {
        for ((width, height) in listOf(4000 to 3000, 3000 to 4000, 3001 to 4000, 1080 to 1080, 1 to 9)) {
            val c = Film120Geometry.crop(width, height)
            assertEquals(minOf(width, height), c.side)
            assertTrue(kotlin.math.abs(c.left * 2 + c.side - width) <= 1)
            assertTrue(kotlin.math.abs(c.top * 2 + c.side - height) <= 1)
        }
    }

    @Test fun randomStockCodeRemainsStablePerPhotoAcrossRedrawsAndSorting() {
        val ids = (1..50).map { "content://media/external/images/media/$it" }
        val before = ids.associateWith(Film120Geometry::emulsionCode)
        val after = ids.reversed().associateWith(Film120Geometry::emulsionCode)
        assertEquals(before, after)
        assertTrue(before.values.all { it.length == 2 && it.toInt() in 10..99 })
        assertTrue(before.values.toSet().size > 10)
    }
}
