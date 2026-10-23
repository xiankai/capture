package com.bespectacledcyborg.capture

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.core.content.edit
import kotlin.math.hypot
import kotlin.math.roundToInt

class Bubble(
    private val context: Context,
    private val onTap: () -> Unit,
    private val onMenu: (Choice) -> Unit,
) {
    enum class Choice(val label: Int) { IDEA(R.string.idea), RECORD(R.string.record), HIDE(R.string.hide) }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val prefs = BubblePrefs(context)
    private val handler = Handler(Looper.getMainLooper())
    private val size = (BUBBLE_DP * context.resources.displayMetrics.density).roundToInt()
    private val background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    private val view = ImageView(context).apply {
        setImageResource(R.drawable.ic_capture)
        scaleType = ImageView.ScaleType.CENTER
        background = this@Bubble.background
        elevation = size / 8f
        contentDescription = context.getString(R.string.report)
        setOnClickListener { onTap() }
        setOnLongClickListener {
            showMenu()
            true
        }
        setOnTouchListener(DragOrTap())
    }
    private val params = WindowManager.LayoutParams(
        size,
        size,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = prefs.x ?: (context.resources.displayMetrics.widthPixels - size * 3 / 2)
        y = prefs.y ?: (context.resources.displayMetrics.heightPixels / 3)
    }

    val isShown: Boolean
        get() = view.isAttachedToWindow

    fun show() {
        refresh()
        if (!isShown) windowManager.addView(view, params)
    }

    fun remove() {
        handler.removeCallbacksAndMessages(null)
        if (isShown) windowManager.removeView(view)
    }

    fun refresh() {
        val recording = RecordService.current != null
        background.setColor(context.getColor(if (recording) R.color.record else R.color.report))
        view.contentDescription = context.getString(if (recording) R.string.stop_recording else R.string.report)
    }

    suspend fun <T> hiddenWhile(block: suspend () -> T): T {
        view.visibility = View.INVISIBLE
        return try {
            block()
        } finally {
            view.visibility = View.VISIBLE
        }
    }

    private fun showMenu() {
        val themed = ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_DayNight)
        PopupMenu(themed, view).apply {
            Choice.entries.forEach { menu.add(0, it.ordinal, it.ordinal, it.label) }
            setOnMenuItemClickListener { item ->
                onMenu(Choice.entries[item.itemId])
                true
            }
        }.show()
    }

    private inner class DragOrTap : View.OnTouchListener {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var pressedLong = false
        private val longPress = Runnable {
            pressedLong = true
            view.performLongClick()
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    pressedLong = false
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && !pressedLong && hypot(dx, dy) > slop) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                    }
                    if (dragging) {
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        windowManager.updateViewLayout(view, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    when {
                        dragging -> prefs.savePosition(params.x, params.y)
                        !pressedLong -> v.performClick()
                    }
                }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
            }
            return true
        }
    }

    private companion object {
        const val BUBBLE_DP = 56
    }
}

class BubblePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("bubble", Context.MODE_PRIVATE)

    var wanted: Boolean
        get() = prefs.getBoolean(KEY_WANTED, true)
        set(value) = prefs.edit { putBoolean(KEY_WANTED, value) }

    val x: Int?
        get() = prefs.getInt(KEY_X, UNSET).takeIf { it != UNSET }

    val y: Int?
        get() = prefs.getInt(KEY_Y, UNSET).takeIf { it != UNSET }

    fun savePosition(x: Int, y: Int) = prefs.edit { putInt(KEY_X, x).putInt(KEY_Y, y) }

    private companion object {
        const val KEY_WANTED = "wanted"
        const val KEY_X = "x"
        const val KEY_Y = "y"
        const val UNSET = Int.MIN_VALUE
    }
}
