package com.bespectacledcyborg.capture

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CaptureKind(val extension: String?) {
    @SerialName("idea") IDEA(null),
    @SerialName("report") REPORT("jpg"),
    @SerialName("record") RECORD("mp4"),
}
