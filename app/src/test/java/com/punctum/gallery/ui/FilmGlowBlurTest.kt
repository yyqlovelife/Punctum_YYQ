package com.punctum.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmGlowBlurTest {
    @Test fun opaqueConstantIsPreserved() {
        val pixels = IntArray(35) { 0xff654321.toInt() }
        blurFilmGlow(pixels, 7, 5, 3f)
        assertTrue(pixels.all { it == 0xff654321.toInt() })
    }

    @Test fun hiddenTransparentColorDoesNotBleed() {
        val pixels = IntArray(25) { 0x00ff0000 }
        pixels[12] = 0xff0000ff.toInt()
        blurFilmGlow(pixels, 5, 5, 1f)
        assertTrue(pixels.all { (it ushr 16 and 255) == 0 })
        assertTrue(pixels.any { (it ushr 24) > 0 })
    }

    @Test fun semitransparentConstantKeepsAlpha() {
        val pixels = IntArray(36) { 0x80808080.toInt() }
        blurFilmGlow(pixels, 6, 6, 4f)
        assertTrue(pixels.all { (it ushr 24) == 128 })
        assertTrue(pixels.all { (it and 255) == 128 })
    }

    @Test fun degenerateShapesAreSafe() {
        val pixels = intArrayOf(0xff334455.toInt())
        blurFilmGlow(pixels, 1, 1, 8f)
        assertEquals(0xff334455.toInt(), pixels[0])
    }
}
