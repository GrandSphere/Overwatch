package com.grandsphere.overwatch.runtime

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile that triggers Panic (same path as the home Panic widget). */
class PanicTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(com.grandsphere.overwatch.R.string.panic)
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, NotificationActionReceiver::class.java)
            .setAction(OverwatchNotifications.ACTION_PANIC)
        sendBroadcast(intent)
    }
}
