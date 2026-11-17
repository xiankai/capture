package com.bespectacledcyborg.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Notifications {
    const val CHANNEL_RESULT = "result"
    const val CHANNEL_RECORDING = "recording"
    const val ID_RESULT = 1
    const val ID_RECORDING = 2
    const val ID_CLIP_READY = 3

    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_RESULT, "Capture results", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_RECORDING, "Screen recording", NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }

    fun sent(context: Context, count: Int) = notify(
        context,
        ID_RESULT,
        builder(context, CHANNEL_RESULT)
            .setContentTitle(if (count == 1) "Sent" else "Sent $count captures")
            .setContentText("The concierge will file it.")
            .setTimeoutAfter(10_000)
            .build(),
    )

    fun unreachable(context: Context) = retryable(
        context,
        "Orchestrator not reachable",
        "Orchestrator not reachable. Check the network or VPN, then retry.",
    )

    fun noOrchestrator(context: Context) = notify(
        context,
        ID_RESULT,
        builder(context, CHANNEL_RESULT)
            .setContentTitle("No orchestrator set")
            .setContentText("Captures are kept until you set the orchestrator URL. Tap to open settings.")
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setAutoCancel(true)
            .build(),
    )

    fun failed(context: Context, reason: String) = retryable(context, "Capture not sent", reason)

    fun clipReady(context: Context, captureId: String) = notify(
        context,
        ID_CLIP_READY,
        builder(context, CHANNEL_RESULT)
            .setContentTitle("Clip saved")
            .setContentText("Tap to dictate and send.")
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    captureId.hashCode(),
                    CaptureActivity.dictateIntent(context, captureId),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setAutoCancel(true)
            .build(),
    )

    fun builder(context: Context, channel: String): Notification.Builder =
        Notification.Builder(context, channel).setSmallIcon(R.drawable.ic_capture)

    private fun retryable(context: Context, title: String, text: String) {
        val retry = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, RetryReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        notify(
            context,
            ID_RESULT,
            builder(context, CHANNEL_RESULT)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .addAction(Notification.Action.Builder(null, "Retry", retry).build())
                .build(),
        )
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        runCatching { context.getSystemService(NotificationManager::class.java).notify(id, notification) }
    }
}
