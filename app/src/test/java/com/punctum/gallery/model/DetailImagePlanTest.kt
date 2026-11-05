package com.punctum.gallery.model

import org.junit.Assert.*
import org.junit.Test

class DetailImagePlanTest {
    @Test fun portraitBaseCoversPhysicalScreenWidthOnBothPhones() {
        val nobel = detailDecodeSize(4000, 6000, 1080, 2412)
        val personal = detailDecodeSize(4000, 6000, 1440, 3216)
        assertTrue(nobel.width >= 1080)
        assertTrue(personal.width >= 1440)
        assertTrue(personal.height > 1800)
        assertEquals(2f / 3f, personal.width.toFloat() / personal.height, .001f)
    }

    @Test fun smallOriginalKeepsItsRealPixelsWithoutUpscaling() {
        assertEquals(DetailDecodeSize(93, 140), detailDecodeSize(93, 140, 1440, 3216))
    }

    @Test fun extremePortraitRemainsMemoryBoundedForRegionLayerToComplete() {
        val size = detailDecodeSize(4000, 40000, 1440, 3216)
        assertTrue(size.width.toLong() * size.height <= 4_510_000)
        assertEquals(.1f, size.width.toFloat() / size.height, .001f)
    }

    @Test fun warmWindowIncludesFivePhotosEachWayInNearFirstOrder() {
        assertEquals(listOf(10, 11, 9, 12, 8, 13, 7, 14, 6, 15, 5), detailWarmIndices(30, 10))
        assertEquals(listOf(0, 1, 2, 3, 4, 5), detailWarmIndices(30, 0))
        assertEquals(emptyList<Int>(), detailWarmIndices(0, 0))
        assertEquals(emptyList<Int>(), detailWarmIndices(4, 5))
    }
}
