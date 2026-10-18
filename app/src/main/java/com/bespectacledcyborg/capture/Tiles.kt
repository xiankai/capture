package com.bespectacledcyborg.capture

import android.app.PendingIntent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

abstract class CaptureTile(private val kind: CaptureKind) : TileService() {
    override fun onClick() {
        startActivityAndCollapse(
            PendingIntent.getActivity(
                this,
                kind.ordinal,
                CaptureActivity.launchIntent(this, kind),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }
}

class IdeaTile : CaptureTile(CaptureKind.IDEA)

class ReportTile : CaptureTile(CaptureKind.REPORT)

class RecordTile : CaptureTile(CaptureKind.RECORD) {
    override fun onStartListening() {
        val tile = qsTile ?: return
        val recording = RecordService.current != null
        tile.state = if (recording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (recording) "Tap to stop" else null
        tile.updateTile()
    }
}
