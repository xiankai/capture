package com.bespectacledcyborg.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class CaptureAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    fun foregroundApp(): AppContext {
        val root = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .firstNotNullOfOrNull { window -> window.root?.takeIf { it.packageName?.toString() != packageName } }
            ?: return AppContext.UNKNOWN
        val pkg = root.packageName.toString()
        return AppContext(label(pkg), pkg, addressBarUrl(root, pkg))
    }

    suspend fun screenshot(): Bitmap? = suspendCancellableCoroutine { continuation ->
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val bitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                    buffer.close()
                    continuation.resume(bitmap)
                }

                override fun onFailure(errorCode: Int) {
                    continuation.resume(null)
                }
            },
        )
    }

    private fun label(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun addressBarUrl(root: AccessibilityNodeInfo, pkg: String): String? {
        val id = BrowserUrls.addressBarId(pkg) ?: return null
        return runCatching {
            root.findAccessibilityNodeInfosByViewId(id).firstNotNullOfOrNull { BrowserUrls.clean(it.text) }
        }.getOrNull()
    }

    companion object {
        @Volatile
        var instance: CaptureAccessibilityService? = null
            private set
    }
}
