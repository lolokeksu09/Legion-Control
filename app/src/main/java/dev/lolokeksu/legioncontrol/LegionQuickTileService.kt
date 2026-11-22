package dev.lolokeksu.legioncontrol

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** A user-added launcher tile. Does not autonomously start a foreground recording. */
class LegionQuickTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label="Legion Control"
            state=Tile.STATE_ACTIVE
            updateTile()
        }
    }

    // Legacy Intent overload is required only on Android 13 (API 33); API 34+ uses PendingIntent.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent=Intent(this,SmartProfilesActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if(Build.VERSION.SDK_INT>=34) {
            val pending=PendingIntent.getActivity(this,0,intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
