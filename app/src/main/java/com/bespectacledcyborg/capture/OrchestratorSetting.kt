package com.bespectacledcyborg.capture

import android.content.Context
import androidx.core.content.edit
import java.net.URI

class OrchestratorSetting(context: Context) {
    private val prefs = context.getSharedPreferences("orchestrator", Context.MODE_PRIVATE)

    val entered: String
        get() = prefs.getString(KEY_URL, null) ?: BuildConfig.DEFAULT_ORCHESTRATOR

    val url: String?
        get() = OrchestratorUrl.parse(entered)

    fun save(raw: String): Boolean {
        val parsed = OrchestratorUrl.parse(raw) ?: return false
        prefs.edit { putString(KEY_URL, parsed) }
        return true
    }

    private companion object {
        const val KEY_URL = "url"
    }
}

object OrchestratorUrl {
    fun parse(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val webScheme = uri.scheme?.lowercase() in setOf("http", "https")
        val bareOrigin = uri.rawQuery == null && uri.rawFragment == null && uri.userInfo == null
        return trimmed.takeIf { webScheme && !uri.host.isNullOrEmpty() && bareOrigin }
    }
}
