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
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
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

    private lateinit var status: TextView
    private lateinit var said: TextView

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
            CaptureKind.IDEA -> dictate(started(CaptureKind.IDEA, foregroundApp()))
            CaptureKind.RECORD -> record()
            CaptureKind.REPORT, null -> finish()
        }
    }

    private suspend fun record() {
        val running = RecordService.current
        if (running != null) {
            val pending = running.finish()?.let(store::load)
            if (pending == null) finish() else dictate(pending)
            return
        }
        recordingCapture = PendingCapture.start(CaptureKind.RECORD, foregroundApp())
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
        decided = true // stack-reviewer-ignore: a cancelled projection never saved recordingCapture, so the finishing activity has nothing to delete
        finish()
    }

    private fun foregroundApp(): AppContext =
        CaptureAccessibilityService.instance?.foregroundApp() ?: AppContext.UNKNOWN

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
            onText = { said.text = it },
            onFinal = ::send,
        ).also { it.start() }
    }

    private fun send(text: String) {
        val pending = capture ?: return
        decided = true
        dictation?.stop()
        store.save(pending.copy(said = text.trim(), ready = true))
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
        if (withTimeoutOrNull(PERMISSION_TIMEOUT_MILLIS) { answered.await() } != null) return true // stack-reviewer-ignore: a denial still opens the panel on purpose; Dictation shows "Microphone permission is off."
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
        said = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(0, dp(12), 0, dp(12))
            minLines = 2
        }
        card.addView(said)
        card.addView(LinearLayout(this).apply {
            gravity = Gravity.END
            addView(Button(this@CaptureActivity, null, android.R.attr.borderlessButtonStyle).apply {
                text = "Talk"
                setOnClickListener { dictation?.start() }
            })
            addView(Button(this@CaptureActivity, null, android.R.attr.borderlessButtonStyle).apply {
                text = "Cancel"
                setOnClickListener { cancel() }
            })
            addView(Button(this@CaptureActivity, null, android.R.attr.borderlessButtonStyle).apply {
                text = "Send"
                setOnClickListener { send(said.text.toString()) }
            })
        })
        return FrameLayout(this).apply {
            setPadding(dp(16), dp(16), dp(16), dp(48))
            addView(card, FrameLayout.LayoutParams(minOf(dp(560), resources.displayMetrics.widthPixels - dp(32)), WRAP_CONTENT))
        }
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
        if (isFinishing && !decided) capture?.let(store::delete) // stack-reviewer-ignore: a process killed before onDestroy leaves the capture unready, and SendWorker's pruneAbandoned removes it
        super.onDestroy()
    }

    private val CaptureKind.title: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }

    companion object {
        private const val ACTION_PREFIX = "com.bespectacledcyborg.capture.action."
        private const val ACTION_DICTATE = ACTION_PREFIX + "DICTATE"
        private const val EXTRA_CAPTURE_ID = "captureId"
        private const val REQUEST_PERMISSIONS = 1
        private const val REQUEST_PROJECTION = 2
        private const val PERMISSION_TIMEOUT_MILLIS = 60_000L

        fun launchIntent(context: Context, kind: CaptureKind): Intent =
            Intent(context, CaptureActivity::class.java)
                .setAction(ACTION_PREFIX + kind.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun dictateIntent(context: Context, captureId: String): Intent =
            Intent(context, CaptureActivity::class.java)
                .setAction(ACTION_DICTATE)
                .putExtra(EXTRA_CAPTURE_ID, captureId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        private fun kindOf(intent: Intent): CaptureKind? =
            CaptureKind.entries.firstOrNull { intent.action == ACTION_PREFIX + it.name }
    }
}
