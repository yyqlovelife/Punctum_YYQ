package com.punctum.gallery.model

import org.junit.Assert.*
import org.junit.Test

class FilmLampTimelineTest {
    private fun reference() = List(123) { index ->
        FilmLampReference(
            if (index < 27) listOf(101f - index, 96f - index, 89f - index)
            else listOf(253f, 250f, 230f),
            listOf(.1f + index * .01f, .05f, .02f),
            if (index in 27..40) listOf(250f, 252f, 255f) else listOf(18f, 32f, 38f),
        )
    }

    @Test fun unlitExposureNeverSinksOrChangesFilmBeforeTheSwitch() {
        val timeline = FilmLampTimeline(reference())
        for (ms in 0..899) assertEquals(timeline.frameAt(0f), timeline.frameAt(ms.toFloat()))
        val dark = timeline.frameAt(0f)
        assertEquals(dark.table[0], dark.table[1])
        assertEquals(dark.table[1], dark.table[2])
        assertTrue(dark.filmGain > 0f)
        assertTrue(dark.filmBias > 0f)
    }

    @Test fun manualSwitchStartsAtIgnitionWithNoDarkCountdownAndKeepsRecovery() {
        val timeline = FilmLampTimeline(reference())
        val start = FilmLampTimeline.MANUAL_ON_START_MILLIS.toFloat()
        assertEquals(timeline.frameAt(900f), timeline.frameAt(start))
        assertEquals(timeline.frameAt(1800f), timeline.frameAt(start + 900f))
        assertEquals(0f, timeline.frameAt(start).filmBias, 0f)
        assertEquals(3200, FilmLampTimeline.MANUAL_ON_DURATION_MILLIS)
    }

    @Test fun switchGoesStraightIntoTheBrightExposure() {
        val timeline = FilmLampTimeline(reference())
        val dark = timeline.frameAt(899f)
        val bright = timeline.frameAt(900f)
        assertTrue(bright.table[0] > dark.table[0] + 100f)
        assertEquals(0f, bright.filmBias, 0f)
        assertTrue(bright.wash > .9f)
        assertTrue(bright.originalMix > 0f)
    }

    @Test fun recoveryHasNeutralExposureAndOnlyTheApprovedSubtleFinalWarmth() {
        val timeline = FilmLampTimeline(reference())
        for (ms in 900..4100) {
            val frame = timeline.frameAt(ms.toFloat())
            assertTrue(frame.table.max() - frame.table.min() <= 5.001f)
            assertTrue(frame.wash in 0f..1f)
            assertTrue(frame.originalMix in 0f..1f)
        }
        val recovery = timeline.frameAt(1800f)
        assertTrue(recovery.table[0] - recovery.table[2] < 1f)
        assertTrue(timeline.frameAt(2700f).originalMix > timeline.frameAt(1800f).originalMix)
    }

    @Test fun tableSettlesClearlyBelowPeakWithoutAnotherBrightnessDipOrFlash() {
        val timeline = FilmLampTimeline(reference())
        var previous = timeline.frameAt(1100f).table[0]
        assertEquals(253f, previous, .001f)
        for (ms in 1100..3500) {
            val level = timeline.frameAt(ms.toFloat()).table[0]
            assertTrue(level <= previous + .001f)
            previous = level
        }
        assertEquals(232f, previous, .001f)
        assertTrue(timeline.frameAt(1100f).table[0] - previous >= 20f)
    }

    @Test fun blackFilmBaseSurvivesPeakAndReturnsToItsOriginalDensity() {
        val rows = reference().mapIndexed { index, row -> row.copy(
            border = if (index in 27..40) listOf(191f, 233f, 235f) else listOf(20f, 20f, 20f),
        ) }
        val timeline = FilmLampTimeline(rows)
        val dark = timeline.frameAt(0f)
        assertEquals(.55f, dark.borderGain, 0f)
        assertTrue(dark.borderLift.all { it == 0f })
        val peak = timeline.frameAt(1000f)
        for (channel in 0..2) {
            val opaqueBlack = 15f * peak.borderGain + peak.borderLift[channel]
            assertTrue(opaqueBlack > 50f && opaqueBlack < 140f)
            assertTrue(peak.table[channel] - opaqueBlack > 100f)
        }
        val final = timeline.frameAt(4100f)
        assertEquals(1f, final.borderGain, 0f)
        assertTrue(final.borderLift.all { it == 0f })
    }

    // Model the actual clipped native filter followed by source-alpha composition.
    private fun output(frame: FilmLampFrame, source: Float): Float {
        val exposed = (source * frame.filmGain + frame.filmBias).coerceIn(0f, 255f)
        return exposed * (1f - frame.originalMix) + source * frame.originalMix
    }

    @Test fun ignitionRetainsDarkStructuresWhileHighlightsTransmitStrongly() {
        val timeline = FilmLampTimeline(reference())
        for (ms in 900..4100 step 33) {
            val frame = timeline.frameAt(ms.toFloat())
            assertEquals(0f, output(frame, 0f), .001f)
            assertTrue(output(frame, 24f) < 70f)
            assertTrue(output(frame, 200f) - output(frame, 24f) > 170f)
        }
        val peak = timeline.frameAt(1000f)
        assertTrue(output(peak, 180f) > 240f)
        assertTrue(output(peak, 128f) > 230f)
        assertTrue(output(peak, 220f) > output(peak, 180f))
    }

    @Test fun photographRecoversContinuouslyWithoutSecondFlashOrFinalColorShift() {
        val timeline = FilmLampTimeline(reference())
        for (source in listOf(0f, 24f, 64f, 128f, 180f, 220f, 255f)) {
            var previous = output(timeline.frameAt(1100f), source)
            for (ms in 1100..4100 step 10) {
                val current = output(timeline.frameAt(ms.toFloat()), source)
                assertTrue(current <= previous + .001f)
                assertTrue(previous - current < 5f)
                previous = current
            }
            assertEquals(source, previous, .001f)
        }
    }

    @Test fun settledFilmRestoresOriginalColorAndTheSameNearWhiteAsTheDemo() {
        val timeline = FilmLampTimeline(reference())
        val final = timeline.frameAt(4100f)
        assertEquals(listOf(232f, 232f, 230f), final.table)
        assertEquals(1f, final.filmGain, 0f)
        assertEquals(0f, final.filmBias, 0f)
        assertEquals(1f, final.originalMix, 0f)
        assertEquals(final, timeline.frameAt(99999f))
        assertEquals(timeline.frameAt(0f), timeline.frameAt(-1f))
    }
}
