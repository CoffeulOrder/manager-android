package com.ormo.manager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoRulesTest {

    @Test
    fun albumPhoto_needsSupportedTypeAndTenMegabytesOrLess() {
        assertNull(checkPhoto("image/jpeg", MAX_PHOTO_BYTES))
        assertNull(checkPhoto("image/HEIC", null))
        assertEquals(PhotoProblem.TOO_BIG, checkPhoto("image/png", MAX_PHOTO_BYTES + 1))
        assertEquals(PhotoProblem.UNSUPPORTED, checkPhoto("image/gif", 10))
        assertEquals(PhotoProblem.UNSUPPORTED, checkPhoto(null, 10))
    }

    @Test
    fun cropWindow_startsInTheMiddle_andNeverLeavesThePhoto() {
        val window = CropWindow(width = 400, height = 200)
        assertEquals(200f, window.side)
        assertEquals(100f, window.left)

        val dragged = window.pan(1_000f, -1_000f)
        assertEquals(200f, dragged.left)
        assertEquals(0f, dragged.top)

        val zoomed = dragged.zoomBy(2f)
        assertEquals(100f, zoomed.side)
        assertEquals(MAX_PHOTO_ZOOM, zoomed.zoomBy(100f).zoom)

        val back = zoomed.pan(1_000f, 1_000f).zoomBy(0.01f)
        assertEquals(1f, back.zoom)
        assertEquals(200f, back.left)
        assertEquals(0f, back.top)
    }
}
