package com.bespectacledcyborg.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoSpecTest {
    @Test
    fun scalesTheTabletPanelDownAndKeepsTheRefreshRate() {
        val spec = RecordService.VideoSpec.fit(2560, 1600, 120) { _, _, _ -> true }

        assertEquals(1920, spec.width)
        assertEquals(1200, spec.height)
        assertEquals(120, spec.frameRate)
    }

    @Test
    fun fallsBackTo60WhenTheEncoderCannotKeepUp() {
        val spec = RecordService.VideoSpec.fit(1600, 2560, 120) { _, _, rate -> rate <= 60 }

        assertEquals(1200, spec.width)
        assertEquals(1920, spec.height)
        assertEquals(60, spec.frameRate)
    }

    @Test
    fun keepsDimensionsEven() {
        val spec = RecordService.VideoSpec.fit(1081, 2401, 60) { _, _, _ -> true }

        assertEquals(0, spec.width % 2)
        assertEquals(0, spec.height % 2)
    }
}
