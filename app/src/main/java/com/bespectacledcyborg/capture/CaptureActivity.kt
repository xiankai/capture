package com.bespectacledcyborg.capture

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class CaptureActivity : Activity() {
    private val scope = MainScope()
    private val store by lazy { CaptureStore(this) }
    private var capture: PendingCapture? = null
    private var decided = false
    private var dictation: Dictation? = null
    private var permissionsAnswered: CompletableDeferred<Unit>? = null
    private var recordingCapture: PendingCapture? = null

    private lateinit var root: FrameLayout
    private lateinit var status: TextView
    private lateinit var said: EditText
    private lateinit var talkButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setLayout(WRAP_CONTENT, WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { cancel() }
        scope.launch { run(intent) }
    }

    private suspend fun run(intent: Intent) {
        if (intent.action == ACTION_DICTATE) {
            val pending = intent.getStringExtra(EXTRA_CAPTURE_ID)?.let(store::load)
            if (pending == null) finish() else dictate(pending)
            return
        }
        when (kindOf(intent)) {
            CaptureKind.IDEA -> dictate(started(CaptureKind.IDEA, appUnder(intent)))
            CaptureKind.RECORD -> record(intent)
            CaptureKind.REPORT, null -> finish()
        }
    }

    private suspend fun record(intent: Intent) {
        val running = RecordService.current
        if (running != null) {
            val pending = running.finish()?.let(store::load)
            if (pending == null) finish() else dictate(pending)
            return
        }
        recordingCapture = PendingCapture.start(CaptureKind.RECORD, appUnder(intent))
        if (!permissionPromptAnswered()) return
        startActivityForResult(
            getSystemService(MediaProjectionManager::class.java)
                .createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()),
            REQUEST_PROJECTION,
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val pending = recordingCapture
        if (requestCode == REQUEST_PROJECTION && resultCode == RESULT_OK && data != null && pending != null) {
            RecordService.start(this, resultCode, data, pending)
        }
        decided = true
        finish()
    }

    private fun appUnder(intent: Intent): AppContext =
        appFrom(intent) ?: CaptureAccessibilityService.instance?.foregroundApp() ?: AppContext.UNKNOWN

    private fun started(kind: CaptureKind, context: AppContext): PendingCapture =
        PendingCapture.start(kind, context).also(store::save)

    private suspend fun dictate(pending: PendingCapture) {
        capture = pending
        if (!permissionPromptAnswered()) return
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        setContentView(panel(pending))
        dictation = Dictation(
            this,
            onStatus = { status.text = it },
            onHeard = { status.text = it },
            onSegment = ::appendSegment,
            onListening = ::showListening,
        )
        talk()
    }

    private fun talk() {
        root.requestFocus()
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(said.windowToken, 0)
        dictation?.start()
    }

    private fun stopTalking() {
        val current = dictation ?: return
        if (!current.listening) return
        current.stop()
        status.text = "Stopped. Edit if needed, then Send."
    }

    private fun appendSegment(segment: String) {
        said.setText(Transcript.append(said.text, segment))
        said.setSelection(said.length())
    }

    private fun showListening(listening: Boolean) {
        talkButton.text = if (listening) "Stop" else "Talk"
    }

    private fun send() {
        val pending = capture ?: return
        decided = true
        dictation?.stop()
        store.save(pending.copy(said = said.text.toString().trim(), ready = true))
        SendWorker.enqueue(this)
        finish()
    }

    private fun cancel() {
        capture?.let(store::delete)
        decided = true
        finish()
    }

    private suspend fun permissionPromptAnswered(): Boolean {
        val missing = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) return true
        val answered = CompletableDeferred<Unit>()
        permissionsAnswered = answered
        requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
        if (withTimeoutOrNull(PERMISSION_TIMEOUT_MILLIS) { answered.await() } != null) return true
        Toast.makeText(this, "Waiting for permissions -- capture cancelled", Toast.LENGTH_LONG).show()
        finish()
        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) permissionsAnswered?.complete(Unit)
    }

    private fun panel(pending: PendingCapture): FrameLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(themeColor(android.R.attr.colorBackground))
            }
            elevation = dp(8).toFloat()
        }
        card.addView(TextView(this).apply {
            text = "${pending.kind.title} · ${pending.app}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            alpha = 0.7f
        })
        status = TextView(this).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) }
        card.addView(status)
        said = EditText(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = "Speak, or tap here to type"
            minLines = 2
            maxLines = 8
            setOnFocusChangeListener { _, focused -> if (focused) stopTalking() }
        }
        card.addView(said)
        talkButton = Button(this@CaptureActivity, null, android.R.attr.borderlessButtonStyle).apply {
            text = "Talk"
            setOnClickListener { if (dictation?.listening == true) stopTalking() else talk() }
        }
        card.addView(LinearLayout(this).apply {
            gravity = Gravity.END
            addView(talkButton)
            addView(Button(this@CaptureActivity, null, android.R.attr.borderlessButtonStyle).apply {
                text = "Cancel"
                setOnClickListener { cancel() }
            })
            addView(Button(this@CaptureActivity, null, android.R.attr.borderlessButtonStyle).apply {
                text = "Send"
                setOnClickListener { send() }
            })
        })
        root = FrameLayout(this).apply {
            setPadding(dp(16), dp(16), dp(16), dp(48))
            isFocusableInTouchMode = true
            setOnApplyWindowInsetsListener { view, insets -> insets.also { liftAboveKeyboard(view, it) } }
            addView(card, FrameLayout.LayoutParams(minOf(dp(560), resources.displayMetrics.widthPixels - dp(32)), WRAP_CONTENT))
        }
        return root
    }

    private fun liftAboveKeyboard(view: View, insets: WindowInsets) {
        val keyboard = if (insets.isVisible(WindowInsets.Type.ime())) {
            windowManager.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.ime()).bottom
        } else {
            0
        }
        view.setPadding(dp(16), dp(16), dp(16), maxOf(dp(48), keyboard + dp(16)))
    }

    private fun themeColor(attr: Int): Int {
        val value = TypedValue()
        theme.resolveAttribute(attr, value, true)
        return value.data
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        dictation?.stop()
        scope.cancel()
        if (isFinishing && !decided) capture?.let(store::delete)
        super.onDestroy()
    }

    private val CaptureKind.title: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }

    companion object {
        private const val ACTION_PREFIX = "com.bespectacledcyborg.capture.action."
        private const val ACTION_DICTATE = ACTION_PREFIX + "DICTATE"
        private const val EXTRA_CAPTURE_ID = "captureId"
        private const val EXTRA_APP_LABEL = "appLabel"
        private const val EXTRA_APP_PACKAGE = "appPackage"
        private const val EXTRA_APP_URL = "appUrl"
        private const val REQUEST_PERMISSIONS = 1
        private const val REQUEST_PROJECTION = 2
        private const val PERMISSION_TIMEOUT_MILLIS = 60_000L

        fun launchIntent(context: Context, kind: CaptureKind, app: AppContext? = null): Intent =
            Intent(context, CaptureActivity::class.java)
                .setAction(ACTION_PREFIX + kind.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .apply {
                    if (app == null) return@apply
                    putExtra(EXTRA_APP_LABEL, app.label)
                    putExtra(EXTRA_APP_PACKAGE, app.packageName)
                    putExtra(EXTRA_APP_URL, app.url)
                }

        private fun appFrom(intent: Intent): AppContext? {
            val label = intent.getStringExtra(EXTRA_APP_LABEL) ?: return null
            val pkg = intent.getStringExtra(EXTRA_APP_PACKAGE) ?: return null
            return AppContext(label, pkg, intent.getStringExtra(EXTRA_APP_URL))
        }

        fun dictateIntent(context: Context, captureId: String): Intent =
            Intent(context, CaptureActivity::class.java)
                .setAction(ACTION_DICTATE)
                .putExtra(EXTRA_CAPTURE_ID, captureId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        private fun kindOf(intent: Intent): CaptureKind? =
            CaptureKind.entries.firstOrNull { intent.action == ACTION_PREFIX + it.name }
    }
}
