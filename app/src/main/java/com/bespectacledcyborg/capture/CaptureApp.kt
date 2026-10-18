package com.bespectacledcyborg.capture

import android.app.Application

class CaptureApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
