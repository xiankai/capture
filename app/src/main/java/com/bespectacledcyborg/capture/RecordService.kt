package com.bespectacledcyborg.capture

import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.quicksettings.TileService
import android.view.Display
import android.view.Surface
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

class RecordService : Service() {
    private var recorder: MediaRecorder? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var capture: PendingCapture? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val consent = intent?.getParcelableExtra(EXTRA_CONSENT, Intent::class.java)
        val pending = intent?.getStringExtra(EXTRA_CAPTURE)?.let { Json.decodeFromString(PendingCapture.serializer(), it) }
        if (consent == null || pending == null || current != null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startForeground(
            Notifications.ID_RECORDING,
            Notifications.builder(this, Notifications.CHANNEL_RECORDING)
                .setContentTitle("Recording screen")
                .setContentText("Tap to stop and dictate.")
                .setOngoing(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        1,
                        CaptureActivity.launchIntent(this, CaptureKind.RECORD),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                .build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
        try {
            begin(intent.getIntExtra(EXTRA_RESULT_CODE, 0), consent, pending)
        } catch (e: Exception) {
            release()
            CaptureStore(this).delete(pending)
            Notifications.failed(this, "Recording failed: ${e.message ?: e.javaClass.simpleName}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun begin(resultCode: Int, consent: Intent, pending: PendingCapture) {
        val store = CaptureStore(this)
        val output = requireNotNull(store.mediaFile(pending))
        val screen = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val spec = VideoSpec.forDisplay(screen)
        val mediaRecorder = MediaRecorder(this).apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(spec.width, spec.height)
            setVideoFrameRate(spec.frameRate)
            setVideoEncodingBitRate(spec.bitRate)
            setOutputFile(output)
            prepare()
        }
        recorder = mediaRecorder
        capture = pending
        store.save(pending)
        val mediaProjection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, consent)
            ?: error("screen recording consent was not granted")
        projection = mediaProjection
        mediaProjection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() = finishInBackground()
            },
            handler,
        )
        display = mediaProjection.createVirtualDisplay(
            "capture",
            spec.width,
            spec.height,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder.surface,
            null,
            null,
        )
        mediaRecorder.start()
        handler.postDelayed(::finishInBackground, MAX_DURATION_MILLIS)
        current = this
        refreshTile(this)
    }

    private fun finishInBackground() {
        finish()?.let { Notifications.clipReady(this, it) }
    }

    fun finish(): String? {
        val finished = capture ?: return null
        capture = null
        handler.removeCallbacksAndMessages(null)
        val saved = runCatching { recorder?.stop() }.isSuccess
        release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        if (!saved) {
            CaptureStore(this).delete(finished)
            Notifications.failed(this, "The clip was too short to save.")
            return null
        }
        return finished.id
    }

    private fun release() {
        display?.release()
        display = null
        recorder?.release()
        recorder = null
        projection?.stop()
        projection = null
        if (current === this) current = null
        refreshTile(this)
    }

    override fun onDestroy() {
        finish()
        super.onDestroy()
    }

    data class VideoSpec(val width: Int, val height: Int, val frameRate: Int, val bitRate: Int) {
        companion object {
            private const val MAX_EDGE = 1920
            private const val BITS_PER_PIXEL_FRAME = 0.06
            const val MAX_BIT_RATE = 8_000_000

            fun forDisplay(display: Display): VideoSpec {
                val mode = display.mode
                val rotated = display.rotation == Surface.ROTATION_90 || display.rotation == Surface.ROTATION_270
                val (w, h) = if (rotated) mode.physicalHeight to mode.physicalWidth else mode.physicalWidth to mode.physicalHeight
                return fit(w, h, mode.refreshRate.roundToInt(), ::supported)
            }

            fun fit(width: Int, height: Int, refreshRate: Int, supported: (Int, Int, Int) -> Boolean): VideoSpec {
                val scale = minOf(1.0, MAX_EDGE.toDouble() / maxOf(width, height))
                val w = even(width * scale)
                val h = even(height * scale)
                val rate = listOf(refreshRate, 60, 30).distinct().firstOrNull { supported(w, h, it) } ?: 30
                val bitRate = (w * h * rate * BITS_PER_PIXEL_FRAME).roundToInt().coerceIn(4_000_000, MAX_BIT_RATE)
                return VideoSpec(w, h, rate, bitRate)
            }

            private fun even(value: Double): Int = value.roundToInt() and 1.inv()

            private fun supported(width: Int, height: Int, rate: Int): Boolean =
                MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                    .filter { it.isEncoder && MediaFormat.MIMETYPE_VIDEO_AVC in it.supportedTypes }
                    .any { info ->
                        val video = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities
                        runCatching { video!!.areSizeAndRateSupported(width, height, rate.toDouble()) }.getOrDefault(false)
                    }
        }
    }

    companion object {
        private const val EXTRA_RESULT_CODE = "resultCode"
        private const val EXTRA_CONSENT = "consent"
        private const val EXTRA_CAPTURE = "capture"
        const val MAX_DURATION_MILLIS = 30_000L

        @Volatile
        var current: RecordService? = null
            private set

        fun start(context: Context, resultCode: Int, consent: Intent, capture: PendingCapture) {
            context.startForegroundService(
                Intent(context, RecordService::class.java)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_CONSENT, consent)
                    .putExtra(EXTRA_CAPTURE, Json.encodeToString(PendingCapture.serializer(), capture)),
            )
        }

        private fun refreshTile(context: Context) {
            TileService.requestListeningState(context, ComponentName(context, RecordTile::class.java))
        }
    }
}
