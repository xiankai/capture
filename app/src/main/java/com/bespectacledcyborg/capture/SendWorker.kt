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
        val failures = queue.mapNotNull { capture -> send(store, capture).exceptionOrNull() }
        val sent = queue.size - failures.size
        if (sent > 0) Notifications.sent(applicationContext, sent)
        failures.firstOrNull()?.let { Notifications.failed(applicationContext, it.message ?: it.javaClass.simpleName) }
        Result.success()
    }

    private fun send(store: CaptureStore, capture: PendingCapture): kotlin.Result<Unit> = runCatching {
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
