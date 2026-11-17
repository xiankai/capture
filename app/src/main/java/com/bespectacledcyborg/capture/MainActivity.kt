package com.bespectacledcyborg.capture

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.WindowInsets
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
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
    private lateinit var orchestrator: OrchestratorSetting

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        orchestrator = OrchestratorSetting(this)
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
        val orchestratorField = EditText(this).apply {
            hint = "https://orchestrator.example"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
            setText(orchestrator.entered)
        }
        val orchestratorForm = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply { text = "Orchestrator URL" })
            addView(orchestratorField, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addButton("Save orchestrator URL") { saveOrchestrator(orchestratorField) }
        }
        val heading = TextView(this).apply {
            setText(R.string.settings)
            setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(heading)
            addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) })
            addView(orchestratorForm, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(24) })
            addView(buttons, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(24) })
        }
        column.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left + dp(24), bars.top + dp(24), bars.right + dp(24), bars.bottom + dp(24))
            insets
        }
        setContentView(ScrollView(this).apply { addView(column) })
    }

    private fun saveOrchestrator(field: EditText) {
        if (!orchestrator.save(field.text.toString())) {
            field.error = "Enter an http or https address, like https://orchestrator.example"
            return
        }
        field.error = null
        field.setText(orchestrator.entered)
        refresh()
        SendWorker.enqueue(this)
    }

    private fun serviceComponent() = ComponentName(this, CaptureAccessibilityService::class.java)

    private fun accessibilityState(): AccessibilityState {
        if (CaptureAccessibilityService.instance != null) return AccessibilityState.RUNNING
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
        return if (serviceComponent() in enabled) AccessibilityState.HELD_OFF else AccessibilityState.OFF
    }

    private enum class AccessibilityState(val status: String, val bubble: String) {
        RUNNING("on", "on. Tap to report, long-press for Idea, Record, Settings or Hide"),
        HELD_OFF(
            "on, but not running. Usually an automation tool is holding it off (an agent driving the tablet over adb)",
            "back once the service runs again",
        ),
        OFF("off. Tap Accessibility settings › Installed apps › Capture › on", "needs the accessibility service"),
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
        val accessibility = accessibilityState()
        val microphone = granted(Manifest.permission.RECORD_AUDIO)
        val notifications = granted(Manifest.permission.POST_NOTIFICATIONS)
        val pending = CaptureStore(this).ready().size
        scope.launch {
            val orchestratorStatus = orchestratorStatus()
            status.text = listOf(
                "Version ${BuildConfig.VERSION_NAME}",
                "Accessibility service: ${accessibility.status}",
                "Floating button: ${accessibility.bubble}",
                "Microphone: ${onOff(microphone)}",
                "Notifications: ${onOff(notifications)}",
                "Orchestrator: $orchestratorStatus",
                "Waiting to send: $pending",
            ).joinToString("\n")
        }
    }

    private suspend fun orchestratorStatus(): String {
        val url = orchestrator.url ?: return "not set. Enter its URL below"
        val reachable = withContext(Dispatchers.IO) { Homelab.isReachable(url) }
        return if (reachable) "reachable" else "not reachable. Check the network or VPN"
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun onOff(on: Boolean) = if (on) "on" else "off"

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
