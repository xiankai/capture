package com.bespectacledcyborg.capture

object Transcript {
    fun append(existing: CharSequence, segment: String): String {
        val words = segment.trim()
        val kept = existing.trimEnd()
        return when {
            words.isEmpty() -> kept.toString()
            kept.isEmpty() -> words
            else -> "$kept $words"
        }
    }
}
