package com.punctum.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OrderedPhotoMergeTest {
    private data class Item(val id: String, val taken: Long, val width: Int = 100)

    @Test fun editedCaptureTimeAndFreshOrderWinEvenWhenDimensionsStayEqual() {
        val old = listOf(Item("a", 300), Item("b", 200), Item("c", 100))
        val fresh = listOf(Item("c", 400), Item("a", 300), Item("b", 200))
        val merged = mergeOrderedItems(old, fresh) { it.id }
        assertEquals(fresh, merged)
        assertSame(fresh[0], merged[0])
        assertSame(old[0], merged[1])
        assertEquals(fresh, mergeOrderedItems(merged, fresh) { it.id })
    }
}
