package com.punctum.gallery.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoSortOrderTest {
    private data class Item(val id: String, val shot: Long, val saved: Long)
    private fun sorted(order: PhotoSortOrder, items: List<Item>): List<String> =
        items.sortedWith(photoTimeComparator(order, { it.shot }, { it.saved }, { it.id })).map { it.id }

    @Test fun editingOldPhotoMovesItToTopOnlyInModifiedMode() {
        val items = listOf(Item("new-shot", 300, 300), Item("edited-old-shot", 100, 500))
        assertEquals(listOf("new-shot", "edited-old-shot"), sorted(PhotoSortOrder.CAPTURE, items))
        assertEquals(listOf("edited-old-shot", "new-shot"), sorted(PhotoSortOrder.MODIFIED, items))
    }

    @Test fun exportOrderCanDifferFromOriginalCameraOrder() {
        val items = listOf(Item("older-exported-last", 100, 900), Item("newer-exported-first", 200, 800))
        assertEquals(listOf("older-exported-last", "newer-exported-first"), sorted(PhotoSortOrder.MODIFIED, items))
        assertEquals(listOf("newer-exported-first", "older-exported-last"), sorted(PhotoSortOrder.CAPTURE, items))
    }

    @Test fun equalModifiedTimesUseCaptureThenStableIdentity() {
        val items = listOf(Item("b", 100, 500), Item("a", 100, 500), Item("c", 200, 500))
        assertEquals(listOf("c", "a", "b"), sorted(PhotoSortOrder.MODIFIED, items))
        assertEquals(sorted(PhotoSortOrder.MODIFIED, items), sorted(PhotoSortOrder.MODIFIED, items.reversed()))
    }

    @Test fun missingModifiedTimeFallsBackToCaptureTime() {
        assertEquals(listOf("known", "missing", "unknown"), sorted(PhotoSortOrder.MODIFIED,
            listOf(Item("missing", 200, 0), Item("known", 100, 300), Item("unknown", 0, 0))))
    }

    @Test fun legacyOrUnknownStoredPreferenceDefaultsToCapture() {
        assertEquals(PhotoSortOrder.CAPTURE, PhotoSortOrder.from(null))
        assertEquals(PhotoSortOrder.CAPTURE, PhotoSortOrder.from(""))
        assertEquals(PhotoSortOrder.CAPTURE, PhotoSortOrder.from("future-value"))
        assertEquals(PhotoSortOrder.MODIFIED, PhotoSortOrder.from("modified"))
        assertEquals(PhotoSortOrder.CAPTURE, PhotoSortOrder.MODIFIED.next())
    }
}
