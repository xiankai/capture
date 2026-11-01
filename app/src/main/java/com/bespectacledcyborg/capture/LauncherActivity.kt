package com.bespectacledcyborg.capture

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (CaptureAccessibilityService.instance?.showBubble() == true) {
            finishAndRemoveTask()
            return
        }
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
