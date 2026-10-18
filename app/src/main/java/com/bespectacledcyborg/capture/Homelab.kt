package com.bespectacledcyborg.capture

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

object Homelab {
    const val ORCHESTRATOR = "https://agents.example.invalid"
    const val CAPTURES = "https://dufs.example.invalid/captures/"
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

    fun upload(file: File, name: String): String {
        val url = CAPTURES + name
        val connection = open(url, "PUT", timeoutMillis = 60_000)
        try {
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(file.length())
            connection.setRequestProperty("Content-Type", if (name.endsWith(".mp4")) "video/mp4" else "image/jpeg")
            connection.outputStream.use { out -> file.inputStream().use { it.copyTo(out) } }
            expectSuccess(connection, "upload")
        } finally {
            connection.disconnect()
        }
        return url
    }

    fun post(line: String) {
        val body = Json.encodeToString(JsonObject.serializer(), buildJsonObject { put("text", JsonPrimitive(line)) })
            .toByteArray()
        val connection = open("$ORCHESTRATOR/api/sessions/$CONCIERGE/input", "POST", timeoutMillis = 15_000)
        try {
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body) }
            expectSuccess(connection, "post")
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, method: String, timeoutMillis: Int): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = minOf(timeoutMillis, 5_000)
            readTimeout = timeoutMillis
        }

    private fun expectSuccess(connection: HttpURLConnection, step: String) {
        val code = connection.responseCode
        if (code !in 200..299) {
            val detail = connection.errorStream?.bufferedReader()?.use { it.readText().take(200) }.orEmpty()
            throw IOException("$step failed: HTTP $code $detail".trim())
        }
    }
}
