package com.bespectacledcyborg.capture

data class AppWindow(
    val packageName: String,
    val focused: Boolean,
    val active: Boolean,
    val layer: Int,
)

object ForegroundWindow {
    private const val SYSTEM_UI = "com.android.systemui"

    fun pick(appWindows: List<AppWindow>, ownPackage: String): AppWindow? =
        appWindows
            .filter { it.packageName != ownPackage }
            .sortedWith(
                compareByDescending<AppWindow> { it.focused }
                    .thenByDescending { it.active }
                    .thenByDescending { it.packageName != SYSTEM_UI }
                    .thenByDescending { it.layer },
            )
            .firstOrNull()
}
