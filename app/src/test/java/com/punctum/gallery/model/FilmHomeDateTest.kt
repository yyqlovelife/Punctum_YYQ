package com.punctum.gallery.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FilmHomeDateTest {
    @Test fun overviewDateFormsUseFullPaddedRange() {
        assertEquals("2026.01-2026.10", filmHomeDate("2026年1-10月"))
        assertEquals("2023.09-2026.10", filmHomeDate("2023年9月 - 2026年10月"))
        assertEquals("2026.01", filmHomeDate("2026年1月"))
        assertEquals("2026.01-2026.10", filmHomeDate("2026.01 — 2026.10"))
    }
    @Test fun unknownDateDoesNotFabricateTime() {
        assertEquals("时间未知", filmHomeDate(""))
        assertEquals("时间未知", filmHomeDate("时间未知"))
    }
}
