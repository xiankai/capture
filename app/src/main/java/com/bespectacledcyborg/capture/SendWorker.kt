package com.bespectacledcyborg.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = CaptureStore(applicationContext)
        store.pruneAbandoned()
        val queue = store.ready()
        if (queue.isEmpty()) return@withContext Result.success()
        if (!Homelab.isReachable()) {
            Notifications.unreachable(applicationContext)
            return@withContext Result.success()
        }
        for (capture in queue) {
            try {
                send(store, capture)
            } catch (e: Exception) {
                Notifications.failed(applicationContext, e.message ?: e.javaClass.simpleName)
                return@withContext Result.success()
            }
        }
        Notifications.sent(applicationContext, queue.size)
        Result.success()
    }

    private fun send(store: CaptureStore, capture: PendingCapture) {
        Homelab.post(capture.payload().toLine(), store.mediaFile(capture))
        store.delete(capture)
    }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "send",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<SendWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build(),
            )
        }
    }
}

class RetryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SendWorker.enqueue(context)
    }
}
