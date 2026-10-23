package com.bespectacledcyborg.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class CaptureAccessibilityService : AccessibilityService() {
    private val scope = MainScope()
    private var reporting: Job? = null
    private var bubble: Bubble? = null

    override fun onServiceConnected() {
        instance = this
        bubble = Bubble(this, onTap = ::onBubbleTap, onMenu = ::onBubbleMenu)
        if (BubblePrefs(this).wanted) bubble?.show()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        bubble?.remove()
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    fun showBubble() {
        BubblePrefs(this).wanted = true
        bubble?.show()
    }

    fun refreshBubble() {
        bubble?.refresh()
    }

    private fun onBubbleTap() {
        if (RecordService.current != null) {
            startActivity(CaptureActivity.launchIntent(this, CaptureKind.RECORD))
            return
        }
        if (reporting?.isActive == true) return
        reporting = scope.launch { report() }
    }

    private fun onBubbleMenu(choice: Bubble.Choice) {
        when (choice) {
            Bubble.Choice.IDEA -> startActivity(CaptureActivity.launchIntent(this, CaptureKind.IDEA))
            Bubble.Choice.RECORD -> startActivity(CaptureActivity.launchIntent(this, CaptureKind.RECORD))
            Bubble.Choice.HIDE -> {
                BubblePrefs(this).wanted = false
                bubble?.remove()
            }
        }
    }

    private suspend fun report() {
        val shown = bubble ?: return
        val pending = PendingCapture.start(CaptureKind.REPORT, foregroundApp())
        val bitmap = shown.hiddenWhile {
            delay(HIDE_SETTLE_MILLIS)
            screenshot()
        }
        if (bitmap == null) {
            Toast.makeText(this, "Screenshot failed", Toast.LENGTH_LONG).show()
            return
        }
        val store = CaptureStore(this)
        withContext(Dispatchers.IO) {
            requireNotNull(store.mediaFile(pending)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } // stack-reviewer-ignore: a REPORT capture always has a jpg fileName, so mediaFile is non-null here
            bitmap.recycle()
            store.save(pending)
        }
        startActivity(CaptureActivity.dictateIntent(this, pending.id))
    }

    fun foregroundApp(): AppContext {
        val root = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .firstNotNullOfOrNull { window -> window.root?.takeIf { it.packageName?.toString() != packageName } }
            ?: return AppContext.UNKNOWN
        val pkg = root.packageName.toString()
        return AppContext(label(pkg), pkg, addressBarUrl(root, pkg))
    }

    private suspend fun screenshot(): Bitmap? = suspendCancellableCoroutine { continuation ->
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val bitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                    buffer.close()
                    if (continuation.isActive) continuation.resume(bitmap) else bitmap?.recycle()
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
        private const val HIDE_SETTLE_MILLIS = 150L

        @Volatile
        var instance: CaptureAccessibilityService? = null
            private set
    }
}
