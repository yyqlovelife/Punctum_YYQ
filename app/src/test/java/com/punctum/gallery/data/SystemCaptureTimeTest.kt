package com.punctum.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemCaptureTimeTest {
    private val shot = 1_700_000_000_000L
    private val export = 1_730_000_000_000L

    @Test fun exporterTimestampDoesNotReplaceEmbeddedCaptureTime() {
        assertEquals(shot, resolveCaptureMillis(export, shot, export))
        assertEquals(shot, resolveCaptureMillis(export + 30_000L, shot, export))
        assertEquals(
            shot,
            resolveCaptureMillis(export, shot, shot),
        )
    }

    @Test fun originalCameraTimeTakesPriorityOverSystemGalleryDates() {
        val edited = 1_710_000_000_000L
        assertEquals(shot, resolveCaptureMillis(edited, shot, export))
    }

    @Test fun missingExifFallsBackToMediaStoreThenFileTime() {
        assertEquals(export, resolveCaptureMillis(export, null, export))
        assertEquals(shot, resolveCaptureMillis(null, shot, export))
        assertEquals(export, resolveCaptureMillis(null, null, export))
    }

    @Test fun cachedExifContinuesToCorrectNewMediaStoreRead() {
        assertEquals(shot, resolveCaptureMillis(export, shot, export, export))
    }

    @Test fun cameraExportMismatchOutsideOldFiveMinuteWindowUsesOriginal() {
        // Real-device Phocus/DJI imports include offsets from 15 minutes to 14 hours.
        for (offset in listOf(900_000L, 6_300_000L, 50_400_000L)) {
            assertEquals(shot, resolveCaptureMillis(shot + offset, shot, shot + offset + 600_000L))
        }
    }

    @Test fun missingMediaStoreDateStillUsesDngCaptureTime() {
        assertEquals(shot, resolveCaptureMillis(0L, shot, export))
    }
}
