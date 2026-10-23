package com.bespectacledcyborg.capture

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : Activity() {
    private val scope = MainScope()
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        status = TextView(this).apply { textSize = 16f }
        column.addView(status)
        column.addView(button("Accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        column.addView(button("Allow microphone and notifications") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 1)
        })
        column.addView(button("Retry pending captures") { SendWorker.enqueue(this) })
        setContentView(ScrollView(this).apply { addView(column) })
    }

    override fun onResume() {
        super.onResume()
        CaptureAccessibilityService.instance?.showBubble()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun refresh() {
        val accessibility = CaptureAccessibilityService.instance != null
        val microphone = granted(Manifest.permission.RECORD_AUDIO)
        val notifications = granted(Manifest.permission.POST_NOTIFICATIONS)
        val pending = CaptureStore(this).ready().size
        scope.launch {
            val reachable = withContext(Dispatchers.IO) { Homelab.isReachable() }
            status.text = listOf(
                "Version ${BuildConfig.VERSION_NAME}",
                "Accessibility service: ${onOff(accessibility)}",
                "Floating button: ${if (accessibility) "on. Tap to report, long-press for Idea, Record or Hide" else "needs the accessibility service"}",
                "Microphone: ${onOff(microphone)}",
                "Notifications: ${onOff(notifications)}",
                "Homelab: ${if (reachable) "reachable" else "not reachable. Not on home Wi-Fi and Tailscale off?"}",
                "Waiting to send: $pending",
            ).joinToString("\n")
        }
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun onOff(on: Boolean) = if (on) "on" else "off"

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
