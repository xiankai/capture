package com.bespectacledcyborg.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptTest {
    @Test
    fun startsWithTheFirstSegment() {
        assertEquals("open the settings", Transcript.append("", " open the settings "))
    }

    @Test
    fun joinsSegmentsAcrossAPauseWithOneSpace() {
        val first = Transcript.append("", "the map freezes")
        val second = Transcript.append(first, "when I zoom out")

        assertEquals("the map freezes when I zoom out", second)
    }

    @Test
    fun keepsWhatWasTypedAndAppendsAfterIt() {
        assertEquals("Edited by hand. and more", Transcript.append("Edited by hand.  \n", "and more"))
    }

    @Test
    fun ignoresAnEmptySegment() {
        assertEquals("unchanged", Transcript.append("unchanged", "  "))
    }
}
