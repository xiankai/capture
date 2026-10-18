package com.bespectacledcyborg.capture

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime
import java.util.Base64

class CapturePayloadTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val time = OffsetDateTime.parse("2026-10-18T19:04:05.678+09:00")

    @Test
    fun reportLineMatchesTheSharedContract() {
        val line = CapturePayload(
            kind = CaptureKind.REPORT,
            app = "Remote Explorer",
            packageName = "com.bespectacledcyborg.remoteexplorer",
            url = null,
            said = "thumbnails are blank",
            at = CaptureNames.at(time),
        ).toLine()

        assertEquals(
            "Capture: {\"kind\":\"report\",\"app\":\"Remote Explorer\",\"package\":\"com.bespectacledcyborg.remoteexplorer\"," +
                "\"url\":null,\"said\":\"thumbnails are blank\",\"at\":\"2026-10-18T19:04:05+09:00\"}",
            line,
        )
    }

    @Test
    fun ideaLineCarriesTheUrl() {
        val line = CapturePayload(CaptureKind.IDEA, "Chrome", "com.android.chrome", "example.com/a", "x", "t").toLine()

        assertEquals(
            "Capture: {\"kind\":\"idea\",\"app\":\"Chrome\",\"package\":\"com.android.chrome\",\"url\":\"example.com/a\",\"said\":\"x\",\"at\":\"t\"}",
            line,
        )
    }

    @Test
    fun dictatedNewlinesStayOnOneLine() {
        val line = CapturePayload(CaptureKind.RECORD, "a", "b", null, "one\ntwo \"quoted\"", "t").toLine()

        assertFalse(line.contains('\n'))
        assertTrue(line.contains("\"said\":\"one\\ntwo \\\"quoted\\\"\""))
    }

    @Test
    fun fileNamesFollowTheContract() {
        assertEquals("20261018-190405-com.android.chrome.jpg", CaptureNames.fileName(time, "com.android.chrome", CaptureKind.REPORT))
        assertEquals("20261018-190405-com.android.chrome.mp4", CaptureNames.fileName(time, "com.android.chrome", CaptureKind.RECORD))
        assertNull(CaptureNames.fileName(time, "com.android.chrome", CaptureKind.IDEA))
    }

    @Test
    fun inputCarriesTheFileAsBase64() {
        val bytes = ByteArray(70_000) { (it % 251).toByte() }
        val file = temp.newFile("20261018-190405-com.android.chrome.jpg").apply { writeBytes(bytes) }
        val body = ByteArrayOutputStream().also { Homelab.writeInput(it, "Capture: {\"said\":\"a \\\"b\\\"\"}", file) }

        val json = Json.parseToJsonElement(body.toString()).jsonObject
        assertEquals("Capture: {\"said\":\"a \\\"b\\\"\"}", json.getValue("text").jsonPrimitive.content)
        val sent = json.getValue("file").jsonObject
        assertEquals(file.name, sent.getValue("name").jsonPrimitive.content)
        assertArrayEquals(bytes, Base64.getDecoder().decode(sent.getValue("data").jsonPrimitive.content))
    }

    @Test
    fun ideaInputHasNoFile() {
        val body = ByteArrayOutputStream().also { Homelab.writeInput(it, "Capture: {}", null) }

        assertEquals("{\"text\":\"Capture: {}\"}", body.toString())
    }
}
