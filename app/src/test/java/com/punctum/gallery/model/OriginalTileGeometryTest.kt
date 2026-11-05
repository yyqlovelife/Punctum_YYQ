package com.punctum.gallery.model

import org.junit.Assert.*
import org.junit.Test

class OriginalTileGeometryTest {
    @Test fun allEightExifOrientationsCoverTheSameEncodedImage() {
        for (exif in 1..8) {
            val orientation = OriginalOrientation(6000, 4000, exif)
            assertEquals(OriginalPixelRect(0, 0, 6000, 4000), orientation.encodedRect(
                OriginalPixelRect(0, 0, orientation.width, orientation.height)))
        }
    }

    @Test fun rotatedAndMirroredVisibleCropMapsToTheCorrectSourceRegion() {
        val rect = OriginalPixelRect(100, 200, 500, 700)
        val expected = listOf(
            OriginalPixelRect(100, 200, 500, 700), OriginalPixelRect(5500, 200, 5900, 700),
            OriginalPixelRect(5500, 3300, 5900, 3800), OriginalPixelRect(100, 3300, 500, 3800),
            OriginalPixelRect(200, 100, 700, 500), OriginalPixelRect(200, 3500, 700, 3900),
            OriginalPixelRect(5300, 3500, 5800, 3900), OriginalPixelRect(5300, 100, 5800, 500),
        )
        for (exif in 1..8) assertEquals("EXIF $exif", expected[exif - 1],
            OriginalOrientation(6000, 4000, exif).encodedRect(rect))
    }

    @Test fun sampleRetainsEnoughPixelsAndStopsAtNativeResolution() {
        assertEquals(4, originalTileSample(4.8f))
        assertEquals(2, originalTileSample(3.99f))
        assertEquals(1, originalTileSample(.2f))
        assertEquals(1, originalTileSample(Float.NaN))
    }

    @Test fun visibleWindowClipsAtPhotoEdgesWithoutLosingFractionalEdgePixels() {
        assertEquals(OriginalPixelRect(0, 10, 6000, 101),
            originalVisibleRect(-100f, 10.8f, 7000f, 100.1f, 6000, 4000))
        assertNull(originalVisibleRect(6001f, 0f, 8000f, 200f, 6000, 4000))
    }

    @Test fun tileWindowIncludesBoundedGuardRingAndVisibleTilesHavePriority() {
        val visible = OriginalPixelRect(1000, 1000, 1900, 1900)
        val tiles = originalTilesForWindow(visible, 6000, 4000, 1)
        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.first().rect.intersects(visible))
        val firstGuard = tiles.indexOfFirst { !it.rect.intersects(visible) }
        assertTrue(firstGuard > 0)
        assertTrue(tiles.drop(firstGuard).none { it.rect.intersects(visible) })
        assertEquals(25, tiles.size)
        assertTrue(tiles.all { it.rect.left >= 0 && it.rect.top >= 0 && it.rect.right <= 6000 && it.rect.bottom <= 4000 })
    }

    @Test fun largeVisibleLayerIsReservedBeforeAnyGuardTiles() {
        // A 50MP portrait at a sampling boundary can need more than the old fixed 32MB.
        val visible = OriginalPixelRect(500, 500, 4000, 6000)
        val tiles = originalTilesForWindow(visible, 6000, 9000, 1)
        val plan = originalTileRequestPlan(visible, tiles)
        assertEquals(tiles.filter { it.rect.intersects(visible) }.toSet(), plan.visibleKeys)
        assertTrue(plan.requests.containsAll(plan.visibleKeys))
        assertTrue(plan.visibleKeys.sumOf(::originalTileEstimatedBytes) <= plan.budgetBytes)
        assertTrue(plan.requests.sumOf(::originalTileEstimatedBytes) <= plan.budgetBytes)
        assertTrue(plan.requests.all { it.sample == 1 })
        assertEquals(plan.visibleKeys, plan.requests.toSet()) // oversize visible layer gets no guard
    }

    @Test fun normalVisibleLayerKeepsSomeGuardInsideTheByteBudget() {
        val visible = OriginalPixelRect(2000, 3000, 3400, 5500)
        val plan = originalTileRequestPlan(visible,
            originalTilesForWindow(visible, 6000, 9000, 1))
        assertTrue(plan.requests.size > plan.visibleKeys.size)
        assertTrue(plan.requests.sumOf(::originalTileEstimatedBytes) <= plan.budgetBytes)
        assertTrue(plan.budgetBytes <= 64L * 1024 * 1024)
    }

}
