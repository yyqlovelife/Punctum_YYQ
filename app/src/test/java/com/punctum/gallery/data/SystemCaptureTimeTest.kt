package com.punctum.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemCaptureTimeTest {
    @Test fun systemAlbumDateOverridesUnchangedFileCacheAndExif() {
        val oldExifOrCache = 1_700_000_000_000L
        val editedInSystemGallery = 1_730_000_000_000L
        assertEquals(
            editedInSystemGallery,
            authoritativeSystemCaptureMillis(editedInSystemGallery, oldExifOrCache),
        )
    }

    @Test fun missingSystemDateKeepsExistingCaptureTime() {
        val original = 1_700_000_000_000L
        assertEquals(original, authoritativeSystemCaptureMillis(null, original))
        assertEquals(original, authoritativeSystemCaptureMillis(0L, original))
    }
}
