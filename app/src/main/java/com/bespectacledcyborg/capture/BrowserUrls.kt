package com.bespectacledcyborg.capture

object BrowserUrls {
    private val addressBarIds = mapOf(
        "com.android.chrome" to "com.android.chrome:id/url_bar",
        "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
    )

    fun addressBarId(packageName: String): String? = addressBarIds[packageName]

    fun clean(text: CharSequence?): String? = text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}
