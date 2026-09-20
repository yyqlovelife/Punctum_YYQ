package com.punctum.gallery.data

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.Collections

class CoalescingCacheWriterTest {
    @Test fun coalescesLatestSnapshotWithoutBlockingCaller() {
        val complete = CountDownLatch(1)
        val caller = Thread.currentThread()
        val writes = Collections.synchronizedList(mutableListOf<String>())
        val writer = CoalescingCacheWriter<String, String>(100, { _, value ->
            assertNotEquals(caller, Thread.currentThread())
            writes.add(value)
            complete.countDown()
        })
        writer.submit("album", "old")
        writer.submit("album", "new")
        assertEquals("new", writer.latest("album"))
        assertTrue(complete.await(3, TimeUnit.SECONDS))
        assertEquals(listOf("new"), writes.toList())
    }

    @Test fun removalWinsAgainstInFlightSaveAndOtherAlbumsSurvive() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val complete = CountDownLatch(3)
        val writes = Collections.synchronizedList(mutableListOf<Pair<String, String>>())
        val writer = CoalescingCacheWriter<String, String>(10, { key, value ->
            if (value == "old") {
                started.countDown()
                assertTrue(release.await(3, TimeUnit.SECONDS))
            }
            writes.add(key to value)
            complete.countDown()
        })
        writer.submit("a", "old")
        assertTrue(started.await(3, TimeUnit.SECONDS))
        writer.submit("a", "removed")
        writer.submit("b", "keep")
        assertEquals("removed", writer.latest("a"))
        release.countDown()
        assertTrue(complete.await(3, TimeUnit.SECONDS))
        assertEquals(listOf("old", "removed"), writes.filter { it.first == "a" }.map { it.second })
        assertTrue(writes.contains("b" to "keep"))
    }
}
