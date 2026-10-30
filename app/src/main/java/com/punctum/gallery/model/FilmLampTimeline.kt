package com.punctum.gallery.model

/** Reference timing/board measurements. Photograph exposure is a separate transmission approximation. */
internal data class FilmLampReference(val table: List<Float>, val gain: List<Float>, val bias: List<Float>, val border: List<Float> = listOf(20f, 20f, 20f))

internal data class FilmLampFrame(
    val table: List<Float>,
    val filmGain: Float,
    val filmBias: Float,
    val wash: Float,
    val originalMix: Float,
    val borderGain: Float,
    val borderLift: List<Float>,
) {
    fun boardGain(channel: Int): Float = (1f - wash) * table[channel] / 255f
    fun boardBias(channel: Int): Float = wash * table[channel]
}

internal class FilmLampTimeline(reference: List<FilmLampReference>) {
    companion object {
        const val DURATION_MILLIS = 4100
        const val SWITCH_MILLIS = 900L
        const val MANUAL_ON_START_MILLIS = SWITCH_MILLIS.toInt()
        const val MANUAL_ON_DURATION_MILLIS = DURATION_MILLIS - MANUAL_ON_START_MILLIS
        private val FINAL_WHITE = listOf(232f, 232f, 230f)
    }

    init { require(reference.isNotEmpty()) }

    // Precompute once; animation draws only select an immutable frame and cached filters.
    private val frames = reference.mapIndexed { index, _ ->
        val row = reference[if (index < 27) 0 else index]
        val seconds = index / 30f
        val settled = ((seconds - 2.15f) / 1.35f).coerceIn(0f, 1f)
        val luminance = row.table[0] * .2126f + row.table[1] * .7152f + row.table[2] * .0722f
        val gain = row.gain.average().toFloat()
        val bias = row.bias.average().toFloat()
        val progress = ((seconds - 1.10f) / 2.40f).coerceIn(0f, 1f)
        val tableRecovery = progress * progress * (3f - 2f * progress)
        FilmLampFrame(
            table = if (index < 27) List(3) { luminance }
                else FINAL_WHITE.map { 253f * (1f - tableRecovery) + it * tableRecovery },
            // Approximate linear-light exposure in encoded RGB (3.0 gain ~= +3.5 stops).
            // No additive white after ignition: dense shadows must continue blocking light.
            filmGain = if (index < 27) gain else 1f + 2f * (1f - tableRecovery),
            filmBias = if (index < 27) bias else 0f,
            wash = ((bias - 150f) / 103f).coerceIn(0f, 1f),
            // Small post-clipping original contribution retains highlight structure as well.
            originalMix = if (index < 27) 0f else .08f + .92f * tableRecovery,
            borderGain = if (index < 27) .55f else 1f,
            // Measured opaque leader exposure: compress its peak to keep the black base visible.
            borderLift = row.border.mapIndexed { channel, value ->
                (value - reference.last().border[channel]).coerceAtLeast(0f) * .48f * (1f - settled)
            },
        )
    }

    fun frameAt(milliseconds: Float): FilmLampFrame = frames[
        (milliseconds.coerceAtLeast(0f) * 30f / 1000f + .001f).toInt().coerceAtMost(frames.lastIndex)
    ]
}
