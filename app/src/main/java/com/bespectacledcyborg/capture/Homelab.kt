package com.bespectacledcyborg.capture

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64

object Homelab {
    const val ORCHESTRATOR = "https://agents.example.invalid"
    const val CONCIERGE = "concierge"

    fun isReachable(): Boolean = runCatching {
        open("$ORCHESTRATOR/api/health", "GET", timeoutMillis = 4_000).run {
            try {
                responseCode == 200
            } finally {
                disconnect()
            }
        }
    }.getOrDefault(false)

    fun post(line: String, file: File?) {
        val connection = open("$ORCHESTRATOR/api/sessions/$CONCIERGE/input", "POST", timeoutMillis = 120_000)
        try {
            connection.doOutput = true
            connection.setChunkedStreamingMode(64 * 1024)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.buffered().use { writeInput(it, line, file) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText().take(200) }.orEmpty()
                throw IOException(failure(code, detail))
            }
        } finally {
            connection.disconnect()
        }
    }

    fun writeInput(out: OutputStream, line: String, file: File?) {
        out.write("{\"text\":${quote(line)}".toByteArray())
        if (file != null) {
            out.write(",\"file\":{\"name\":${quote(file.name)},\"data\":\"".toByteArray())
            val encoder = Base64.getEncoder().wrap(NonClosing(out))
            file.inputStream().use { it.copyTo(encoder) }
            encoder.close()
            out.write("\"}".toByteArray())
        }
        out.write("}".toByteArray())
    }

    private fun failure(code: Int, detail: String): String = when (code) {
        429 -> "The concierge's queue is full. Retry in a bit."
        413 -> "The file is over the 50 MB limit."
        else -> "Send failed: HTTP $code $detail".trim()
    }

    private fun quote(value: String): String = Json.encodeToString(String.serializer(), value)

    private fun open(url: String, method: String, timeoutMillis: Int): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = minOf(timeoutMillis, 5_000)
            readTimeout = timeoutMillis
        }

    private class NonClosing(private val out: OutputStream) : OutputStream() {
        override fun write(b: Int) = out.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() = Unit
    }
}
