package com.bespectacledcyborg.capture

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.WindowInsets
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val scope = MainScope()
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            textSize = 16f
            setLineSpacing(dp(6).toFloat(), 1f)
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addButton("Accessibility settings") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            addButton("Allow microphone and notifications") {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 1)
            }
            addButton("Retry pending captures") { SendWorker.enqueue(this@MainActivity) }
        }
        val heading = TextView(this).apply {
            setText(R.string.settings)
            setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(heading)
            addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) })
            addView(buttons, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(24) })
        }
        column.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left + dp(24), bars.top + dp(24), bars.right + dp(24), bars.bottom + dp(24))
            insets
        }
        setContentView(ScrollView(this).apply { addView(column) })
    }

    private fun LinearLayout.addButton(label: String, onClick: () -> Unit) {
        val button = Button(context).apply {
            text = label
            setOnClickListener { onClick() }
        }
        addView(button, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

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
                "Floating button: ${if (accessibility) "on. Tap to report, long-press for Idea, Record, Settings or Hide" else "needs the accessibility service"}",
                "Microphone: ${onOff(microphone)}",
                "Notifications: ${onOff(notifications)}",
                "Homelab: ${if (reachable) "reachable" else "not reachable. Not on home Wi-Fi and Tailscale off?"}",
                "Waiting to send: $pending",
            ).joinToString("\n")
        }
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun onOff(on: Boolean) = if (on) "on" else "off"

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
