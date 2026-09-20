package com.punctum.gallery

import org.junit.Assert.*
import org.junit.Test

class GalleryReturnPositionTest {
    private val ids = (0..99).map { "photo-$it" }

    @Test fun browsingReturnsToLastViewedIdentity() {
        val id = galleryReturnPhoto(ids, "photo-71", 0)
        assertEquals("photo-71", id)
        assertEquals(36, galleryReturnRow(ids, id))
    }
    @Test fun deletionAndCancellationRecomputeRowsForSamePhoto() {
        val id = galleryReturnPhoto(ids, "photo-71", 71, ids.take(31).toSet())
        assertEquals(36, galleryReturnRow(ids, id))
        assertEquals(21, galleryReturnRow(ids.drop(31), id))
        assertEquals(36, galleryReturnRow(ids, id))
    }
    @Test fun deletedCurrentPhotoUsesNextSurvivor() {
        assertEquals("photo-42", galleryReturnPhoto(ids, "photo-40", 40, setOf("photo-40", "photo-41")))
    }
    @Test fun deletedLastPhotoUsesPreviousSurvivor() {
        assertEquals("photo-97", galleryReturnPhoto(ids, "photo-99", 99, setOf("photo-98", "photo-99")))
    }
    @Test fun emptyAndFullyDeletedAlbumsHaveNoAnchor() {
        assertNull(galleryReturnPhoto(emptyList(), "photo-0", 0))
        assertNull(galleryReturnPhoto(ids, "photo-99", 99, ids.toSet()))
        assertNull(galleryReturnRow(emptyList(), "photo-0"))
    }
    @Test fun twoPhotoRowsIncludeHeader() {
        assertEquals(1, galleryReturnRow(ids, "photo-0"))
        assertEquals(1, galleryReturnRow(ids, "photo-1"))
        assertEquals(2, galleryReturnRow(ids, "photo-2"))
    }
    @Test fun returnCentersMeasuredRowWithinViewport() {
        assertEquals(-350f, galleryReturnCenterDelta(0, 300, 0, 1000), 0.01f)
        assertEquals(0f, galleryReturnCenterDelta(350, 300, 0, 1000), 0.01f)
        assertEquals(250f, galleryReturnCenterDelta(600, 300, 0, 1000), 0.01f)
    }
    @Test fun returnCenterRespectsInsetsAndTallRows() {
        assertEquals(0f, galleryReturnCenterDelta(300, 300, -100, 1000), 0.01f)
        assertEquals(100f, galleryReturnCenterDelta(0, 1200, 0, 1000), 0.01f)
    }
    @Test fun returnWithinEntryViewportDoesNotMoveList() {
        val visible = (20..27).map { "photo-$it" }.toSet()
        assertFalse(shouldCenterGalleryReturn("photo-20", visible))
        assertFalse(shouldCenterGalleryReturn("photo-25", visible))
        assertFalse(shouldCenterGalleryReturn("photo-27", visible))
        assertTrue(shouldCenterGalleryReturn("photo-19", visible))
        assertTrue(shouldCenterGalleryReturn("photo-28", visible))
        assertFalse(shouldCenterGalleryReturn(null, visible))
    }
    @Test fun returnPolicyUsesIdentityAfterDeletionAndNewEntrySnapshot() {
        val visible = setOf("photo-20", "photo-21")
        val remaining = ids.drop(10)
        assertEquals(6, galleryReturnRow(remaining, "photo-20"))
        assertFalse(shouldCenterGalleryReturn("photo-20", visible))
        assertTrue(shouldCenterGalleryReturn("photo-20", setOf("photo-40", "photo-41")))
    }
}
