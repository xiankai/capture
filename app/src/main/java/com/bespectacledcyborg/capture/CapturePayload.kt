package com.bespectacledcyborg.capture

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class CapturePayload(
    val kind: CaptureKind,
    val app: String,
    @SerialName("package") val packageName: String,
    val url: String?,
    val file: String?,
    val said: String,
    val at: String,
) {
    fun toLine(): String = "Capture: " + Json.encodeToString(serializer(), this)
}

object CaptureNames {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val unsafe = Regex("[^A-Za-z0-9._-]")

    fun at(time: OffsetDateTime): String = time.withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    fun fileName(time: OffsetDateTime, packageName: String, kind: CaptureKind): String? =
        kind.extension?.let { "${time.format(stamp)}-${packageName.replace(unsafe, "_")}.$it" }
}
