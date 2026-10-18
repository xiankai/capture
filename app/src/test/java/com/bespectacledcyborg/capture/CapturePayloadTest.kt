package com.bespectacledcyborg.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class CapturePayloadTest {
    private val time = OffsetDateTime.parse("2026-10-18T19:04:05.678+09:00")

    @Test
    fun reportLineMatchesTheSharedContract() {
        val line = CapturePayload(
            kind = CaptureKind.REPORT,
            app = "Remote Explorer",
            packageName = "com.bespectacledcyborg.remoteexplorer",
            url = null,
            file = "https://dufs.example.invalid/captures/20261018-190405-com.bespectacledcyborg.remoteexplorer.jpg",
            said = "thumbnails are blank",
            at = CaptureNames.at(time),
        ).toLine()

        assertEquals(
            "Capture: {\"kind\":\"report\",\"app\":\"Remote Explorer\",\"package\":\"com.bespectacledcyborg.remoteexplorer\"," +
                "\"url\":null,\"file\":\"https://dufs.example.invalid/captures/20261018-190405-com.bespectacledcyborg.remoteexplorer.jpg\"," +
                "\"said\":\"thumbnails are blank\",\"at\":\"2026-10-18T19:04:05+09:00\"}",
            line,
        )
    }

    @Test
    fun ideaLineCarriesNullFile() {
        val line = CapturePayload(CaptureKind.IDEA, "Chrome", "com.android.chrome", "example.com/a", null, "x", "t").toLine()

        assertEquals(
            "Capture: {\"kind\":\"idea\",\"app\":\"Chrome\",\"package\":\"com.android.chrome\",\"url\":\"example.com/a\",\"file\":null,\"said\":\"x\",\"at\":\"t\"}",
            line,
        )
    }

    @Test
    fun dictatedNewlinesStayOnOneLine() {
        val line = CapturePayload(CaptureKind.RECORD, "a", "b", null, null, "one\ntwo \"quoted\"", "t").toLine()

        assertFalse(line.contains('\n'))
        assertTrue(line.contains("\"said\":\"one\\ntwo \\\"quoted\\\"\""))
    }

    @Test
    fun fileNamesFollowTheDufsLayout() {
        assertEquals("20261018-190405-com.android.chrome.jpg", CaptureNames.fileName(time, "com.android.chrome", CaptureKind.REPORT))
        assertEquals("20261018-190405-com.android.chrome.mp4", CaptureNames.fileName(time, "com.android.chrome", CaptureKind.RECORD))
        assertNull(CaptureNames.fileName(time, "com.android.chrome", CaptureKind.IDEA))
    }
}
