package com.punctum.gallery.data

import org.junit.Assert.*
import org.junit.Test

class PreviewGeometryTest {
    @Test fun rejectsObservedFourByThreePreviewOfThreeByTwoPhoto() {
        assertTrue(previewAspectMismatch(160, 120, 5952, 3968))
    }
    @Test fun allowsRealProviderRoundingWithoutReadingOriginal() {
        assertFalse(previewAspectMismatch(637, 424, 7008, 4671))
        assertFalse(previewAspectMismatch(424, 637, 4672, 7008))
        assertFalse(previewAspectMismatch(256, 170, 4080, 2720))
    }
    @Test fun detectsOrientationAndSquareCropMismatch() {
        assertTrue(previewAspectMismatch(640, 480, 3000, 4000))
        assertTrue(previewAspectMismatch(256, 256, 6000, 4000))
    }
    @Test fun unknownDimensionsKeepFastProviderPreview() {
        assertFalse(previewAspectMismatch(160, 120, 0, 0))
    }
}
