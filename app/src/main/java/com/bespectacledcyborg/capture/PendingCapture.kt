package com.bespectacledcyborg.capture

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.OffsetDateTime

@Serializable
data class PendingCapture(
    val id: String,
    val kind: CaptureKind,
    val app: String,
    val packageName: String,
    val url: String?,
    val at: String,
    val fileName: String?,
    val said: String = "",
    val ready: Boolean = false,
) {
    fun payload(): CapturePayload = CapturePayload(kind, app, packageName, url, said, at)

    companion object {
        fun start(kind: CaptureKind, context: AppContext, time: OffsetDateTime = OffsetDateTime.now()): PendingCapture {
            val at = CaptureNames.at(time)
            return PendingCapture(
                id = time.toInstant().toEpochMilli().toString(),
                kind = kind,
                app = context.label,
                packageName = context.packageName,
                url = context.url,
                at = at,
                fileName = CaptureNames.fileName(time, context.packageName, kind),
            )
        }
    }
}

data class AppContext(val label: String, val packageName: String, val url: String?) {
    companion object {
        val UNKNOWN = AppContext("unknown", "unknown", null)
    }
}

class CaptureStore(context: Context) {
    private val dir = File(context.filesDir, "pending").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    fun mediaFile(capture: PendingCapture): File? = capture.fileName?.let { File(dir, it) }

    fun save(capture: PendingCapture) {
        val target = File(dir, "${capture.id}.json")
        val temp = File(dir, "${capture.id}.json.tmp")
        temp.writeText(json.encodeToString(PendingCapture.serializer(), capture))
        temp.renameTo(target)
    }

    fun load(id: String): PendingCapture? =
        File(dir, "$id.json").takeIf { it.exists() }?.let { decode(it) }

    fun ready(): List<PendingCapture> =
        dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .mapNotNull { decode(it) }
            .filter { it.ready }
            .sortedBy { it.id }

    fun pruneAbandoned(now: Long = System.currentTimeMillis()) {
        dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .mapNotNull { decode(it) }
            .filter { !it.ready && now - (it.id.toLongOrNull() ?: now) > ABANDONED_AFTER_MILLIS }
            .forEach(::delete)
    }

    fun delete(capture: PendingCapture) {
        mediaFile(capture)?.delete()
        File(dir, "${capture.id}.json").delete()
    }

    private fun decode(file: File): PendingCapture? =
        runCatching { json.decodeFromString(PendingCapture.serializer(), file.readText()) }.getOrNull()

    private companion object {
        const val ABANDONED_AFTER_MILLIS = 24 * 60 * 60 * 1000L
    }
}
